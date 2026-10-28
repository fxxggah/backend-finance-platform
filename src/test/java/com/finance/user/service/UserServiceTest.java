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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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

    @Test
    @DisplayName("Deve retornar os dados do perfil quando o usuário existe e está ativo")
    void shouldReturnProfileWhenUserExistsAndIsActive() {
        User user = new User("google-123", "user@example.com", "User Name", "https://photo.url");
        user.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.get(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("User Name");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.pictureUrl()).isEqualTo("https://photo.url");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando o usuário não existe")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando o usuário existe mas está inativo")
    void shouldThrowExceptionWhenUserIsInactive() {
        User user = new User("google-123", "user@example.com", "User Name", "https://photo.url");
        user.setId(1L);
        user.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.get(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    @DisplayName("Deve apagar todos os dados do usuário na ordem correta (LGPD)")
    void shouldDeleteAllUserDataInCorrectOrder() {
        Long userId = 1L;
        User user = new User("google-123", "user@example.com", "User Name", "https://photo.url");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.delete(userId);

        InOrder inOrder = inOrder(
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

        inOrder.verify(goalContributionRepository).deleteAllByGoalUserId(userId);
        inOrder.verify(goalRepository).deleteAllByUserId(userId);
        inOrder.verify(invoicePaymentRepository).deleteAllByUserId(userId);
        inOrder.verify(transactionRepository).deleteAllByUserId(userId);
        inOrder.verify(transferRepository).deleteAllByUserId(userId);
        inOrder.verify(refreshTokenRepository).deleteAllByUserId(userId);
        inOrder.verify(creditCardRepository).deleteAllByUserId(userId);
        inOrder.verify(accountRepository).deleteAllByUserId(userId);
        inOrder.verify(categoryRepository).deleteAllByUserId(userId);
        inOrder.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar deletar usuário inexistente")
    void shouldThrowExceptionWhenDeletingNonExistentUser() {
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");

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
