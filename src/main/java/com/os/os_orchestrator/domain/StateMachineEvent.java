package com.os.os_orchestrator.domain;

public record StateMachineEvent(
        String order,
        StateMachineActions action,
        String correlationId,
        java.math.BigDecimal amount
) {
}
