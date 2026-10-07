package com.os.os_orchestrator.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.os.os_orchestrator.domain.StateMachineStates.*;

@Getter
@RequiredArgsConstructor
public enum StateMachineSteps {

    CRIAR_OS_STEP(CRIAR_OS.getState(), OS_CRIADA.getState()),
    GERAR_ORCAMENTO_STEP(OS_CRIADA.getState(), GERAR_ORCAMENTO.getState()),
    AGUARDAR_APROVACAO_STEP(GERAR_ORCAMENTO.getState(), AGUARDAR_APROVACAO.getState()),
    EXECUTAR_SERVICO_STEP(AGUARDAR_APROVACAO.getState(), EXECUTAR_SERVICO.getState());

    private final String actual;
    private final String next;
}
