# language: pt
Funcionalidade: Orquestrar uma comunicações entre os serviços do os-management e os-payment

  Cenario: Receber um pedido de OS com sucesso
    Dado um pedido de troca de pneu com cliente válido
    Quando recebermos um pedido de criação de OS
    Então devemos realizar o serviço do cliente

  Cenario: Cliente não pagou a OS
    Dado um pedido de troca de pneu com cliente sem saldo em conta
    Quando recebermos um pedido de criação de OS
    Então não devemos realizar o serviço do cliente

  Cenario: Cliente que não possui cadastro
    Dado um pedido de troca de pneu com erro ao gerar o orçamento
    Quando recebermos um pedido de criação de OS
    Então não devemos realizar o serviço do cliente

