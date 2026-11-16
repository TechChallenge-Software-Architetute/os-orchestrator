package com.os.os_orchestrator.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineStepsTest {

    @Test
    void noTwoStepsShareTheSameTransition() {
        // fromTransition devolve o primeiro que casa: um par repetido esconderia o segundo.
        var seen = new HashSet<String>();
        for (StateMachineSteps step : StateMachineSteps.values()) {
            if (step == StateMachineSteps.ERRO_STEP) {
                continue;
            }
            assertThat(seen.add(step.getAction() + " em " + step.getActual()))
                    .as("transicao repetida: %s", step)
                    .isTrue();
        }
    }

    @Test
    void unknownTransitionFallsBackToErrorStep() {
        assertThat(StateMachineSteps.fromTransition(
                StateMachineStates.EXECUTAR_SERVICO, StateMachineActions.GERAR_ORCAMENTO))
                .isEqualTo(StateMachineSteps.ERRO_STEP);
    }

    @Test
    void noStepSendsAnActionBackToTheOrchestratorQueue() {
        // O orquestrador so envia para os-management e os-payment. Uma acao com destino
        // ORCHESTRATOR como proxima acao faria a mensagem voltar para a propria fila.
        for (StateMachineSteps step : StateMachineSteps.values()) {
            if (step.getNextAction() != null) {
                assertThat(step.getNextAction().getDestination())
                        .as("destino da proxima acao de %s", step)
                        .isNotEqualTo(StateMachineActions.Destination.ORCHESTRATOR);
            }
        }
    }
}
