package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
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
                             @Header("action") StateMachineStates action) {

        System.out.println("Processando pedido: " + order + " com ação: " + action);

        stateMachineUseCase.process(order, action);
    }
}
