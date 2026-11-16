package com.os.os_orchestrator.adapter.out.sqs;

import com.os.os_orchestrator.domain.StateMachineActions.Destination;
import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ActionSenderServiceImpl implements ActionSenderService {

    private final SqsTemplate sqsTemplate;
    private final Map<Destination, String> queues;

    /**
     * As filas tem nome por ambiente (os-&lt;servico&gt;-&lt;env&gt;-&lt;finalidade&gt;) e chegam
     * pelo ConfigMap. Sem default de proposito: o SqsTemplate cria em silencio uma
     * fila que nao existe, entao um nome errado nao falharia, so mandaria a mensagem
     * para uma fila que ninguem le.
     */
    public ActionSenderServiceImpl(SqsTemplate sqsTemplate,
                                   @Value("${app.aws.sqs.order-processing-queue}") String orchestratorQueue,
                                   @Value("${app.aws.sqs.management-queue}") String managementQueue,
                                   @Value("${app.aws.sqs.payment-queue}") String paymentQueue) {
        this.sqsTemplate = sqsTemplate;
        this.queues = Map.of(
                Destination.ORCHESTRATOR, orchestratorQueue,
                Destination.MANAGEMENT, managementQueue,
                Destination.PAYMENT, paymentQueue);
    }

    @Override
    public void sendAction(String order, StateMachineEvent event) {
        var message = MessageBuilder.withPayload(order)
                .setHeader("action", event.action().name());
        if (event.amount() != null) {
            message.setHeader("amount", event.amount().toPlainString());
        }
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            message.setHeader("correlationId", event.correlationId());
        }
        sqsTemplate.send(queues.get(event.action().getDestination()), message.build());
    }
}
