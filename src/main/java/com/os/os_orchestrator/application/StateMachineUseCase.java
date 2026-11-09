package com.os.os_orchestrator.application;

import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.adapter.out.database.OrderStateRepository.OrderState;
import com.os.os_orchestrator.adapter.out.database.OrderNotFoundException;
import com.os.os_orchestrator.adapter.out.sqs.ActionSenderService;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import com.os.os_orchestrator.domain.StateMachineSteps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StateMachineUseCase {

    @Autowired
    private OrderStateRepository orderStateRepository;

    @Autowired
    private ActionSenderService actionSenderService;

    public void process(StateMachineEvent event) {
        log.info("Evento recebido | Event: {}", event);

        StateMachineStates actualState;
        StateMachineEvent currentEvent = event;

        try {
            OrderState persisted = orderStateRepository.findState(event.order());
            actualState = persisted.state();
            currentEvent = new StateMachineEvent(event.order(), event.action(), persisted.correlationId(), persisted.amount());

            log.info("Ordem encontrada | pedido={} | estado={}",
                    event.order(), actualState);
        } catch (OrderNotFoundException exception) {
            actualState = StateMachineStates.CRIAR_OS;

            orderStateRepository.createState(event, actualState);

            log.info("Ordem criada | pedido={} | estado inicial={}",
                    event.order(), actualState);
        }

        StateMachineSteps step = StateMachineSteps.fromTransition(
                actualState,
                event.action()
        );

        StateMachineStates nextState =
                step == StateMachineSteps.ERRO_STEP
                        ? StateMachineStates.ERRO
                        : step.getNext();

        orderStateRepository.updateState(currentEvent, nextState);

        log.info(
                "Estado atualizado | pedido={} | estadoAtual={} | acao={} | proximoEstado={}",
                event.order(),
                actualState,
                event.action(),
                nextState
        );

        if (step.getNextAction() != null) {
            StateMachineEvent nextEvent = new StateMachineEvent(
                    event.order(),
                    step.getNextAction(),
                    currentEvent.correlationId(),
                    currentEvent.amount()
            );

            actionSenderService.sendAction(event.order(), nextEvent);

            log.info(
                    "Próxima ação enviada | pedido={} | ação={}",
                    event.order(),
                    step.getNextAction()
            );
        }
    }
}
