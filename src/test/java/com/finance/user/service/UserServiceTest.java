package com.finance.user.service;

import com.finance.account.repository.AccountRepository;
import com.finance.auth.repository.RefreshTokenRepository;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.goal.repository.GoalContributionRepository;
import com.finance.goal.repository.GoalRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transfer.repository.TransferRepository;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String NOT_FOUND_MESSAGE = "Usuário não encontrado";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private GoalContributionRepository goalContributionRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png");
        user.setId(USER_ID);
    }

    // ---------- get ----------

    @Test
    @DisplayName("get: deve retornar o UserResponse quando o usuário existe e está ativo")
    void get_shouldReturnResponse_whenUserIsActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = userService.get(USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.name()).isEqualTo("João Silva");
        assertThat(response.email()).isEqualTo("joao@example.com");
        assertThat(response.pictureUrl()).isEqualTo("https://img.example.com/joao.png");
    }

    @Test
    @DisplayName("get: deve lançar ResourceNotFoundException quando o usuário não existe")
    void get_shouldThrow_whenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);
    }

    @Test
    @DisplayName("get: deve lançar ResourceNotFoundException quando o usuário está inativo")
    void get_shouldThrow_whenUserIsInactive() {
        user.setActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.get(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);
    }

    // ---------- getActiveUser ----------

    @Test
    @DisplayName("getActiveUser: deve retornar a entidade quando o usuário existe e está ativo")
    void getActiveUser_shouldReturnUser_whenUserIsActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        User result = userService.getActiveUser(USER_ID);

        assertThat(result).isSameAs(user);
    }

    @Test
    @DisplayName("getActiveUser: deve lançar ResourceNotFoundException quando o usuário não existe")
    void getActiveUser_shouldThrow_whenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getActiveUser(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);
    }

    @Test
    @DisplayName("getActiveUser: deve lançar ResourceNotFoundException quando o usuário está inativo")
    void getActiveUser_shouldThrow_whenUserIsInactive() {
        user.setActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getActiveUser(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve remover os dados dependentes na ordem correta e por fim o usuário")
    void delete_shouldDeleteDependenciesInOrderAndThenUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.delete(USER_ID);

        InOrder order = inOrder(
                goalContributionRepository,
                goalRepository,
                invoicePaymentRepository,
                transactionRepository,
                transferRepository,
                refreshTokenRepository,
                creditCardRepository,
                accountRepository,
                categoryRepository,
                userRepository);

        order.verify(goalContributionRepository).deleteAllByGoalUserId(USER_ID);
        order.verify(goalRepository).deleteAllByUserId(USER_ID);
        order.verify(invoicePaymentRepository).deleteAllByUserId(USER_ID);
        order.verify(transactionRepository).deleteAllByUserId(USER_ID);
        order.verify(transferRepository).deleteAllByUserId(USER_ID);
        order.verify(refreshTokenRepository).deleteAllByUserId(USER_ID);
        order.verify(creditCardRepository).deleteAllByUserId(USER_ID);
        order.verify(accountRepository).deleteAllByUserId(USER_ID);
        order.verify(categoryRepository).deleteAllByUserId(USER_ID);
        order.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("delete: deve permitir excluir usuário inativo")
    void delete_shouldDeleteUser_evenWhenInactive() {
        user.setActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.delete(USER_ID);

        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException e não apagar nada quando o usuário não existe")
    void delete_shouldThrowAndDeleteNothing_whenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        verify(userRepository, never()).delete(user);
        verifyNoInteractions(
                goalContributionRepository,
                goalRepository,
                invoicePaymentRepository,
                transactionRepository,
                transferRepository,
                refreshTokenRepository,
                creditCardRepository,
                accountRepository,
                categoryRepository);
    }
}