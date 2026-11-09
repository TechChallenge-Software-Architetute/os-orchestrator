package com.os.os_orchestrator.adapter.out.database;

public class OrderNotFoundException extends IllegalStateException {
    public OrderNotFoundException(String order) {
        super("Pedido não encontrado no DynamoDB: " + order);
    }
}
