package com.os.os_orchestrator;

import com.os.os_orchestrator.adapter.in.sqs.StateMachineListener;
import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
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

    @Test void senderPublishesOrderAndActionHeaderToActionQueue() {
        SqsTemplate sqs = mock(SqsTemplate.class);
        StateMachineEvent event = new StateMachineEvent("o-2", StateMachineActions.GERAR_ORCAMENTO, "corr-2", new BigDecimal("50.30"));
        new ActionSenderServiceImpl(sqs).sendAction("o-2", event);
        verify(sqs).send("os-payment-queue", event);
    }

    @Test void repositoryCreatesReadsAndUpdatesState() {
        DynamoDbClient db = mock(DynamoDbClient.class);
        OrderStateRepository repository = new OrderStateRepository(db, "orders");
        repository.createState("o", StateMachineStates.CRIAR_OS);
        verify(db).putItem(argThat((PutItemRequest r) -> r.tableName().equals("orders") &&
                "o".equals(r.item().get("orderId").s()) && "attribute_not_exists(orderId)".equals(r.conditionExpression())));
        when(db.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(Map.of("state", AttributeValue.builder().s("OS_CRIADA").build())).build());
        assertEquals(StateMachineStates.OS_CRIADA, repository.findState("o"));
        when(db.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertThrows(IllegalStateException.class, () -> repository.findState("missing"));
        repository.updateState("o", StateMachineStates.ERRO);
        verify(db).putItem(argThat((PutItemRequest r) -> r.item().get("state").s().equals("ERRO") && r.conditionExpression() == null));
    }

    @Test void errorHandlerAlwaysClearsMdcAndSwallowsStateUpdateFailure() {
        OrderStateRepository repository = mock(OrderStateRepository.class);
        GlobalErrorHandler handler = new GlobalErrorHandler(repository);
        StateMachineEvent event = new StateMachineEvent("o", StateMachineActions.CRIAR_OS);
        MDC.put("order", "o");
        handler.handle(event, new RuntimeException("processing"));
        verify(repository).updateState("o", StateMachineStates.ERRO);
        assertNull(MDC.get("order"));
        doThrow(new RuntimeException("database")).when(repository).updateState("o", StateMachineStates.ERRO);
        MDC.put("order", "o");
        assertDoesNotThrow(() -> handler.handle(event, new RuntimeException("processing")));
        assertNull(MDC.get("order"));
    }
}
