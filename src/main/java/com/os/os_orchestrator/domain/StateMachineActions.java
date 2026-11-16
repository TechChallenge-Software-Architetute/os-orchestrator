package com.os.os_orchestrator.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StateMachineActions {

    CRIAR_OS(Destination.MANAGEMENT),
    ORCAMENTO_DISPONIVEL(Destination.ORCHESTRATOR),
    ORCAMENTO_APROVADO(Destination.ORCHESTRATOR),
    PAGAMENTO_APROVADO(Destination.ORCHESTRATOR),
    GERAR_ORCAMENTO(Destination.PAYMENT),
    GERAR_PAGAMENTO(Destination.PAYMENT),
    EXECUTAR_SERVICO(Destination.MANAGEMENT),
    ATUALIZAR_ESTOQUE(Destination.MANAGEMENT),
    NEGAR_SERVICO(Destination.MANAGEMENT),
    NEGAR_ESTOQUE(Destination.MANAGEMENT),
    ERRO(Destination.MANAGEMENT);

    /**
     * Servico que recebe a acao. O nome da fila de cada um depende do ambiente
     * (os-<servico>-<env>-<finalidade>) e vem da configuracao, nao daqui.
     */
    public enum Destination {
        ORCHESTRATOR,
        MANAGEMENT,
        PAYMENT
    }

    private final Destination destination;
}
