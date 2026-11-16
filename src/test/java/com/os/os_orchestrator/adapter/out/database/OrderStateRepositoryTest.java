package com.os.os_orchestrator.adapter.out.database;

import com.os.os_orchestrator.domain.StateMachineStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStateRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private OrderStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OrderStateRepository(dynamoDbClient, "orders");
    }

    @Test
    void findStateFailsWithIllegalStateWhenTheOrderDoesNotExist() {
        // O StateMachineUseCase conta com esta excecao para criar a OS na primeira mensagem.
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        assertThatThrownBy(() -> repository.findState("order-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findStateReadsTheStoredStateFromTheConfiguredTable() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of("orderId", AttributeValue.builder().s("order-1").build(),
                        "state", AttributeValue.builder().s("OS_CRIADA").build()))
                .build());

        assertThat(repository.findState("order-1")).isEqualTo(StateMachineStates.OS_CRIADA);

        ArgumentCaptor<GetItemRequest> request = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(request.capture());
        assertThat(request.getValue().tableName()).isEqualTo("orders");
        assertThat(request.getValue().key().get("orderId").s()).isEqualTo("order-1");
    }

    @Test
    void createStateRefusesToOverwriteAnExistingOrder() {
        repository.createState("order-1", StateMachineStates.CRIAR_OS);

        ArgumentCaptor<PutItemRequest> request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(request.capture());
        assertThat(request.getValue().conditionExpression()).isEqualTo("attribute_not_exists(orderId)");
        assertThat(request.getValue().item().get("state").s()).isEqualTo("CRIAR_OS");
    }
}
