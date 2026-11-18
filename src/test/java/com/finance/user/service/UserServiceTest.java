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

    @Mock private com.finance.billing.service.ProAccessService proAccess;
    @Mock private com.finance.billing.service.BillingDeletionService deletion;
    @Mock private java.time.Clock clock;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(java.time.Instant.parse("2026-11-16T12:00:00Z"));
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

    @Test void deleteDelegatesToCancellationBeforePhysicalDeletion() {
        userService.delete(USER_ID);
        verify(deletion).delete(USER_ID);
        verifyNoInteractions(userRepository);
    }
}
