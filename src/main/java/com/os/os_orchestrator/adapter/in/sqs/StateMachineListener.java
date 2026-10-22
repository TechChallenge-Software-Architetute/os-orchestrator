package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class StateMachineListener {

    @Autowired
    private StateMachineUseCase stateMachineUseCase;

    @SqsListener("order-processing-queue")
    public void processOrder(@Payload String order,
                             @Header("action") StateMachineActions action) {

        System.out.println("| Processando pedido: " + order + "| Solicitacao de troca de estado: " + action);

        StateMachineEvent event = new StateMachineEvent(order, action);

        stateMachineUseCase.process(event);
    }
}
