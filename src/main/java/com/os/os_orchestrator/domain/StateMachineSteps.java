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
            null
    ),

    ORCAMENTO_DISPONIVEL_STEP(
            StateMachineActions.ORCAMENTO_DISPONIVEL,
            OS_CRIADA,
            GERAR_ORCAMENTO,
            StateMachineActions.GERAR_ORCAMENTO
    ),

    ORCAMENTO_DISPONIVEL_DUPLICADO_STEP(
            StateMachineActions.ORCAMENTO_DISPONIVEL,
            GERAR_ORCAMENTO,
            GERAR_ORCAMENTO,
            null
    ),

    ORCAMENTO_DISPONIVEL_APOS_GERACAO_STEP(
            StateMachineActions.ORCAMENTO_DISPONIVEL,
            ORCAMENTO_GERADO,
            ORCAMENTO_GERADO,
            null
    ),

    GERAR_ORCAMENTO_STEP(
            StateMachineActions.GERAR_ORCAMENTO,
            GERAR_ORCAMENTO,
            ORCAMENTO_GERADO,
            null
    ),

    PAGAMENTO_APROVADO_ANTES_DO_CLIENTE_STEP(
            StateMachineActions.PAGAMENTO_APROVADO,
            ORCAMENTO_GERADO,
            PAGAMENTO_APROVADO,
            StateMachineActions.ATUALIZAR_ESTOQUE
    ),

    EXECUTAR_SERVICO_STEP(
            StateMachineActions.EXECUTAR_SERVICO,
            PAGAMENTO_APROVADO,
            EXECUTAR_SERVICO,
            StateMachineActions.ATUALIZAR_ESTOQUE
    ),

    PAGAMENTO_APROVACAO_DUPLICADA_STEP(
            StateMachineActions.PAGAMENTO_APROVADO,
            PAGAMENTO_APROVADO,
            PAGAMENTO_APROVADO,
            StateMachineActions.NEGAR_SERVICO
    ),

    NEGAR_SERVICO_STEP(
            StateMachineActions.NEGAR_SERVICO,
            GERAR_ORCAMENTO,
            NEGAR_SERVICO,
            StateMachineActions.NEGAR_SERVICO
    ),

    NEGAR_SERVICO_APOS_ORCAMENTO_STEP(
            StateMachineActions.NEGAR_SERVICO,
            ORCAMENTO_GERADO,
            NEGAR_SERVICO,
            StateMachineActions.NEGAR_SERVICO
    ),

    ATUALIZAR_ESTOQUE_STEP(
            StateMachineActions.ATUALIZAR_ESTOQUE,
            EXECUTAR_SERVICO,
            ATUALIZAR_ESTOQUE,
            null
    ),

    NEGAR_ESTOQUE_STEP(
            StateMachineActions.ATUALIZAR_ESTOQUE,
            ORCAMENTO_GERADO,
            NEGAR_ESTOQUE,
            StateMachineActions.NEGAR_ESTOQUE
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
