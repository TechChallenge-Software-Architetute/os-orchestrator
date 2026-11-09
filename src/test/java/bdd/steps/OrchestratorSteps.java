package bdd.steps;

import com.os.os_orchestrator.adapter.out.database.OrderStateRepository;
import com.os.os_orchestrator.adapter.out.database.OrderNotFoundException;
import com.os.os_orchestrator.adapter.out.sqs.ActionSenderService;
import com.os.os_orchestrator.application.StateMachineUseCase;
import com.os.os_orchestrator.domain.StateMachineActions;
import com.os.os_orchestrator.domain.StateMachineEvent;
import com.os.os_orchestrator.domain.StateMachineStates;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class OrchestratorSteps {

    private static final String ORDER_ID = "order-bdd-123";
    private static final String CORRELATION_ID = "corr-bdd-123";
    private static final BigDecimal AMOUNT = new BigDecimal("50.30");

    private OrderStateRepository orderStateRepository;
    private ActionSenderService actionSenderService;
    private StateMachineUseCase stateMachineUseCase;

    private StateMachineStates currentState;
    private boolean orderExists;
    private boolean failureFlow;

    @Before
    public void configurarCenario() {
        orderStateRepository = mock(OrderStateRepository.class);
        actionSenderService = mock(ActionSenderService.class);
        stateMachineUseCase = new StateMachineUseCase();

        injectDependencies();
        configureRepositoryState();

        currentState = null;
        orderExists = false;
        failureFlow = false;
    }

    @After
    public void limparCenario() {
        if (orderStateRepository != null) {
            reset(orderStateRepository);
        }

        if (actionSenderService != null) {
            reset(actionSenderService);
        }

        stateMachineUseCase = null;
        orderStateRepository = null;
        actionSenderService = null;

        currentState = null;
        orderExists = false;
        failureFlow = false;
    }

    @Dado("um pedido de troca de pneu com cliente válido")
    public void pedidoComClienteValido() {
        failureFlow = false;
    }

    @Dado("um pedido de troca de pneu com cliente sem saldo em conta")
    public void pedidoComClienteSemSaldo() {
        failureFlow = true;
    }

    @Dado("um pedido de troca de pneu com erro ao gerar o orçamento")
    public void pedidoComErroAoGerarOrcamento() {
        failureFlow = true;
    }

    @Quando("recebermos um pedido de criação de OS")
    public void receberPedidoDeCriacaoDeOs() {
        process(StateMachineActions.CRIAR_OS);
        process(StateMachineActions.GERAR_ORCAMENTO);

        if (failureFlow) {
            process(StateMachineActions.NEGAR_SERVICO);
            process(StateMachineActions.NEGAR_ESTOQUE);
            return;
        }

        process(StateMachineActions.EXECUTAR_SERVICO);
        process(StateMachineActions.ATUALIZAR_ESTOQUE);
    }

    @Então("devemos realizar o serviço do cliente")
    public void devemosRealizarServicoDoCliente() {
        assertEquals(
                StateMachineStates.ATUALIZAR_ESTOQUE,
                currentState
        );

        verify(orderStateRepository).createState(
                event(StateMachineActions.CRIAR_OS),
                StateMachineStates.CRIAR_OS
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.CRIAR_OS),
                StateMachineStates.OS_CRIADA
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.GERAR_ORCAMENTO),
                StateMachineStates.ORCAMENTO_GERADO
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.EXECUTAR_SERVICO),
                StateMachineStates.EXECUTAR_SERVICO
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.ATUALIZAR_ESTOQUE),
                StateMachineStates.ATUALIZAR_ESTOQUE
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.GERAR_ORCAMENTO)
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.EXECUTAR_SERVICO)
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.ATUALIZAR_ESTOQUE)
        );

        verifyNoMoreInteractions(actionSenderService);
    }

    @Então("não devemos realizar o serviço do cliente")
    public void naoDevemosRealizarServicoDoCliente() {
        assertEquals(
                StateMachineStates.NEGAR_ESTOQUE,
                currentState
        );

        verify(orderStateRepository).createState(
                event(StateMachineActions.CRIAR_OS),
                StateMachineStates.CRIAR_OS
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.CRIAR_OS),
                StateMachineStates.OS_CRIADA
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.GERAR_ORCAMENTO),
                StateMachineStates.ORCAMENTO_GERADO
        );

        verify(orderStateRepository).updateState(
                event(StateMachineActions.NEGAR_SERVICO),
                StateMachineStates.NEGAR_SERVICO
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.GERAR_ORCAMENTO)
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.EXECUTAR_SERVICO)
        );

        verify(actionSenderService).sendAction(
                ORDER_ID,
                event(StateMachineActions.NEGAR_ESTOQUE)
        );

        verifyNoMoreInteractions(actionSenderService);
    }

    private void process(StateMachineActions action) {
        stateMachineUseCase.process(
                event(action)
        );
    }

    private StateMachineEvent event(StateMachineActions action) {
        return new StateMachineEvent(ORDER_ID, action, CORRELATION_ID, AMOUNT);
    }

    private void configureRepositoryState() {
        when(orderStateRepository.findState(ORDER_ID))
                .thenAnswer(invocation -> {
                    if (!orderExists) {
                        throw new OrderNotFoundException(ORDER_ID);
                    }

                    return new OrderStateRepository.OrderState(currentState, CORRELATION_ID, AMOUNT);
                });

        doAnswer(invocation -> {
            orderExists = true;
            currentState = invocation.getArgument(1);
            return null;
        }).when(orderStateRepository)
                .createState(any(StateMachineEvent.class), any(StateMachineStates.class));

        doAnswer(invocation -> {
            currentState = invocation.getArgument(1);
            return null;
        }).when(orderStateRepository)
                .updateState(any(StateMachineEvent.class), any(StateMachineStates.class));
    }

    private void injectDependencies() {
        try {
            var repositoryField =
                    StateMachineUseCase.class
                            .getDeclaredField("orderStateRepository");

            repositoryField.setAccessible(true);
            repositoryField.set(
                    stateMachineUseCase,
                    orderStateRepository
            );

            var senderField =
                    StateMachineUseCase.class
                            .getDeclaredField("actionSenderService");

            senderField.setAccessible(true);
            senderField.set(
                    stateMachineUseCase,
                    actionSenderService
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Não foi possível configurar as dependências do caso de uso",
                    exception
            );
        }
    }
}
