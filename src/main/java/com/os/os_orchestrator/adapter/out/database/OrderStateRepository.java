package com.os.os_orchestrator.adapter.out.database;

import com.os.os_orchestrator.domain.StateMachineStates;
import com.os.os_orchestrator.domain.StateMachineEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.math.BigDecimal;

@Repository
public class OrderStateRepository {

    private final String tableName;

    private final DynamoDbClient dynamoDbClient;

    public OrderStateRepository(DynamoDbClient dynamoDbClient,
                                @Value("${app.aws.dynamodb.order-state-table}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void createState(StateMachineEvent event, StateMachineStates state) {
        Map<String, AttributeValue> item = item(event, state);

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(orderId)")
                .build());
    }

    public OrderState findState(String order) {
        Map<String, AttributeValue> key = Map.of(
                "orderId", AttributeValue.builder().s(order).build());

        var response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build());

        if (!response.hasItem()) {
            throw new OrderNotFoundException(order);
        }

        Map<String, AttributeValue> item = response.item();
        try {
            return new OrderState(StateMachineStates.valueOf(required(item, "state", order)),
                    required(item, "correlationId", order), new BigDecimal(required(item, "amount", order)));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Dados obrigatórios ausentes ou inválidos no DynamoDB para pedido: " + order, exception);
        }
    }

    public void updateState(StateMachineEvent event, StateMachineStates state) {
        Map<String, AttributeValue> item = item(event, state);

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    private Map<String, AttributeValue> item(StateMachineEvent event, StateMachineStates state) {
        if (event.correlationId() == null || event.correlationId().isBlank() || event.amount() == null) {
            throw new IllegalArgumentException("correlationId e amount são obrigatórios");
        }
        return Map.of("orderId", AttributeValue.builder().s(event.order()).build(),
                "state", AttributeValue.builder().s(state.name()).build(),
                "correlationId", AttributeValue.builder().s(event.correlationId()).build(),
                "amount", AttributeValue.builder().s(event.amount().toPlainString()).build());
    }

    private String required(Map<String, AttributeValue> item, String field, String order) {
        AttributeValue value = item.get(field);
        if (value == null || value.s() == null || value.s().isBlank()) {
            throw new IllegalStateException("Campo " + field + " ausente para pedido " + order);
        }
        return value.s();
    }

    public record OrderState(StateMachineStates state, String correlationId, BigDecimal amount) {}
}
