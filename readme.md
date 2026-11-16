# os-orchestrator

Máquina de estados da saga de ordens de serviço. Não expõe API de negócio: é
acionado exclusivamente por mensagens na sua fila SQS de entrada e guarda o estado
de cada ordem no DynamoDB.

O roteamento HTTP continua em `os-management`, que publica na fila deste serviço
para iniciar o fluxo.

### Executando localmente

O LocalStack sobe pelo `docker-compose.yml`; o `init-aws.sh` cria a fila
`order-processing-queue` e a tabela `order-state`.

```bash
docker compose up -d && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

O perfil `local` é **obrigatório** para desenvolvimento: ele é quem aponta o SQS e
o DynamoDB para `http://localhost:4566` e usa as credenciais de teste do
LocalStack (`application-local.yaml`). O `application.yaml` padrão não tem
endpoint nem credenciais fixas — sem o perfil, a aplicação fala com a AWS real
através do default credentials provider chain. A ordem é intencional: faltando uma
variável em produção, a aplicação falha na inicialização em vez de apontar
silenciosamente para o `localhost`.

Variáveis obrigatórias fora do perfil `local` (preenchidas pelo ConfigMap no
deploy):

| Variável | Exemplo |
|---|---|
| `ORDER_PROCESSING_QUEUE` | `os-orchestrator-develop-order-processing` |
| `MANAGEMENT_QUEUE` | `os-management-develop-order-events` |
| `PAYMENT_QUEUE` | `os-payment-develop-payment-requests` |
| `ORDER_STATE_TABLE` | `os-orchestrator-develop-order-state` |
| `AWS_REGION` | `us-east-1` |


---

### Infraestrutura (`infra/terraform`)

Este repositório cria **apenas o que é seu**:

| Recurso | Nome |
|---|---|
| Fila de entrada | `os-orchestrator-<env>-order-processing` |
| DLQ (redrive, maxReceiveCount 5) | `os-orchestrator-<env>-order-processing-dlq` |
| Tabela de estado da saga | `os-orchestrator-<env>-order-state` |

`<env>` é o nome da branch (`develop`/`main`).

Nenhum `terraform_remote_state` e nenhuma referência ao state de outro
repositório: filas de outros serviços são endereçadas por nome determinístico
(`os-<serviço>-<env>-<propósito>`), então este deploy roda em qualquer ordem,
inclusive em conta vazia.

Produtores da mesma conta (`os-management`, `os-payment`) recebem permissão de
`sqs:SendMessage` pela própria policy IAM deles — por isso a variável
`producer_role_arns` vem vazia por padrão e nenhuma resource policy é criada.
Preencha-a somente para produtores em outra conta.

### Segurança

- O `Service` é `ClusterIP`. O serviço não tem API de negócio nem autenticação
  própria, então não pode receber `LoadBalancer`.
- SSE-SQS e encryption-at-rest no DynamoDB habilitados; PITR ligado na tabela.
- O cluster não tem IRSA (`os-management-k8s-terraform` não habilita OIDC), então
  o pod recebe chaves estáticas por Secret. Pendência registrada em
  `k8s/app-secret.yaml.tpl` para migrar para service-account role.

---

### Passos (OLD)

1. Criar a OS do pedido
   Action: CRIAR_OS
   Step: CRIAR_OS -> OS_CRIADA

2. Gerar o orçamento do pedido
   Action: GERAR_ORCAMENTO
   Step: OS_CRIADA -> ORCAMENTO_GERADO

3. Executar o serviço do pedido
   Action: EXECUTAR_SERVICO
   Step: ORCAMENTO_GERADO -> EXECUTAR_SERVICO

---

### Fluxo


- Sucesso:
Os-management -> (CRIAR_OS)  -> orchestrator -> (GERAR_ORCAMENTO) -> payment -> (EXECUTAR_SERVICO) -> orchestrator -> (ATUALIZAR_ESTOQUE) -> management

- Falha:
Os-management -> (CRIAR_OS)  -> orchestrator -> (GERAR_ORCAMENTO) -> payment -> (NEGAR_SERVICO) -> orchestrator -> (NEGAR_ESTOQUE) -> management


---

### Observabilidade (Datadog)

O orquestrador roda no mesmo EKS do `os-management` e usa o Datadog Agent que o
pipeline do `os-management` instala (Helm, namespace `datadog`). Não há Agent nem
tracer neste repositório:

- **Traces:** o admission controller do Cluster Agent injeta o `dd-java-agent` no
  pod (init container `datadog-lib-java-init`). Para isso o pod precisa da label
  `admission.datadoghq.com/enabled: "true"` e da annotation
  `admission.datadoghq.com/java-lib.version` (`k8s/app.yaml`). O tracer instrumenta
  o SQS e o DynamoDB e propaga o contexto no atributo `_datadog` das mensagens, o
  que liga o trace da saga entre os serviços.
- **Logs:** JSON (`LogstashEncoder`) no stdout, coletado pela annotation
  `ad.datadoghq.com/os-orchestrator.logs`. O `DD_LOGS_INJECTION` põe
  `dd.trace_id`/`dd.span_id` no MDC, então cada log abre o trace correspondente.
- **Tags:** `DD_SERVICE=os-orchestrator`, `DD_VERSION` e `DD_ENV` no ConfigMap; a
  pipeline mapeia a branch como no `os-management` (`main` → `production`,
  `develop` → `homolog`) e aplica a label `tags.datadoghq.com/env`.
- **Ordem do deploy:** a injeção só acontece em pods criados depois que o Cluster
  Agent está pronto. O job `deploy-aws` espera por ele e, no fim, avisa se o pod
  subiu sem o tracer. Se o Agent ainda não existir no cluster (o `os-management`
  não fez deploy), o pod sobe sem tracer até o próximo deploy.

Validação no EKS (`env:homolog`):

```bash
kubectl get pods -n os-orchestrator -l app=os-orchestrator \
  -o jsonpath='{.items[*].spec.initContainers[*].name}'      # datadog-lib-java-init
```

No Datadog, filtre por `service:os-orchestrator env:homolog` em APM e Logs.

#### Teste local (sem AWS)

O script `datadog/test-local.sh` sobe o `docker-compose.yml` (LocalStack) junto
com o `datadog/docker-compose.datadog.yml`, que acrescenta o orquestrador em
container e um Datadog Agent. O script baixa o mesmo `dd-java-agent` que o
admission controller injeta no EKS (em `datadog/.cache`, ignorado pelo git) e o
container o carrega por `JAVA_TOOL_OPTIONS`.

Requer Docker rodando e uma API key do Datadog (exportada ou no `.env`, que não vai
para o git). No Git Bash, na raiz do repositório:

```bash
export DD_API_KEY=...          # ou DD_API_KEY=... no .env
export DD_SITE=datadoghq.com   # padrão; troque se a conta for de outro site
bash datadog/test-local.sh up          # tracer, build, LocalStack, Agent e orquestrador + verificações
bash datadog/test-local.sh traffic 5   # 5 OS em CRIAR_OS -> GERAR_ORCAMENTO + links do Datadog
bash datadog/test-local.sh down        # derruba tudo
```

No fim do `up` tem que aparecer `OK dd-java-agent carregado` e a API key como
válida. Os dados chegam com `service:os-orchestrator env:developer`.

### Testes

1. Criar a OS do pedido no os-oschestrator:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"CRIAR_OS"}}'

2. Gerar o orçamento do pedido no os-payment:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"GERAR_ORCAMENTO"}}'

3. Executar o serviço no os-management:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"EXECUTAR_SERVICO"}}'

4. Atualizar o estoque no os-management:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"ATUALIZAR_ESTOQUE"}}'
