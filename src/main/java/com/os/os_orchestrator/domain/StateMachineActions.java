package com.os.os_orchestrator.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StateMachineActions {

    CRIAR_OS("os-management-queue"),
    GERAR_ORCAMENTO("os-payment-queue"),
    GERAR_PAGAMENTO("os-payment-queue"),
    EXECUTAR_SERVICO("os-management-queue"),
    ATUALIZAR_ESTOQUE("os-management-queue"),
    NEGAR_SERVICO("os-management-queue"),
    NEGAR_ESTOQUE("os-management-queue"),
    ERRO("os-management-queue");

    private final String queueName;
}