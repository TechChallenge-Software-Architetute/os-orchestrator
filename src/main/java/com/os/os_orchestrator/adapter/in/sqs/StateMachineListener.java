package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;


@Slf4j
@Component
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
    @SqsListener("order-processing-queue")
    public void processOrder(@Payload StateMachineEvent event) {

        if (event == null || event.order() == null || event.order().isBlank()
                || event.action() == null || event.correlationId() == null || event.correlationId().isBlank()
                || event.amount() == null) {
            throw new IllegalArgumentException("Mensagem inválida: order, action, correlationId e amount válidos são obrigatórios");
        }

        log.info("Evento recebido. {}", event);
        MDC.put("order", event.order());
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId());
        log.info("Inicio do processamento do pedido: {} | Solicitacao de troca de estado: {}", event.order(), event.action());

        try {
            stateMachineUseCase.process(event);
            log.info("Fim do processamento do pedido: {} | Solicitacao de troca de estado: {}", event.order(), event.action());
        } finally {
            MDC.clear();
        }
    }
}
