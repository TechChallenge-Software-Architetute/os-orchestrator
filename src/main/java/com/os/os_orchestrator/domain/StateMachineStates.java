package com.os.os_orchestrator.domain;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StateMachineStates {

    CRIAR_OS("CRIAR_OS"),
    OS_CRIADA("OS_CRIADA"),
    GERAR_ORCAMENTO("GERAR_ORCAMENTO"),
    ORCAMENTO_GERADO("ORCAMENTO_GERADO"),
    AGUARDAR_APROVACAO("AGUARDAR_APROVACAO"),
    ORCAMENTO_APROVADO("ORCAMENTO_APROVADO"),
    EXECUTAR_SERVICO("EXECUTAR_SERVICO");

    private final String state;

}
