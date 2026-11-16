package com.os.os_orchestrator.adapter.out.sqs;

import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActionSenderServiceImplTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @Captor
    private ArgumentCaptor<Message<String>> message;

    private ActionSenderServiceImpl sender;

    @BeforeEach
    void setUp() {
        sender = new ActionSenderServiceImpl(sqsTemplate, "orchestrator-q", "management-q", "payment-q");
    }

    @Test
    void sendsEachActionToTheQueueOfItsDestination() {
        sender.sendAction("order-1", new StateMachineEvent("order-1", StateMachineActions.GERAR_ORCAMENTO));
        sender.sendAction("order-1", new StateMachineEvent("order-1", StateMachineActions.EXECUTAR_SERVICO));

        verify(sqsTemplate).send(eq("payment-q"), any(Message.class));
        verify(sqsTemplate).send(eq("management-q"), any(Message.class));
    }

    @Test
    void sendsOrderIdAsBodyAndTheRestAsHeaders() {
        sender.sendAction("order-1", new StateMachineEvent(
                "order-1", StateMachineActions.GERAR_ORCAMENTO, new BigDecimal("179.80"), "corr-1"));

        verify(sqsTemplate).send(eq("payment-q"), message.capture());
        assertThat(message.getValue().getPayload()).isEqualTo("order-1");
        assertThat(message.getValue().getHeaders())
                .containsEntry("action", "GERAR_ORCAMENTO")
                .containsEntry("amount", "179.80")
                .containsEntry("correlationId", "corr-1");
    }

    @Test
    void leavesOutAmountAndCorrelationIdWhenTheEventHasNone() {
        sender.sendAction("order-1", new StateMachineEvent(
                "order-1", StateMachineActions.EXECUTAR_SERVICO, null, " "));

        verify(sqsTemplate).send(eq("management-q"), message.capture());
        assertThat(message.getValue().getHeaders())
                .containsEntry("action", "EXECUTAR_SERVICO")
                .doesNotContainKeys("amount", "correlationId");
    }
}
