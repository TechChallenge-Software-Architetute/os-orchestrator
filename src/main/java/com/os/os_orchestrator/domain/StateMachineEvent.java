package com.os.os_orchestrator.domain;

import java.math.BigDecimal;

public record StateMachineEvent(
        String order,
        StateMachineActions action,
        BigDecimal amount,
        String correlationId
) {
    public StateMachineEvent(String order, StateMachineActions action) {
        this(order, action, null, null);
    }

    public StateMachineEvent(String order, StateMachineActions action, BigDecimal amount) {
        this(order, action, amount, null);
    }
}
