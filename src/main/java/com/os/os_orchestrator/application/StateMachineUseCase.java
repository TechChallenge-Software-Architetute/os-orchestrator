package com.os.os_orchestrator.application;

import com.os.os_orchestrator.domain.StateMachineStates;
import com.os.os_orchestrator.domain.StateMachineSteps;
import org.springframework.stereotype.Service;

@Service
public class StateMachineUseCase {

    public void process(String order, StateMachineStates action) {

        System.out.println("Processando pedido: " + order);

        StateMachineSteps step = StateMachineSteps.valueOf(action.name() + "_STEP");
    }
}
