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

1. Criar a OS do pedido no os-oschestrator:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"CRIAR_OS"}}'

2. Gerar o orçamento do pedido no os-payment:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"GERAR_ORCAMENTO"}}'

3. Executar o serviço no os-management:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"EXECUTAR_SERVICO"}}'

4. Atualizar o estoque no os-management:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-4444" --message-attributes '{"action":{"DataType":"String","StringValue":"ATUALIZAR_ESTOQUE"}}'
