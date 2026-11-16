package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StateMachineListenerTest {

    @Mock
    private StateMachineUseCase stateMachineUseCase;

    @InjectMocks
    private StateMachineListener listener;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void passesTheMessageFieldsToTheUseCase() {
        listener.processOrder("order-1", StateMachineActions.ORCAMENTO_DISPONIVEL, "179.80", "corr-1");

        verify(stateMachineUseCase).process(new StateMachineEvent(
                "order-1", StateMachineActions.ORCAMENTO_DISPONIVEL, new BigDecimal("179.80"), "corr-1"));
    }

    @Test
    void generatesACorrelationIdAndSkipsTheAmountWhenTheMessageHasNone() {
        listener.processOrder("order-1", StateMachineActions.CRIAR_OS, "", null);

        ArgumentCaptor<StateMachineEvent> event = ArgumentCaptor.forClass(StateMachineEvent.class);
        verify(stateMachineUseCase).process(event.capture());
        assertThat(event.getValue().amount()).isNull();
        assertThat(UUID.fromString(event.getValue().correlationId())).isNotNull();
    }

    @Test
    void rejectsAMalformedAmountWithoutProcessingTheEvent() {
        // A excecao deixa a mensagem sem ack: ela volta para a fila e, depois do
        // maxReceiveCount, vai para a DLQ, em vez de avancar o estado com valor errado.
        assertThatThrownBy(() ->
                listener.processOrder("order-1", StateMachineActions.CRIAR_OS, "abc", "corr-1"))
                .isInstanceOf(NumberFormatException.class);

        verifyNoInteractions(stateMachineUseCase);
    }
}
