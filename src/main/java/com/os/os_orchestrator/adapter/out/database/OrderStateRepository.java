package com.os.os_orchestrator.adapter.out.database;

import com.os.os_orchestrator.domain.StateMachineStates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@Repository
public class OrderStateRepository {

    private final String tableName;

    private final DynamoDbClient dynamoDbClient;

    public OrderStateRepository(DynamoDbClient dynamoDbClient,
                                @Value("${app.aws.dynamodb.order-state-table}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void createState(String order, StateMachineStates state) {
        Map<String, AttributeValue> item = Map.of(
                "orderId", AttributeValue.builder().s(order).build(),
                "state", AttributeValue.builder().s(state.name()).build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(orderId)")
                .build());
    }

    public StateMachineStates findState(String order) {
        Map<String, AttributeValue> key = Map.of(
                "orderId", AttributeValue.builder().s(order).build());

        var response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build());

        if (!response.hasItem()) {
            throw new IllegalStateException(
                    "Pedido não encontrado no DynamoDB: " + order);
        }

        return StateMachineStates.valueOf(
                response.item().get("state").s());
    }

    public void updateState(String order, StateMachineStates state) {
        Map<String, AttributeValue> item = Map.of(
                "orderId", AttributeValue.builder().s(order).build(),
                "state", AttributeValue.builder().s(state.name()).build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }
}