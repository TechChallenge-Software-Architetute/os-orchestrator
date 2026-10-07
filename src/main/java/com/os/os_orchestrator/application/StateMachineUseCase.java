package com.os.os_orchestrator.application;

import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import com.os.os_orchestrator.domain.StateMachineSteps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StateMachineUseCase {

    @Autowired
    private OrderStateRepository orderStateRepository;

    public void process(StateMachineEvent event) {
        System.out.println(
                "[USE_CASE] Evento recebido | Pedido: " + event.order() +
                        " | Ação: " + event.action());

        if (event.action() == StateMachineActions.CRIAR_OS) {
            StateMachineSteps step = StateMachineSteps.fromTransition(
                    StateMachineStates.CRIAR_OS,
                    event.action());

            orderStateRepository.createState(
                    event.order(),
                    step.getNext());

            System.out.println(
                    "[USE_CASE] OS criada | Pedido: " + event.order() +
                            " | Estado: " + step.getNext());

            return;
        }

        StateMachineStates actualState =
                orderStateRepository.findState(event.order());

        StateMachineSteps step = StateMachineSteps.fromTransition(
                actualState,
                event.action());

        StateMachineStates nextState =
                step == StateMachineSteps.ERRO_STEP
                        ? StateMachineStates.ERRO
                        : step.getNext();

        orderStateRepository.updateState(event.order(), nextState);

        System.out.println(
                "[USE_CASE] Estado atualizado | Pedido: " + event.order() +
                        " | Estado atual: " + actualState +
                        " | Ação: " + event.action() +
                        " | Próximo estado: " + nextState);
    }
}