package com.os.os_orchestrator.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

import static com.os.os_orchestrator.domain.StateMachineStates.*;

@Getter
@RequiredArgsConstructor
public enum StateMachineSteps {

    CRIAR_OS_STEP(StateMachineActions.CRIAR_OS, CRIAR_OS, OS_CRIADA),
    GERAR_ORCAMENTO_STEP(StateMachineActions.GERAR_ORCAMENTO, OS_CRIADA, ORCAMENTO_GERADO),
    EXECUTAR_SERVICO_STEP(StateMachineActions.EXECUTAR_SERVICO, ORCAMENTO_GERADO, EXECUTAR_SERVICO),
    ERRO_STEP(null, ERRO, ERRO);

    private final StateMachineActions action;
    private final StateMachineStates actual;
    private final StateMachineStates next;

    public static StateMachineSteps fromTransition(
            StateMachineStates actual,
            StateMachineActions action) {

        StateMachineSteps step = Arrays.stream(values())
                .filter(candidate ->
                        candidate.action == action &&
                                candidate.actual == actual)
                .findFirst()
                .orElse(ERRO_STEP);

        System.out.println(
                "[STATE_MACHINE] Estado atual: " + actual +
                        " | Ação recebida: " + action +
                        " | Step selecionado: " + step);

        return step;
    }
}
