package com.os.os_orchestrator.adapter.out.sqs;

import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class ActionSenderServiceImpl implements ActionSenderService {

    private final SqsTemplate sqsTemplate;

    public ActionSenderServiceImpl(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
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
        sqsTemplate.send(event.action().getQueueName(), message.build());
    }
}
