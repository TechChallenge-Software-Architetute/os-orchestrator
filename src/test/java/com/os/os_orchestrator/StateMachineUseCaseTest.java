package com.os.os_orchestrator;

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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateMachineUseCaseTest {

    @Mock
    private OrderStateRepository orderStateRepository;

    @Mock
    private ActionSenderService actionSenderService;

    @InjectMocks
    private StateMachineUseCase stateMachineUseCase;

    @Test
    void shouldCreateOrderWithoutStartingPaymentBeforeBudgetExists() {
        String order = "order-123";
        StateMachineEvent event = new StateMachineEvent(
                order,
                StateMachineActions.CRIAR_OS
        );

        when(orderStateRepository.findState(order))
                .thenThrow(new IllegalStateException("Pedido não encontrado"));

        stateMachineUseCase.process(event);

        verify(orderStateRepository).createState(
                order,
                StateMachineStates.CRIAR_OS
        );

        verify(orderStateRepository).updateState(
                order,
                StateMachineStates.OS_CRIADA
        );

        verifyNoInteractions(actionSenderService);
    }

    @Test
    void shouldFollowFlowWhenOrderAlreadyExists() {
        String order = "order-123";
        StateMachineEvent event = new StateMachineEvent(
                order,
                StateMachineActions.ORCAMENTO_DISPONIVEL
        );

        when(orderStateRepository.findState(order))
                .thenReturn(StateMachineStates.OS_CRIADA);

        stateMachineUseCase.process(event);

        verify(orderStateRepository, never())
                .createState(anyString(), any(StateMachineStates.class));

        verify(orderStateRepository).updateState(
                order,
                StateMachineStates.GERAR_ORCAMENTO
        );

        verify(actionSenderService).sendAction(
                order,
                new StateMachineEvent(
                        order,
                        StateMachineActions.GERAR_ORCAMENTO
                )
        );
    }

    @Test
    void paymentApprovalSendsTheStockUpdate() {
        String order = "order-123";
        StateMachineEvent event = new StateMachineEvent(
                order,
                StateMachineActions.PAGAMENTO_APROVADO
        );

        when(orderStateRepository.findState(order))
                .thenReturn(StateMachineStates.ORCAMENTO_GERADO);

        stateMachineUseCase.process(event);

        verify(orderStateRepository).updateState(
                order,
                StateMachineStates.PAGAMENTO_APROVADO
        );

        verify(actionSenderService).sendAction(
                order,
                new StateMachineEvent(order, StateMachineActions.ATUALIZAR_ESTOQUE)
        );
    }

    @Test
    void duplicatedPaymentApprovalDoesNotSendTheStockUpdateTwice() {
        String order = "order-123";
        when(orderStateRepository.findState(order)).thenReturn(StateMachineStates.PAGAMENTO_APROVADO);

        stateMachineUseCase.process(new StateMachineEvent(order, StateMachineActions.PAGAMENTO_APROVADO));

        verifyNoInteractions(actionSenderService);
    }

    @Test
    void stockUpdateConfirmationFinishesTheSaga() {
        String order = "order-123";
        when(orderStateRepository.findState(order)).thenReturn(StateMachineStates.PAGAMENTO_APROVADO);

        stateMachineUseCase.process(new StateMachineEvent(order, StateMachineActions.ATUALIZAR_ESTOQUE));

        verify(orderStateRepository).updateState(order, StateMachineStates.ATUALIZAR_ESTOQUE);
        verifyNoInteractions(actionSenderService);
    }

    @Test
    void shouldMoveOrderToErrorWhenTransitionIsInvalid() {
        String order = "order-123";
        StateMachineEvent event = new StateMachineEvent(
                order,
                StateMachineActions.GERAR_ORCAMENTO
        );

        when(orderStateRepository.findState(order))
                .thenReturn(StateMachineStates.EXECUTAR_SERVICO);

        stateMachineUseCase.process(event);

        verify(orderStateRepository).updateState(
                order,
                StateMachineStates.ERRO
        );

        verifyNoInteractions(actionSenderService);
    }

    @Test
    void forwardsAmountAndCorrelationIdToTheNextAction() {
        String order = "order-123";
        when(orderStateRepository.findState(order)).thenReturn(StateMachineStates.OS_CRIADA);

        stateMachineUseCase.process(new StateMachineEvent(
                order, StateMachineActions.ORCAMENTO_DISPONIVEL, new BigDecimal("179.80"), "corr-1"));

        verify(actionSenderService).sendAction(order, new StateMachineEvent(
                order, StateMachineActions.GERAR_ORCAMENTO, new BigDecimal("179.80"), "corr-1"));
    }

    @Test
    void leavesTheStateUntouchedWhenTheLookupFailsForAnyOtherReason() {
        String order = "order-123";
        when(orderStateRepository.findState(order)).thenThrow(new RuntimeException("DynamoDB indisponivel"));

        assertThrows(RuntimeException.class, () -> stateMachineUseCase.process(
                new StateMachineEvent(order, StateMachineActions.ORCAMENTO_DISPONIVEL)));

        verify(orderStateRepository, never()).createState(anyString(), any(StateMachineStates.class));
        verify(orderStateRepository, never()).updateState(anyString(), any(StateMachineStates.class));
        verifyNoInteractions(actionSenderService);
    }
}
