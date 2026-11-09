package com.os.os_orchestrator;

import com.os.os_orchestrator.adapter.out.database.OrderNotFoundException;
import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.adapter.out.sqs.ActionSenderService;
import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateMachineUseCaseTest {
    private static final String CORRELATION_ID = "corr-456";
    private static final BigDecimal AMOUNT = new BigDecimal("50.30");

    @Mock private OrderStateRepository orderStateRepository;
    @Mock private ActionSenderService actionSenderService;
    @InjectMocks private StateMachineUseCase stateMachineUseCase;

    @Test void shouldCreateOrderAndSendNextActionWhenOrderDoesNotExist() {
        StateMachineEvent event = event(StateMachineActions.CRIAR_OS);
        when(orderStateRepository.findState(event.order())).thenThrow(new OrderNotFoundException(event.order()));
        stateMachineUseCase.process(event);
        verify(orderStateRepository).createState(event, StateMachineStates.CRIAR_OS);
        verify(orderStateRepository).updateState(event, StateMachineStates.OS_CRIADA);
        verify(actionSenderService).sendAction(event.order(), event(StateMachineActions.GERAR_ORCAMENTO));
    }

    @Test void shouldFollowFlowWhenOrderAlreadyExistsAndUsePersistedMetadata() {
        StateMachineEvent incoming = event(StateMachineActions.GERAR_ORCAMENTO);
        StateMachineEvent persistedEvent = new StateMachineEvent(incoming.order(), incoming.action(), "persisted-correlation", new BigDecimal("75.25"));
        when(orderStateRepository.findState(incoming.order())).thenReturn(new OrderStateRepository.OrderState(
                StateMachineStates.OS_CRIADA, persistedEvent.correlationId(), persistedEvent.amount()));
        stateMachineUseCase.process(incoming);
        verify(orderStateRepository, never()).createState(any(), any());
        verify(orderStateRepository).updateState(persistedEvent, StateMachineStates.ORCAMENTO_GERADO);
        verify(actionSenderService).sendAction(incoming.order(), new StateMachineEvent(incoming.order(),
                StateMachineActions.EXECUTAR_SERVICO, persistedEvent.correlationId(), persistedEvent.amount()));
    }

    @Test void shouldSendInventoryActionWithPersistedMetadata() {
        StateMachineEvent incoming = event(StateMachineActions.EXECUTAR_SERVICO);
        when(orderStateRepository.findState(incoming.order())).thenReturn(new OrderStateRepository.OrderState(
                StateMachineStates.ORCAMENTO_GERADO, CORRELATION_ID, AMOUNT));
        stateMachineUseCase.process(incoming);
        verify(orderStateRepository).updateState(new StateMachineEvent(incoming.order(), incoming.action(), CORRELATION_ID, AMOUNT), StateMachineStates.EXECUTAR_SERVICO);
        verify(actionSenderService).sendAction(incoming.order(), event(StateMachineActions.ATUALIZAR_ESTOQUE));
    }

    @Test void shouldNotSendNextActionWhenStepIsFinal() {
        StateMachineEvent incoming = event(StateMachineActions.ATUALIZAR_ESTOQUE);
        when(orderStateRepository.findState(incoming.order())).thenReturn(new OrderStateRepository.OrderState(
                StateMachineStates.EXECUTAR_SERVICO, CORRELATION_ID, AMOUNT));
        stateMachineUseCase.process(incoming);
        verify(orderStateRepository).updateState(new StateMachineEvent(incoming.order(), incoming.action(), CORRELATION_ID, AMOUNT), StateMachineStates.ATUALIZAR_ESTOQUE);
        verifyNoInteractions(actionSenderService);
    }

    @Test void shouldMoveOrderToErrorWhenTransitionIsInvalid() {
        StateMachineEvent incoming = event(StateMachineActions.GERAR_ORCAMENTO);
        when(orderStateRepository.findState(incoming.order())).thenReturn(new OrderStateRepository.OrderState(
                StateMachineStates.EXECUTAR_SERVICO, CORRELATION_ID, AMOUNT));
        stateMachineUseCase.process(incoming);
        verify(orderStateRepository).updateState(new StateMachineEvent(incoming.order(), incoming.action(), CORRELATION_ID, AMOUNT), StateMachineStates.ERRO);
        verifyNoInteractions(actionSenderService);
    }

    @Test void shouldNotTreatIncompletePersistedItemAsNewOrder() {
        StateMachineEvent event = event(StateMachineActions.CRIAR_OS);
        when(orderStateRepository.findState(event.order())).thenThrow(new IllegalStateException("Campo amount ausente"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> stateMachineUseCase.process(event));
        verify(orderStateRepository, never()).createState(any(), any());
        verifyNoInteractions(actionSenderService);
    }

    private StateMachineEvent event(StateMachineActions action) {
        return new StateMachineEvent("order-123", action, CORRELATION_ID, AMOUNT);
    }
}
