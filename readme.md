### Passos

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

### Testes

1. Criar a OS do pedido:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-12345" --message-attributes '{"action":{"DataType":"String","StringValue":"CRIAR_OS"}}'

2. Gerar o orçamento do pedido:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-12345" --message-attributes '{"action":{"DataType":"String","StringValue":"GERAR_ORCAMENTO"}}'

3. Executar o serviço do pedido:
aws --endpoint-url http://localhost:4566 sqs send-message --queue-url http://localhost:4566/000000000000/order-processing-queue --message-body "order-12345" --message-attributes '{"action":{"DataType":"String","StringValue":"EXECUTAR_SERVICO"}}'

