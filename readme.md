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
| `ORDER_STATE_TABLE` | `os-orchestrator-develop-order-state` |
| `AWS_REGION` | `us-east-1` |

### Metadados obrigatórios da saga

Cada item de `order-state` mantém `orderId`, `state`, `correlationId` e `amount`.
O DynamoDB não exige declarar atributos que não sejam chaves no Terraform, então
a tabela existente é atualizada in-place, sem recriação.

Antes do rollout da aplicação, a pipeline percorre a tabela e migra itens sem os
dois metadados. Para ordens existentes, configure o secret do GitHub Actions
`ORDER_STATE_MIGRATION_JSON` com um objeto JSON indexado por `orderId`, usando os
valores da fonte de dados confiável:

```json
{
  "order-123": {
    "correlationId": "123",
    "amount": "50.30"
  }
}
```

A migração valida todos os itens antes de gravar. Se faltar um pedido no mapa,
ou se os valores divergirem de atributos já persistidos, ela falha e impede o
rollout; nenhum item é alterado nessa validação. Itens completos são ignorados e
as atualizações usam `if_not_exists`, podendo ser repetidas com segurança. Para
tabela vazia ou já migrada, o secret pode conter `{}`.


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

### Testes

O `order-processing-queue` recebe o evento completo como JSON no corpo da mensagem.
`action` deve ser um valor de `StateMachineActions`; `order`, `correlationId` e
`amount` também devem ser informados. `amount` é um valor decimal representado
como string no JSON.

Exemplo de corpo:

```json
{
  "action": "CRIAR_OS",
  "order": "order-123",
  "correlationId": "123",
  "amount": "50.30"
}
```

Envio para o LocalStack (repita com a próxima ação do fluxo quando necessário;
para este contrato, não use `--message-attributes`):

```bash
aws --endpoint-url http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/order-processing-queue \
  --message-body '{"action":"CRIAR_OS","order":"order-123","correlationId":"123","amount":"50.30"}'
```
