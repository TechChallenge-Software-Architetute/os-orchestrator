package com.os.os_orchestrator.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.os.os_orchestrator.domain.StateMachineStates.*;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum StateMachineSteps {

    CRIAR_OS_STEP(
            StateMachineActions.CRIAR_OS,
            CRIAR_OS,
            OS_CRIADA,
            StateMachineActions.GERAR_ORCAMENTO
    ),

    GERAR_ORCAMENTO_STEP(
            StateMachineActions.GERAR_ORCAMENTO,
            OS_CRIADA,
            ORCAMENTO_GERADO,
            StateMachineActions.EXECUTAR_SERVICO
    ),

    EXECUTAR_SERVICO_STEP(
            StateMachineActions.EXECUTAR_SERVICO,
            ORCAMENTO_GERADO,
            EXECUTAR_SERVICO,
            StateMachineActions.ATUALIZAR_ESTOQUE
    ),

    NEGAR_SERVICO_STEP(
            StateMachineActions.NEGAR_SERVICO,
            ORCAMENTO_GERADO,
            NEGAR_SERVICO,
            StateMachineActions.NEGAR_ESTOQUE
    ),

    ATUALIZAR_ESTOQUE_STEP(
            StateMachineActions.ATUALIZAR_ESTOQUE,
            EXECUTAR_SERVICO,
            ATUALIZAR_ESTOQUE,
            null
    ),

    NEGAR_ESTOQUE_STEP(
            StateMachineActions.NEGAR_ESTOQUE,
            NEGAR_SERVICO,
            NEGAR_ESTOQUE,
            null
    ),

    ERRO_STEP(
            null,
            StateMachineStates.ERRO,
            StateMachineStates.ERRO,
            null
    );

    private final StateMachineActions action;
    private final StateMachineStates actual;
    private final StateMachineStates next;
    private final StateMachineActions nextAction;

    public static StateMachineSteps fromTransition(
            StateMachineStates actual,
            StateMachineActions action
    ) {
        return Arrays.stream(values())
                .filter(candidate ->
                        candidate.action == action
                                && candidate.actual == actual)
                .findFirst()
                .orElse(ERRO_STEP);
    }
}
