package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class StateMachineListener {

    @Autowired
    private StateMachineUseCase stateMachineUseCase;

    @SqsListener("order-processing-queue")
    public void processOrder(@Payload String order,
                             @Header("action") StateMachineActions action) {

        log.info("Evento recebido. {} - {}", order, action);
        MDC.put("order", order);
        MDC.put("correlationId", UUID.randomUUID().toString());
        log.info("Inicio do processamento do pedido: {} | Solicitacao de troca de estado: {}", order, action);

        StateMachineEvent event = new StateMachineEvent(order, action);

        stateMachineUseCase.process(event);

        log.info("Fim do processamento do pedido: {} | Solicitacao de troca de estado: {}", order, action);
        MDC.clear();
    }
}
