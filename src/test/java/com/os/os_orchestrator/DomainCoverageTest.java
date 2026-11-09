package com.os.os_orchestrator;

import com.os.os_orchestrator.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainCoverageTest {
    @Test
    void exposesActionQueuesAndStateNames() {
        assertEquals("os-management-queue", StateMachineActions.CRIAR_OS.getQueueName());
        assertEquals("os-payment-queue", StateMachineActions.GERAR_ORCAMENTO.getQueueName());
        assertEquals("os-payment-queue", StateMachineActions.GERAR_PAGAMENTO.getQueueName());
        assertEquals("os-management-queue", StateMachineActions.EXECUTAR_SERVICO.getQueueName());
        assertEquals("os-management-queue", StateMachineActions.ATUALIZAR_ESTOQUE.getQueueName());
        assertEquals("os-management-queue", StateMachineActions.NEGAR_SERVICO.getQueueName());
        assertEquals("os-management-queue", StateMachineActions.NEGAR_ESTOQUE.getQueueName());
        assertEquals("os-management-queue", StateMachineActions.ERRO.getQueueName());
        for (StateMachineStates state : StateMachineStates.values()) assertEquals(state.name(), state.getState());
    }

    @Test
    void eventHasRecordValueSemantics() {
        StateMachineEvent event = new StateMachineEvent("order", StateMachineActions.CRIAR_OS);
        assertEquals("order", event.order());
        assertEquals(StateMachineActions.CRIAR_OS, event.action());
        assertEquals(event, new StateMachineEvent("order", StateMachineActions.CRIAR_OS));
        assertNotEquals(event, new StateMachineEvent("other", StateMachineActions.CRIAR_OS));
        assertTrue(event.toString().contains("order"));
    }

    @Test
    void resolvesEveryTransitionAndFallsBackToError() {
        assertEquals(StateMachineSteps.CRIAR_OS_STEP, StateMachineSteps.fromTransition(StateMachineStates.CRIAR_OS, StateMachineActions.CRIAR_OS));
        assertEquals(StateMachineSteps.GERAR_ORCAMENTO_STEP, StateMachineSteps.fromTransition(StateMachineStates.OS_CRIADA, StateMachineActions.GERAR_ORCAMENTO));
        assertEquals(StateMachineSteps.EXECUTAR_SERVICO_STEP, StateMachineSteps.fromTransition(StateMachineStates.ORCAMENTO_GERADO, StateMachineActions.EXECUTAR_SERVICO));
        assertEquals(StateMachineSteps.NEGAR_SERVICO_STEP, StateMachineSteps.fromTransition(StateMachineStates.ORCAMENTO_GERADO, StateMachineActions.NEGAR_SERVICO));
        assertEquals(StateMachineSteps.ATUALIZAR_ESTOQUE_STEP, StateMachineSteps.fromTransition(StateMachineStates.EXECUTAR_SERVICO, StateMachineActions.ATUALIZAR_ESTOQUE));
        assertEquals(StateMachineSteps.NEGAR_ESTOQUE_STEP, StateMachineSteps.fromTransition(StateMachineStates.NEGAR_SERVICO, StateMachineActions.NEGAR_ESTOQUE));
        assertEquals(StateMachineSteps.ERRO_STEP, StateMachineSteps.fromTransition(StateMachineStates.ERRO, null));
        assertEquals(StateMachineActions.GERAR_ORCAMENTO, StateMachineSteps.CRIAR_OS_STEP.getNextAction());
        assertNull(StateMachineSteps.ATUALIZAR_ESTOQUE_STEP.getNextAction());
    }
}
