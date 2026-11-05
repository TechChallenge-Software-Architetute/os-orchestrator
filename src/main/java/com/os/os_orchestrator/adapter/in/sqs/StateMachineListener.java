package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StateMachineListener {

    private final StateMachineUseCase stateMachineUseCase;

    public StateMachineListener(StateMachineUseCase stateMachineUseCase) {
        this.stateMachineUseCase = stateMachineUseCase;
    }

    /**
     * Inbound saga queue. The name is a property rather than a literal so the queue
     * can be environment-suffixed (os-orchestrator-&lt;env&gt;-order-processing) while
     * local development keeps using the LocalStack queue from init-aws.sh.
     */
    @SqsListener("${app.aws.sqs.order-processing-queue}")
    public void processOrder(@Payload String order,
                             @Header("action") StateMachineActions action) {

        log.info("Processando pedido: {} | Solicitacao de troca de estado: {}", order, action);

        StateMachineEvent event = new StateMachineEvent(order, action);

        stateMachineUseCase.process(event);
    }
}
