package com.os.os_orchestrator;

import com.os.os_orchestrator.adapter.in.sqs.StateMachineListener;
import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.adapter.out.database.OrderNotFoundException;
import com.os.os_orchestrator.adapter.out.database.DynamoDbConfig;
import com.os.os_orchestrator.adapter.out.sqs.ActionSenderServiceImpl;
import com.os.os_orchestrator.application.GlobalErrorHandler;
import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.*;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.math.BigDecimal;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdapterAndHandlerTest {
    @Test void listenerReceivesCompleteJsonEventAndDelegates() {
        StateMachineUseCase useCase = mock(StateMachineUseCase.class);
        StateMachineEvent event = new StateMachineEvent("order-123", StateMachineActions.CRIAR_OS,
                "123", new BigDecimal("50.30"));
        new StateMachineListener(useCase).processOrder(event);
        verify(useCase).process(event);
        assertNull(MDC.get("order"));
        assertNull(MDC.get("correlationId"));
    }

    @Test void listenerRejectsMissingRequiredMetadata() {
        StateMachineUseCase useCase = mock(StateMachineUseCase.class);
        StateMachineListener listener = new StateMachineListener(useCase);
        assertThrows(IllegalArgumentException.class, () -> listener.processOrder(
                new StateMachineEvent("order-123", StateMachineActions.CRIAR_OS, null, new BigDecimal("50.30"))));
        assertThrows(IllegalArgumentException.class, () -> listener.processOrder(
                new StateMachineEvent("order-123", StateMachineActions.CRIAR_OS, "corr", null)));
        verifyNoInteractions(useCase);
    }

    @Test void senderPublishesOrderAndActionHeaderToActionQueue() {
        SqsTemplate sqs = mock(SqsTemplate.class);
        StateMachineEvent event = new StateMachineEvent("o-2", StateMachineActions.GERAR_ORCAMENTO, "corr-2", new BigDecimal("50.30"));
        new ActionSenderServiceImpl(sqs).sendAction("o-2", event);
        verify(sqs).send("os-payment-queue", event);
    }

    @Test void repositoryCreatesReadsAndUpdatesState() {
        DynamoDbClient db = mock(DynamoDbClient.class);
        OrderStateRepository repository = new OrderStateRepository(db, "orders");
        StateMachineEvent event = new StateMachineEvent("o", StateMachineActions.CRIAR_OS, "corr", new BigDecimal("50.30"));
        repository.createState(event, StateMachineStates.CRIAR_OS);
        verify(db).putItem(argThat((PutItemRequest r) -> r.tableName().equals("orders") &&
                "o".equals(r.item().get("orderId").s()) && "corr".equals(r.item().get("correlationId").s()) &&
                "50.30".equals(r.item().get("amount").s()) && "attribute_not_exists(orderId)".equals(r.conditionExpression())));
        when(db.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(Map.of(
                "state", AttributeValue.builder().s("OS_CRIADA").build(),
                "correlationId", AttributeValue.builder().s("corr").build(),
                "amount", AttributeValue.builder().s("50.30").build())).build());
        assertEquals(new OrderStateRepository.OrderState(StateMachineStates.OS_CRIADA, "corr", new BigDecimal("50.30")), repository.findState("o"));
        when(db.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertThrows(OrderNotFoundException.class, () -> repository.findState("missing"));
        when(db.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(Map.of("state", AttributeValue.builder().s("OS_CRIADA").build())).build());
        assertThrows(IllegalStateException.class, () -> repository.findState("legacy"));
        repository.updateState(event, StateMachineStates.ERRO);
        verify(db).putItem(argThat((PutItemRequest r) -> r.item().get("state").s().equals("ERRO") &&
                r.item().get("correlationId").s().equals("corr") && r.item().get("amount").s().equals("50.30") && r.conditionExpression() == null));
    }

    @Test void dynamoConfigurationUsesRegionalDefaultAndOptionalEndpoint() {
        try (DynamoDbClient defaultClient = new DynamoDbConfig("", "us-east-1").dynamoDbClient();
             DynamoDbClient localClient = new DynamoDbConfig("http://localhost:4566", "us-west-2").dynamoDbClient()) {
            assertTrue(defaultClient.serviceClientConfiguration().endpointOverride().isEmpty());
            assertEquals(URI.create("http://localhost:4566"),
                    localClient.serviceClientConfiguration().endpointOverride().orElseThrow());
        }
    }

    @Test void errorHandlerAlwaysClearsMdcAndSwallowsStateUpdateFailure() {
        OrderStateRepository repository = mock(OrderStateRepository.class);
        GlobalErrorHandler handler = new GlobalErrorHandler(repository);
        StateMachineEvent event = new StateMachineEvent("o", StateMachineActions.CRIAR_OS, "corr", new BigDecimal("50.30"));
        MDC.put("order", "o");
        handler.handle(event, new RuntimeException("processing"));
        verify(repository).updateState(event, StateMachineStates.ERRO);
        assertNull(MDC.get("order"));
        doThrow(new RuntimeException("database")).when(repository).updateState(event, StateMachineStates.ERRO);
        MDC.put("order", "o");
        assertDoesNotThrow(() -> handler.handle(event, new RuntimeException("processing")));
        assertNull(MDC.get("order"));
    }
}
