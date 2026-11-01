package com.finance.account.service;

import com.finance.account.dto.request.AccountRequest;
import com.finance.account.dto.response.AccountDeleteResponse;
import com.finance.account.dto.response.AccountListResponse;
import com.finance.account.dto.response.AccountResponse;
import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.enums.AccountType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transfer.repository.TransferRepository;
import com.finance.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final AccountType TYPE = AccountType.values()[0];

    private static final String NOT_FOUND = "Conta não encontrada";
    private static final String CONFLICT = "Já existe uma conta com esse nome";
    private static final String LAST_ACTIVE = "Não é possível excluir ou arquivar a última conta ativa";

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private InvoicePaymentRepository invoicePaymentRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AccountService accountService;

    // ---------- helpers ----------

    private Account account(Long id, String name, String initialBalance, boolean archived) {
        Account account = new Account(
                new User("google-1", "joao@example.com", "João", null),
                name, TYPE, new BigDecimal(initialBalance), "#3B82F6", "wallet");
        account.setId(id);
        account.setArchived(archived);
        return account;
    }

    private void stubBalance(Long accountId, String net, String received, String sent, String payments) {
        when(transactionRepository.getNetBalanceByAccountId(accountId)).thenReturn(new BigDecimal(net));
        when(transferRepository.sumAmountReceivedByAccountId(accountId)).thenReturn(new BigDecimal(received));
        when(transferRepository.sumAmountSentByAccountId(accountId)).thenReturn(new BigDecimal(sent));
        when(invoicePaymentRepository.sumPaymentsByAccountId(accountId)).thenReturn(new BigDecimal(payments));
    }

    private void stubZeroBalance(Long accountId) {
        stubBalance(accountId, "0", "0", "0", "0");
    }

    private void stubFindOwned(Account account) {
        when(accountRepository.findByIdAndUserId(account.getId(), USER_ID)).thenReturn(Optional.of(account));
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: deve calcular o saldo de cada conta e somar no total apenas as contas ativas")
    void list_includingArchived_shouldCalculateBalancesAndTotalOnlyActive() {
        Account active = account(1L, "Nubank", "100.00", false);
        Account archived = account(2L, "Antiga", "500.00", true);
        when(accountRepository.findAllByUserId(USER_ID)).thenReturn(List.of(active, archived));
        // 100 + 50 (transações) + 30 (recebido) - 20 (enviado) - 10 (faturas) = 150
        stubBalance(1L, "50.00", "30.00", "20.00", "10.00");
        stubZeroBalance(2L);
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of());

        AccountListResponse response = accountService.list(USER_ID, true);

        assertThat(response.accounts()).hasSize(2);
        assertThat(response.accounts().get(0).balance()).isEqualByComparingTo("150.00");
        assertThat(response.accounts().get(1).balance()).isEqualByComparingTo("500.00");
        assertThat(response.accounts().get(1).archived()).isTrue();
        assertThat(response.totalBalance()).isEqualByComparingTo("150.00");
        assertThat(response.creditCardDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(accountRepository, never()).findAllByUserIdAndArchivedFalse(anyLong());
    }

    @Test
    @DisplayName("list: com includeArchived=false deve buscar apenas contas ativas")
    void list_excludingArchived_shouldQueryOnlyActiveAccounts() {
        Account active = account(1L, "Nubank", "100.00", false);
        when(accountRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of(active));
        stubZeroBalance(1L);
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of());

        AccountListResponse response = accountService.list(USER_ID, false);

        assertThat(response.accounts()).hasSize(1);
        assertThat(response.totalBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).findAllByUserId(anyLong());
    }

    @Test
    @DisplayName("list: deve retornar totais zerados quando o usuário não tem contas nem cartões")
    void list_shouldReturnZeroTotals_whenUserHasNothing() {
        when(accountRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of());

        AccountListResponse response = accountService.list(USER_ID, true);

        assertThat(response.accounts()).isEmpty();
        assertThat(response.totalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.creditCardDebt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("list: deve somar a dívida dos cartões e ignorar cartões com fatura paga a mais")
    void list_shouldSumCreditCardDebt_ignoringOverpaidCards() {
        CreditCard debtCard = mock(CreditCard.class);
        CreditCard overpaidCard = mock(CreditCard.class);
        CreditCard unpaidCard = mock(CreditCard.class);
        when(debtCard.getId()).thenReturn(100L);
        when(overpaidCard.getId()).thenReturn(200L);
        when(unpaidCard.getId()).thenReturn(300L);

        when(accountRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID))
                .thenReturn(List.of(debtCard, overpaidCard, unpaidCard));

        // 500 - 200 = 300
        when(transactionRepository.sumTotalAmountByCreditCardId(100L)).thenReturn(new BigDecimal("500.00"));
        when(invoicePaymentRepository.sumTotalPaymentsByCreditCardId(100L)).thenReturn(new BigDecimal("200.00"));
        // 100 - 150 = -50 (ignorado)
        when(transactionRepository.sumTotalAmountByCreditCardId(200L)).thenReturn(new BigDecimal("100.00"));
        when(invoicePaymentRepository.sumTotalPaymentsByCreditCardId(200L)).thenReturn(new BigDecimal("150.00"));
        // 250 - 0 = 250
        when(transactionRepository.sumTotalAmountByCreditCardId(300L)).thenReturn(new BigDecimal("250.00"));
        when(invoicePaymentRepository.sumTotalPaymentsByCreditCardId(300L)).thenReturn(BigDecimal.ZERO);

        AccountListResponse response = accountService.list(USER_ID, true);

        assertThat(response.creditCardDebt()).isEqualByComparingTo("550.00");
    }

    // ---------- getById ----------

    @Test
    @DisplayName("getById: deve retornar a conta com o saldo calculado")
    void getById_shouldReturnAccountWithBalance() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        stubBalance(ACCOUNT_ID, "25.50", "0", "0", "0");

        AccountResponse response = accountService.getById(USER_ID, ACCOUNT_ID);

        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.name()).isEqualTo("Nubank");
        assertThat(response.type()).isEqualTo(TYPE);
        assertThat(response.balance()).isEqualByComparingTo("125.50");
        assertThat(response.color()).isEqualTo("#3B82F6");
        assertThat(response.icon()).isEqualTo("wallet");
        assertThat(response.archived()).isFalse();
    }

    @Test
    @DisplayName("getById: deve lançar ResourceNotFoundException quando a conta não existe ou é de outro usuário")
    void getById_shouldThrow_whenNotFoundOrNotOwned() {
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(USER_ID, ACCOUNT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verifyNoInteractions(transactionRepository, transferRepository, invoicePaymentRepository);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: deve remover espaços do nome, persistir a conta e retornar o saldo")
    void create_shouldTrimNameAndPersistAccount() {
        AccountRequest request = new AccountRequest("  Nubank  ", TYPE, new BigDecimal("1000.00"), "#8A05BE", "bank");
        User userRef = new User("google-1", "joao@example.com", "João", null);
        when(accountRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nubank")).thenReturn(false);
        when(entityManager.getReference(User.class, USER_ID)).thenReturn(userRef);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(ACCOUNT_ID);
            return saved;
        });
        stubZeroBalance(ACCOUNT_ID);

        AccountResponse response = accountService.create(USER_ID, request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account persisted = captor.getValue();
        assertThat(persisted.getUser()).isSameAs(userRef);
        assertThat(persisted.getName()).isEqualTo("Nubank");
        assertThat(persisted.getType()).isEqualTo(TYPE);
        assertThat(persisted.getInitialBalance()).isEqualByComparingTo("1000.00");
        assertThat(persisted.getColor()).isEqualTo("#8A05BE");
        assertThat(persisted.getIcon()).isEqualTo("bank");
        assertThat(persisted.isArchived()).isFalse();

        assertThat(response.id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.name()).isEqualTo("Nubank");
        assertThat(response.balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("create: deve assumir saldo inicial zero quando não informado")
    void create_shouldDefaultInitialBalanceToZero_whenNull() {
        AccountRequest request = new AccountRequest("Carteira", TYPE, null, null, null);
        when(accountRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Carteira")).thenReturn(false);
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(new User("google-1", "joao@example.com", "João", null));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(ACCOUNT_ID);
            return saved;
        });
        stubZeroBalance(ACCOUNT_ID);

        AccountResponse response = accountService.create(USER_ID, request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("create: deve lançar ConflictException quando já existe conta com o mesmo nome")
    void create_shouldThrowConflict_whenNameAlreadyExists() {
        AccountRequest request = new AccountRequest(" Nubank ", TYPE, BigDecimal.TEN, null, null);
        when(accountRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nubank")).thenReturn(true);

        assertThatThrownBy(() -> accountService.create(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(entityManager);
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: deve atualizar todos os campos da conta e retornar o saldo")
    void update_shouldUpdateAllFields() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        AccountType newType = AccountType.values()[AccountType.values().length - 1];
        AccountRequest request = new AccountRequest("  Novo Nome ", newType, new BigDecimal("300.00"), "#000000", "bank");
        stubFindOwned(account);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Novo Nome", ACCOUNT_ID))
                .thenReturn(false);
        when(accountRepository.save(account)).thenReturn(account);
        stubBalance(ACCOUNT_ID, "50.00", "0", "0", "0");

        AccountResponse response = accountService.update(USER_ID, ACCOUNT_ID, request);

        assertThat(account.getName()).isEqualTo("Novo Nome");
        assertThat(account.getType()).isEqualTo(newType);
        assertThat(account.getInitialBalance()).isEqualByComparingTo("300.00");
        assertThat(account.getColor()).isEqualTo("#000000");
        assertThat(account.getIcon()).isEqualTo("bank");

        assertThat(response.name()).isEqualTo("Novo Nome");
        assertThat(response.balance()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("update: deve assumir saldo inicial zero quando não informado")
    void update_shouldDefaultInitialBalanceToZero_whenNull() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        AccountRequest request = new AccountRequest("Nubank", TYPE, null, null, null);
        stubFindOwned(account);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Nubank", ACCOUNT_ID))
                .thenReturn(false);
        when(accountRepository.save(account)).thenReturn(account);
        stubZeroBalance(ACCOUNT_ID);

        AccountResponse response = accountService.update(USER_ID, ACCOUNT_ID, request);

        assertThat(account.getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getColor()).isNull();
        assertThat(account.getIcon()).isNull();
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando a conta não existe ou é de outro usuário")
    void update_shouldThrowNotFound_whenNotOwned() {
        AccountRequest request = new AccountRequest("Nubank", TYPE, null, null, null);
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(USER_ID, ACCOUNT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(accountRepository, never())
                .existsByUserIdAndNameIgnoreCaseAndIdNot(anyLong(), anyString(), anyLong());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("update: deve lançar ConflictException quando outra conta já usa o nome e não alterar a conta")
    void update_shouldThrowConflict_whenAnotherAccountHasTheName() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        AccountRequest request = new AccountRequest("Inter", TYPE, new BigDecimal("999.00"), null, null);
        stubFindOwned(account);
        when(accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Inter", ACCOUNT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.update(USER_ID, ACCOUNT_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        assertThat(account.getName()).isEqualTo("Nubank");
        assertThat(account.getInitialBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve bloquear a exclusão da última conta ativa")
    void delete_shouldThrowBusinessRule_whenLastActiveAccount() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        when(accountRepository.countByUserIdAndArchivedFalse(USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> accountService.delete(USER_ID, ACCOUNT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(LAST_ACTIVE);

        assertThat(account.isArchived()).isFalse();
        verify(accountRepository, never()).save(any(Account.class));
        verify(accountRepository, never()).delete(any(Account.class));
        verifyNoInteractions(transactionRepository, transferRepository, invoicePaymentRepository);
    }

    @Test
    @DisplayName("delete: deve arquivar a conta quando ela possui transações")
    void delete_shouldArchive_whenAccountHasTransactions() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        when(accountRepository.countByUserIdAndArchivedFalse(USER_ID)).thenReturn(2L);
        when(transactionRepository.existsByAccountIdAndDeletedFalse(ACCOUNT_ID)).thenReturn(true);

        AccountDeleteResponse response = accountService.delete(USER_ID, ACCOUNT_ID);

        assertThat(response.archived()).isTrue();
        assertThat(account.isArchived()).isTrue();
        verify(accountRepository).save(account);
        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    @DisplayName("delete: deve arquivar a conta quando ela possui transferências")
    void delete_shouldArchive_whenAccountHasTransfers() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        when(accountRepository.countByUserIdAndArchivedFalse(USER_ID)).thenReturn(2L);
        when(transferRepository.existsByFromAccountIdOrToAccountId(ACCOUNT_ID, ACCOUNT_ID)).thenReturn(true);

        AccountDeleteResponse response = accountService.delete(USER_ID, ACCOUNT_ID);

        assertThat(response.archived()).isTrue();
        assertThat(account.isArchived()).isTrue();
        verify(accountRepository).save(account);
        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    @DisplayName("delete: deve arquivar a conta quando ela possui pagamentos de fatura")
    void delete_shouldArchive_whenAccountHasInvoicePayments() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        when(accountRepository.countByUserIdAndArchivedFalse(USER_ID)).thenReturn(2L);
        when(invoicePaymentRepository.existsByAccountId(ACCOUNT_ID)).thenReturn(true);

        AccountDeleteResponse response = accountService.delete(USER_ID, ACCOUNT_ID);

        assertThat(response.archived()).isTrue();
        assertThat(account.isArchived()).isTrue();
        verify(accountRepository).save(account);
        verify(accountRepository, never()).delete(any(Account.class));
    }

    @Test
    @DisplayName("delete: deve excluir fisicamente a conta sem movimentações")
    void delete_shouldHardDelete_whenAccountHasNoMovements() {
        Account account = account(ACCOUNT_ID, "Nubank", "100.00", false);
        stubFindOwned(account);
        when(accountRepository.countByUserIdAndArchivedFalse(USER_ID)).thenReturn(2L);

        AccountDeleteResponse response = accountService.delete(USER_ID, ACCOUNT_ID);

        assertThat(response.archived()).isFalse();
        assertThat(account.isArchived()).isFalse();
        verify(accountRepository).delete(account);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("delete: conta já arquivada pode ser excluída sem checar a regra da última conta ativa")
    void delete_shouldSkipLastActiveCheck_whenAccountIsAlreadyArchived() {
        Account account = account(ACCOUNT_ID, "Antiga", "0.00", true);
        stubFindOwned(account);

        AccountDeleteResponse response = accountService.delete(USER_ID, ACCOUNT_ID);

        assertThat(response.archived()).isFalse();
        verify(accountRepository, never()).countByUserIdAndArchivedFalse(anyLong());
        verify(accountRepository).delete(account);
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException quando a conta não existe ou é de outro usuário")
    void delete_shouldThrowNotFound_whenNotOwned() {
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.delete(USER_ID, ACCOUNT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(accountRepository, never()).delete(any(Account.class));
        verify(accountRepository, never()).save(any(Account.class));
    }
}