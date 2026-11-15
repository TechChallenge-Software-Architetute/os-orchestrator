#!/usr/bin/env bash
# Sobe o os-orchestrator localmente (Docker Compose + LocalStack) com o Datadog Agent e
# o tracer ligados, para validar a integração sem AWS:
#   - o orquestrador carrega o dd-java-agent por JAVA_TOOL_OPTIONS (no EKS quem faz
#     isso é o admission controller do Datadog);
#   - traces (SQS -> DynamoDB -> SQS) e logs em JSON chegam ao Datadog com
#     service:os-orchestrator.
#
# Uso (Git Bash, na raiz do repositório):
#   export DD_API_KEY=<api key>        # ou DD_API_KEY=... no arquivo .env (ignorado pelo git)
#   export DD_SITE=datadoghq.eu        # opcional; padrão datadoghq.com (US1)
#   bash datadog/test-local.sh up          # baixa o tracer, build, LocalStack, Agent e orquestrador
#   bash datadog/test-local.sh traffic 5   # 5 OS percorrendo CRIAR_OS -> GERAR_ORCAMENTO (padrão 3)
#   bash datadog/test-local.sh check       # repete as verificações
#   bash datadog/test-local.sh diag        # diagnóstico do Agent
#   bash datadog/test-local.sh down        # derruba tudo
#
# Requer Docker rodando e curl. Usa a porta 4566 do LocalStack, como o
# docker-compose.yml: pare outro LocalStack que esteja nessa porta antes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml -f datadog/docker-compose.datadog.yml)
TRACER_JAR="datadog/.cache/dd-java-agent.jar"
TRACER_URL="${DD_JAVA_AGENT_URL:-https://dtdg.co/latest-java-tracer}"

# Lê uma variável do .env quando ela não foi exportada no terminal.
from_env_file() {
  local name="$1"
  if [ -z "${!name:-}" ] && [ -f .env ]; then
    local value
    value="$(sed -n "s/^${name}=//p" .env | tail -1 | tr -d '\r' || true)"
    value="${value%\"}"; value="${value#\"}"
    [ -n "$value" ] && printf -v "$name" '%s' "$value"
  fi
  return 0
}

from_env_file DD_API_KEY
from_env_file DD_SITE
export DD_API_KEY="${DD_API_KEY:-}"
export DD_SITE="${DD_SITE:-datadoghq.com}"
export DD_ENV="${DD_ENV:-developer}"   # um dos valores do filtro env dos dashboards

log() { printf '\n==> %s\n' "$*"; }
die() { echo "ERRO: $*" >&2; exit 1; }

require() {
  local c
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || die "comando '$c' não encontrado no PATH."
  done
}

# URL da interface web: app.<site> para US1/EU/gov, <site> para us3/us5/ap1/ap2.
app_url() {
  case "$DD_SITE" in
    datadoghq.com|datadoghq.eu|ddog-gov.com) echo "https://app.$DD_SITE" ;;
    *) echo "https://$DD_SITE" ;;
  esac
}

container_id() { "${COMPOSE[@]}" ps -q "$1" 2>/dev/null || true; }

validate_key() {
  [ -n "$DD_API_KEY" ] || die "defina DD_API_KEY (export DD_API_KEY=... ou no arquivo .env)."
  local answer
  answer="$(curl -s "https://api.$DD_SITE/api/v1/validate" -H "DD-API-KEY: $DD_API_KEY" || true)"
  if ! grep -q '"valid":true' <<<"$answer"; then
    echo "A API key não foi aceita em $DD_SITE (resposta: ${answer:-sem resposta})." >&2
    echo "Confira a chave e o site da conta pela URL depois do login: app.datadoghq.com" >&2
    echo "é o padrão (US1); app.datadoghq.eu -> export DD_SITE=datadoghq.eu." >&2
    exit 1
  fi
  echo "API key válida em $DD_SITE."
}

# Baixa o dd-java-agent uma vez; DD_JAVA_AGENT_REFRESH=1 força uma nova versão.
download_tracer() {
  if [ -s "$TRACER_JAR" ] && [ "${DD_JAVA_AGENT_REFRESH:-0}" != "1" ]; then
    echo "Tracer já baixado: $TRACER_JAR"
    return 0
  fi
  mkdir -p "$(dirname "$TRACER_JAR")"
  curl -fsSL -o "$TRACER_JAR.tmp" "$TRACER_URL" || die "não foi possível baixar o dd-java-agent de $TRACER_URL."
  mv "$TRACER_JAR.tmp" "$TRACER_JAR"
  echo "Tracer baixado: $TRACER_JAR"
}

up() {
  require docker curl
  docker info >/dev/null 2>&1 || die "o Docker não está rodando. Abra o Docker Desktop e tente de novo."
  validate_key

  log "dd-java-agent"
  download_tracer

  log "Build da imagem e subida do LocalStack, Datadog Agent e orquestrador"
  "${COMPOSE[@]}" up -d --build

  log "Aguardando o Datadog Agent ficar saudável"
  local i status="" cid
  for i in $(seq 1 60); do
    cid="$(container_id datadog-agent)"
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
    [ "$status" = "healthy" ] && { echo "Agent saudável."; break; }
    sleep 2
  done
  if [ "$status" != "healthy" ]; then
    echo "AVISO: o Agent não ficou saudável em 2 minutos (status: ${status:-desconhecido})." >&2
    diag
  fi

  log "Aguardando o orquestrador subir com o tracer"
  local logs
  for i in $(seq 1 90); do
    logs="$("${COMPOSE[@]}" logs --no-color orchestrator 2>/dev/null || true)"
    grep -q "Started OsOrchestratorApplication" <<<"$logs" && break
    sleep 2
  done

  check
}

check() {
  require docker
  log "Verificações"
  local logs
  logs="$("${COMPOSE[@]}" logs --no-color orchestrator 2>/dev/null || true)"
  if grep -q "Picked up JAVA_TOOL_OPTIONS" <<<"$logs"; then
    echo "OK     JAVA_TOOL_OPTIONS aplicado no orquestrador"
  else
    echo "ERRO   o orquestrador não recebeu JAVA_TOOL_OPTIONS (confira o datadog/docker-compose.datadog.yml)" >&2
  fi
  if grep -q "DATADOG TRACER CONFIGURATION" <<<"$logs"; then
    echo "OK     dd-java-agent carregado"
  else
    echo "ERRO   a linha 'DATADOG TRACER CONFIGURATION' não apareceu no log do orquestrador." >&2
    echo "       Confira se $TRACER_JAR existe e se o container o enxerga em /datadog-lib." >&2
  fi
  if grep -q "Started OsOrchestratorApplication" <<<"$logs"; then
    echo "OK     orquestrador iniciado"
  else
    echo "ERRO   o orquestrador não terminou de subir. Últimas linhas:" >&2
    "${COMPOSE[@]}" logs --no-color --tail 25 orchestrator >&2 || true
  fi
  diag
  echo
  echo "Próximo passo: bash datadog/test-local.sh traffic"
}

# Saúde do Agent e validação da API key, a causa mais comum de problema.
diag() {
  echo
  echo "agent health:"
  { "${COMPOSE[@]}" exec -T datadog-agent agent health 2>&1 || true; } | sed 's/^/  /'
  echo "API key (agent status):"
  { "${COMPOSE[@]}" exec -T datadog-agent agent status 2>/dev/null || true; } \
    | grep -i -A1 "api key" | sed 's/^/  /' || true
}

# AWS CLI do próprio LocalStack (awslocal), o mesmo que o init-aws.sh usa.
awslocal() {
  "${COMPOSE[@]}" exec -T localstack awslocal "$@"
}

send_action() {
  local queue_url="$1" order="$2" action="$3"
  awslocal sqs send-message --queue-url "$queue_url" --message-body "$order" \
    --message-attributes "action={DataType=String,StringValue=$action}" >/dev/null
  echo "enviado: order=$order action=$action"
}

traffic() {
  require docker
  local total="${1:-3}" i stamp order queue_url
  log "Enviando $total OS para a fila order-processing-queue"
  queue_url="$(awslocal sqs get-queue-url --queue-name order-processing-queue --query QueueUrl --output text 2>/dev/null | tr -d '\r' || true)"
  [ -n "$queue_url" ] || die "fila order-processing-queue não encontrada no LocalStack. Rode 'up' primeiro (e confira se o init-aws.sh rodou)."
  stamp="$(date +%s)"
  for i in $(seq 1 "$total"); do
    order="dd-teste-$stamp-$i"
    # CRIAR_OS cria o estado e manda GERAR_ORCAMENTO para os-payment-queue; o segundo
    # envio simula a resposta e leva a OS a ORCAMENTO_GERADO (-> os-management-queue).
    send_action "$queue_url" "$order" CRIAR_OS
    sleep 2
    send_action "$queue_url" "$order" GERAR_ORCAMENTO
  done

  log "Últimas linhas do orquestrador (aguardando 10 s pelo processamento)"
  sleep 10
  "${COMPOSE[@]}" logs --no-color --tail 20 orchestrator || true

  log "Onde ver no Datadog (os dados levam de 1 a 2 minutos para aparecer)"
  links
}

links() {
  local app
  app="$(app_url)"
  echo "APM:        $app/apm/services?env=$DD_ENV"
  echo "Logs:       $app/logs?query=service%3Aos-orchestrator%20env%3A$DD_ENV"
  echo "Containers: $app/containers"
}

down() {
  require docker
  "${COMPOSE[@]}" down
}

case "${1:-up}" in
  up) up ;;
  check) check ;;
  diag) diag ;;
  traffic) traffic "${2:-3}" ;;
  links) links ;;
  down) down ;;
  *) echo "Uso: $0 [up|traffic [n]|check|diag|links|down]" >&2; exit 2 ;;
esac
