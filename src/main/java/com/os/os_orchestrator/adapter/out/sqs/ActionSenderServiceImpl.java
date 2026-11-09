package com.os.os_orchestrator.adapter.out.sqs;

import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActionSenderServiceImpl implements ActionSenderService {

    private final SqsTemplate sqsTemplate;

    public ActionSenderServiceImpl(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }
    @Override
    public void sendAction(String order, StateMachineEvent event) {
        sqsTemplate.send(
                event.action().getQueueName(),
                event
        );
    }
}
