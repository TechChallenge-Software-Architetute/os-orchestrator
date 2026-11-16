package com.os.os_orchestrator.adapter.in.sqs;

import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class StateMachineListener {

    private final StateMachineUseCase stateMachineUseCase;

    public StateMachineListener(StateMachineUseCase stateMachineUseCase) {
        this.stateMachineUseCase = stateMachineUseCase;
    }

    /**
     * Inbound saga queues. The names are properties rather than literals so the queues
     * can be environment-suffixed while local development keeps using the LocalStack
     * queues from init-aws.sh.
     *
     * <ul>
     *   <li>{@code order-processing-queue} (os-orchestrator-&lt;env&gt;-order-processing):
     *       commands from os-management.</li>
     *   <li>{@code payment-status-queue} (os-payment-&lt;env&gt;-payment-status): replies from
     *       os-payment. Same message format (order id in the body, {@code action},
     *       {@code amount} and {@code correlationId} as attributes), so both queues
     *       feed the same state machine.</li>
     * </ul>
     */
    @SqsListener({"${app.aws.sqs.order-processing-queue}", "${app.aws.sqs.payment-status-queue}"})
    public void processOrder(@Payload String order,
                             @Header("action") StateMachineActions action,
                             @Header(name = "amount", required = false) String amount,
                             @Header(name = "correlationId", required = false) String correlationId) {

        log.info("Evento recebido. {} - {}", order, action);
        MDC.put("order", order);
        String eventCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        MDC.put("correlationId", eventCorrelationId);
        log.info("Inicio do processamento do pedido: {} | Solicitacao de troca de estado: {}", order, action);

        BigDecimal orderAmount = amount == null || amount.isBlank() ? null : new BigDecimal(amount);
        StateMachineEvent event = new StateMachineEvent(order, action, orderAmount, eventCorrelationId);

        stateMachineUseCase.process(event);

        log.info("Fim do processamento do pedido: {} | Solicitacao de troca de estado: {}", order, action);
        MDC.clear();
    }
}
