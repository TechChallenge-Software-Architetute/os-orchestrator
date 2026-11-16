package com.os.os_orchestrator.application;

import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalErrorHandler {

    private final OrderStateRepository orderStateRepository;

    public GlobalErrorHandler(OrderStateRepository orderStateRepository) {
        this.orderStateRepository = orderStateRepository;
    }

    public void handle(StateMachineEvent event, Exception exception) {
        log.error(
                "Erro ao processar pedido | pedido={} | acao={}",
                event.order(),
                event.action(),
                exception
        );

        try {
            orderStateRepository.updateState(
                    event.order(),
                    StateMachineStates.ERRO
            );

            log.info(
                    "Pedido atualizado para ERRO | pedido={}",
                    event.order()
            );
        } catch (Exception stateUpdateException) {
            log.error(
                    "Não foi possível atualizar o pedido para ERRO | pedido={}",
                    event.order(),
                    stateUpdateException
            );
        }
        MDC.clear();
    }
}
