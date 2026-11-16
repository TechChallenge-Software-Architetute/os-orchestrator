package com.os.os_orchestrator.adapter.out.sqs;

import com.os.os_orchestrator.domain.StateMachineEvent;

public interface ActionSenderService {
    void sendAction(String order, StateMachineEvent event);
}
