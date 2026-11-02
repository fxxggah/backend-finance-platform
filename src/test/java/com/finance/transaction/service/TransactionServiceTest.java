package com.finance.transaction.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.dto.PageResponse;
import com.finance.common.enums.AccountType;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.dto.request.TransactionRequest;
import com.finance.transaction.dto.response.TransactionResponse;
import com.finance.transaction.entity.Transaction;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TX_ID = 100L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long ACCOUNT_ID = 5L;
    private static final Long CARD_ID = 7L;
    private static final String GROUP_ID = "group-1";

    private static final TransactionType EXPENSE = TransactionType.EXPENSE;
    private static final TransactionType INCOME = TransactionType.INCOME;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final String TX_NOT_FOUND = "Transação não encontrada";
    private static final String CATEGORY_NOT_FOUND = "Categoria não encontrada";
    private static final String ACCOUNT_NOT_FOUND = "Conta não encontrada";
    private static final String CARD_NOT_FOUND = "Cartão de crédito não encontrado";

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreditCardRepository creditCardRepository;

    @InjectMocks
    private TransactionService transactionService;

    // ---------- helpers ----------

    private User user() {
        User user = new User("google-1", "joao@example.com", "João", null);
        user.setId(USER_ID);
        return user;
    }

    private Category category(Long id, TransactionType type, boolean deleted) {
        Category category = new Category(user(), "Mercado", type, "#3B82F6", "tag");
        category.setId(id);
        category.setDeleted(deleted);
        return category;
    }

    private Account account(Long id, boolean archived) {
        Account account = new Account(user(), "Conta corrente", AccountType.BANK, BigDecimal.ZERO, "#1E90FF", "bank-icon");
        account.setId(id);
        account.setArchived(archived);
        return account;
    }

    private CreditCard card(Long id, int closingDay, boolean archived) {
        CreditCard card = new CreditCard(user(), "Nubank", new BigDecimal("5000.00"), closingDay, 15, "#8A05BE", "credit-card-icon");
        card.setId(id);
        card.setArchived(archived);
        return card;
    }

    private Transaction transaction(Long id, TransactionType type, Category category, Account account) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setType(type);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setDate(DATE);
        tx.setDescription("Descrição original");
        tx.setNotes("Obs original");
        tx.setCategory(category);
        tx.setAccount(account);
        return tx;
    }

    private Transaction installmentTx(Long id, Category category, boolean deleted) {
        Transaction tx = transaction(id, EXPENSE, category, null);
        tx.setCreditCard(card(CARD_ID, 15, false));
        tx.setInstallmentGroupId(GROUP_ID);
        tx.setInstallmentNumber(1);
        tx.setInstallmentTotal(3);
        tx.setDeleted(deleted);
        return tx;
    }

    private TransactionRequest request(TransactionType type, String amount, LocalDate date,
                                       Long accountId, Long creditCardId, Integer installments) {
        return new TransactionRequest(type, amount == null ? null : new BigDecimal(amount),
                CATEGORY_ID, date, "Descrição", "Obs", accountId, creditCardId, installments);
    }

    private TransactionRequest accountRequest(TransactionType type) {
        return request(type, "99.90", DATE, ACCOUNT_ID, null, null);
    }

    private TransactionRequest cardRequest(LocalDate date) {
        return request(EXPENSE, "99.90", date, null, CARD_ID, null);
    }

    private static TransactionFilter noFilter() {
        return new TransactionFilter(null, null, null, null, null, null, null);
    }

    private void stubSaveAssigningId() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(TX_ID);
            return saved;
        });
    }

    private void stubFindOwned(Transaction tx) {
        when(transactionRepository.findByIdAndUserIdAndDeletedFalse(tx.getId(), USER_ID)).thenReturn(Optional.of(tx));
    }

    private void stubPage(Transaction... txs) {
        when(transactionRepository.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(txs), invocation.getArgument(1), txs.length));
    }

    private Pageable captureListPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(ArgumentMatchers.<Specification<Transaction>>any(), captor.capture());
        return captor.getValue();
    }

    private Transaction captureSavedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: deve retornar a página de transações convertidas para response")
    void list_shouldReturnMappedPage() {
        Transaction tx = transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false));
        stubPage(tx);

        PageResponse<TransactionResponse> response = transactionService.list(USER_ID, noFilter(), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        TransactionResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo(TX_ID);
        assertThat(item.type()).isEqualTo(EXPENSE);
        assertThat(item.amount()).isEqualByComparingTo("50.00");
        assertThat(item.category().id()).isEqualTo(CATEGORY_ID);
        assertThat(item.account().id()).isEqualTo(ACCOUNT_ID);
        assertThat(item.creditCard()).isNull();
        assertThat(item.installment()).isNull();
    }

    @Test
    @DisplayName("list: deve lançar BusinessRuleException quando a data inicial é posterior à final")
    void list_shouldThrow_whenFromIsAfterTo() {
        TransactionFilter filter = new TransactionFilter(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null, null, null);

        assertThatThrownBy(() -> transactionService.list(USER_ID, filter, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("list: deve aceitar período com data inicial igual à final")
    void list_shouldAccept_whenFromEqualsTo() {
        stubPage();
        TransactionFilter filter = new TransactionFilter(DATE, DATE, null, null, null, null, null);

        PageResponse<TransactionResponse> response = transactionService.list(USER_ID, filter, PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
    }

    @Test
    @DisplayName("list: deve limitar o tamanho da página a 100")
    void list_shouldCapPageSizeAtMax() {
        stubPage();

        transactionService.list(USER_ID, noFilter(), PageRequest.of(2, 500));

        Pageable used = captureListPageable();
        assertThat(used.getPageSize()).isEqualTo(100);
        assertThat(used.getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("list: deve manter o tamanho da página quando abaixo do limite")
    void list_shouldKeepPageSize_whenBelowMax() {
        stubPage();

        transactionService.list(USER_ID, noFilter(), PageRequest.of(0, 15));

        assertThat(captureListPageable().getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("list: deve usar data decrescente e id como desempate quando não há ordenação")
    void list_shouldUseDefaultSort_whenUnsorted() {
        stubPage();

        transactionService.list(USER_ID, noFilter(), PageRequest.of(0, 20));

        assertThat(captureListPageable().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("list: deve manter campos de ordenação permitidos e acrescentar o id como desempate")
    void list_shouldKeepAllowedSortAndAppendIdTieBreaker() {
        stubPage();

        transactionService.list(USER_ID, noFilter(), PageRequest.of(0, 20, Sort.by(Sort.Order.asc("amount"))));

        assertThat(captureListPageable().getSort())
                .isEqualTo(Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("list: deve descartar campos de ordenação não permitidos")
    void list_shouldDiscardDisallowedSortFields() {
        stubPage();

        transactionService.list(USER_ID, noFilter(),
                PageRequest.of(0, 20, Sort.by(Sort.Order.asc("user.password"), Sort.Order.asc("description"))));

        assertThat(captureListPageable().getSort())
                .isEqualTo(Sort.by(Sort.Order.asc("description"), Sort.Order.desc("id")));
    }

    @Test
    @DisplayName("list: deve usar a ordenação padrão quando todos os campos são não permitidos")
    void list_shouldUseDefaultSort_whenAllSortFieldsAreDisallowed() {
        stubPage();

        transactionService.list(USER_ID, noFilter(), PageRequest.of(0, 20, Sort.by("deleted")));

        assertThat(captureListPageable().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
    }

    // ---------- findById ----------

    @Test
    @DisplayName("findById: deve retornar a transação do usuário")
    void findById_shouldReturnTransaction() {
        stubFindOwned(transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false)));

        TransactionResponse response = transactionService.findById(USER_ID, TX_ID);

        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.description()).isEqualTo("Descrição original");
        assertThat(response.notes()).isEqualTo("Obs original");
        assertThat(response.date()).isEqualTo(DATE);
    }

    @Test
    @DisplayName("findById: deve mapear dados de cartão, fatura e parcelamento")
    void findById_shouldMapCardInvoiceAndInstallment() {
        Transaction tx = installmentTx(TX_ID, category(CATEGORY_ID, EXPENSE, false), false);
        tx.setInvoiceReference(LocalDate.of(2026, 4, 1));
        stubFindOwned(tx);

        TransactionResponse response = transactionService.findById(USER_ID, TX_ID);

        assertThat(response.account()).isNull();
        assertThat(response.creditCard().id()).isEqualTo(CARD_ID);
        assertThat(response.invoiceReference()).isEqualTo("2026-04");
        assertThat(response.installment().groupId()).isEqualTo(GROUP_ID);
        assertThat(response.installment().number()).isEqualTo(1);
        assertThat(response.installment().total()).isEqualTo(3);
    }

    @Test
    @DisplayName("findById: deve lançar ResourceNotFoundException quando não existe, está excluída ou é de outro usuário")
    void findById_shouldThrowNotFound() {
        when(transactionRepository.findByIdAndUserIdAndDeletedFalse(TX_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findById(USER_ID, TX_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TX_NOT_FOUND);
    }

    // ---------- create (conta) ----------

    @Test
    @DisplayName("create: deve persistir despesa em conta vinculada ao usuário, categoria e conta")
    void create_shouldPersistExpenseOnAccount() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        Account account = account(ACCOUNT_ID, false);
        User userRef = user();
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(userRef);
        stubSaveAssigningId();

        TransactionResponse response = transactionService.create(USER_ID, accountRequest(EXPENSE));

        Transaction persisted = captureSavedTransaction();
        assertThat(persisted.getUser()).isSameAs(userRef);
        assertThat(persisted.getType()).isEqualTo(EXPENSE);
        assertThat(persisted.getAmount()).isEqualByComparingTo("99.90");
        assertThat(persisted.getDate()).isEqualTo(DATE);
        assertThat(persisted.getDescription()).isEqualTo("Descrição");
        assertThat(persisted.getNotes()).isEqualTo("Obs");
        assertThat(persisted.getCategory()).isSameAs(category);
        assertThat(persisted.getAccount()).isSameAs(account);
        assertThat(persisted.getCreditCard()).isNull();
        assertThat(persisted.getInvoiceReference()).isNull();
        assertThat(persisted.isDeleted()).isFalse();

        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.type()).isEqualTo(EXPENSE);
        assertThat(response.account().id()).isEqualTo(ACCOUNT_ID);
        assertThat(response.creditCard()).isNull();
        assertThat(response.invoiceReference()).isNull();
        verifyNoInteractions(creditCardRepository);
    }

    @Test
    @DisplayName("create: deve persistir receita em conta")
    void create_shouldPersistIncomeOnAccount() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, INCOME, false)));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account(ACCOUNT_ID, false)));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        stubSaveAssigningId();

        TransactionResponse response = transactionService.create(USER_ID, accountRequest(INCOME));

        assertThat(response.type()).isEqualTo(INCOME);
    }

    @Test
    @DisplayName("create: deve aceitar data exatamente 1 ano à frente")
    void create_shouldAcceptDate_whenExactlyOneYearAhead() {
        LocalDate limit = LocalDate.now(ZONE).plusYears(1);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account(ACCOUNT_ID, false)));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        stubSaveAssigningId();

        TransactionResponse response = transactionService.create(USER_ID,
                request(EXPENSE, "10.00", limit, ACCOUNT_ID, null, null));

        assertThat(response.date()).isEqualTo(limit);
    }

    // ---------- create (cartão) ----------

    @Test
    @DisplayName("create: despesa no cartão antes ou no dia de fechamento deve entrar na fatura do mês")
    void create_shouldUseSameMonthInvoice_whenPurchaseIsOnOrBeforeClosingDay() {
        stubCardCreation(card(CARD_ID, 15, false));

        TransactionResponse response = transactionService.create(USER_ID, cardRequest(LocalDate.of(2026, 3, 15)));

        assertThat(captureSavedTransaction().getInvoiceReference()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(response.invoiceReference()).isEqualTo("2026-03");
        assertThat(response.creditCard().id()).isEqualTo(CARD_ID);
        assertThat(response.account()).isNull();
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("create: despesa no cartão depois do fechamento deve entrar na fatura do mês seguinte")
    void create_shouldUseNextMonthInvoice_whenPurchaseIsAfterClosingDay() {
        stubCardCreation(card(CARD_ID, 15, false));

        TransactionResponse response = transactionService.create(USER_ID, cardRequest(LocalDate.of(2026, 3, 16)));

        assertThat(captureSavedTransaction().getInvoiceReference()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(response.invoiceReference()).isEqualTo("2026-04");
    }

    @Test
    @DisplayName("create: despesa no cartão após o fechamento em dezembro deve virar fatura de janeiro do ano seguinte")
    void create_shouldRollInvoiceToNextYear_whenPurchaseIsAfterClosingInDecember() {
        stubCardCreation(card(CARD_ID, 10, false));

        transactionService.create(USER_ID, cardRequest(LocalDate.of(2025, 12, 20)));

        assertThat(captureSavedTransaction().getInvoiceReference()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    private void stubCardCreation(CreditCard card) {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(card));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        stubSaveAssigningId();
    }

    // ---------- create (validações) ----------

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando não informa conta nem cartão")
    void create_shouldThrow_whenNeitherAccountNorCard() {
        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(EXPENSE, "10.00", DATE, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe accountId ou creditCardId (exatamente um dos dois)");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando informa conta e cartão ao mesmo tempo")
    void create_shouldThrow_whenBothAccountAndCard() {
        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(EXPENSE, "10.00", DATE, ACCOUNT_ID, CARD_ID, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe accountId ou creditCardId (exatamente um dos dois)");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando cartão de crédito recebe receita")
    void create_shouldThrow_whenIncomeOnCreditCard() {
        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(INCOME, "10.00", DATE, null, CARD_ID, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cartão de crédito aceita apenas despesas");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando informa parcelas maiores que 1 em compra em conta")
    void create_shouldThrow_whenInstallmentsOnAccount() {
        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(EXPENSE, "10.00", DATE, ACCOUNT_ID, null, 3)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Parcelamento disponível apenas para compras no cartão de crédito");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando o valor é nulo")
    void create_shouldThrow_whenAmountIsNull() {
        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(EXPENSE, null, DATE, ACCOUNT_ID, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O valor (amount) é obrigatório");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando a data passa de 1 ano à frente")
    void create_shouldThrow_whenDateIsMoreThanOneYearAhead() {
        LocalDate tooFar = LocalDate.now(ZONE).plusYears(1).plusDays(1);

        assertThatThrownBy(() -> transactionService.create(USER_ID,
                request(EXPENSE, "10.00", tooFar, ACCOUNT_ID, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A data não pode ser superior a 1 ano à frente");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar ResourceNotFoundException quando a categoria não existe")
    void create_shouldThrow_whenCategoryNotFound() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(USER_ID, accountRequest(EXPENSE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CATEGORY_NOT_FOUND);

        verify(transactionRepository, never()).save(any(Transaction.class));
        verifyNoInteractions(accountRepository, creditCardRepository);
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando a categoria está excluída")
    void create_shouldThrow_whenCategoryIsDeleted() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, true)));

        assertThatThrownBy(() -> transactionService.create(USER_ID, accountRequest(EXPENSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível usar uma categoria excluída");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando o tipo da categoria difere do tipo da transação")
    void create_shouldThrow_whenCategoryTypeDiffers() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, INCOME, false)));

        assertThatThrownBy(() -> transactionService.create(USER_ID, accountRequest(EXPENSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O tipo da categoria deve ser igual ao tipo da transação");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar ResourceNotFoundException quando a conta não existe")
    void create_shouldThrow_whenAccountNotFound() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(USER_ID, accountRequest(EXPENSE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ACCOUNT_NOT_FOUND);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando a conta está arquivada")
    void create_shouldThrow_whenAccountIsArchived() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account(ACCOUNT_ID, true)));

        assertThatThrownBy(() -> transactionService.create(USER_ID, accountRequest(EXPENSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível usar uma conta arquivada");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar ResourceNotFoundException quando o cartão não existe")
    void create_shouldThrow_whenCardNotFound() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(USER_ID, cardRequest(DATE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CARD_NOT_FOUND);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando o cartão está arquivado")
    void create_shouldThrow_whenCardIsArchived() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, EXPENSE, false)));
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.of(card(CARD_ID, 15, true)));

        assertThatThrownBy(() -> transactionService.create(USER_ID, cardRequest(DATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível usar um cartão arquivado");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: deve atualizar os dados da transação em conta")
    void update_shouldUpdateAccountTransaction() {
        Category oldCategory = category(CATEGORY_ID, EXPENSE, false);
        Category newCategory = category(11L, EXPENSE, false);
        Account oldAccount = account(ACCOUNT_ID, false);
        Account newAccount = account(6L, false);
        Transaction tx = transaction(TX_ID, EXPENSE, oldCategory, oldAccount);
        stubFindOwned(tx);
        when(categoryRepository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(newCategory));
        when(accountRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(newAccount));
        when(transactionRepository.save(tx)).thenReturn(tx);

        LocalDate newDate = LocalDate.of(2026, 4, 2);
        TransactionRequest request = new TransactionRequest(EXPENSE, new BigDecimal("120.00"), 11L, newDate,
                "Nova descrição", "Nova obs", 6L, null, null);

        TransactionResponse response = transactionService.update(USER_ID, TX_ID, request);

        assertThat(tx.getAmount()).isEqualByComparingTo("120.00");
        assertThat(tx.getDate()).isEqualTo(newDate);
        assertThat(tx.getDescription()).isEqualTo("Nova descrição");
        assertThat(tx.getNotes()).isEqualTo("Nova obs");
        assertThat(tx.getCategory()).isSameAs(newCategory);
        assertThat(tx.getAccount()).isSameAs(newAccount);
        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.amount()).isEqualByComparingTo("120.00");
        assertThat(response.category().id()).isEqualTo(11L);
        assertThat(response.account().id()).isEqualTo(6L);
    }

    @Test
    @DisplayName("update: deve permitir manter categoria excluída e conta arquivada já vinculadas")
    void update_shouldAllowKeepingDeletedCategoryAndArchivedAccount() {
        Category deletedCategory = category(CATEGORY_ID, EXPENSE, true);
        Account archivedAccount = account(ACCOUNT_ID, true);
        Transaction tx = transaction(TX_ID, EXPENSE, deletedCategory, archivedAccount);
        stubFindOwned(tx);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(deletedCategory));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(archivedAccount));
        when(transactionRepository.save(tx)).thenReturn(tx);

        TransactionResponse response = transactionService.update(USER_ID, TX_ID, accountRequest(EXPENSE));

        assertThat(response.amount()).isEqualByComparingTo("99.90");
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando a transação não existe, está excluída ou é de outro usuário")
    void update_shouldThrowNotFound_whenNotOwnedOrDeleted() {
        when(transactionRepository.findByIdAndUserIdAndDeletedFalse(TX_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, accountRequest(EXPENSE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TX_NOT_FOUND);

        verify(transactionRepository, never()).save(any(Transaction.class));
        verifyNoInteractions(categoryRepository, accountRepository);
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException ao editar compra no cartão que não é parcelada")
    void update_shouldThrow_whenEditingCreditCardTransaction() {
        Transaction tx = transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), null);
        tx.setCreditCard(card(CARD_ID, 15, false));
        stubFindOwned(tx);

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, cardRequest(DATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A edição de compras no cartão de crédito ainda não está disponível");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException quando o request informa conta e cartão")
    void update_shouldThrow_whenRequestHasBothSources() {
        stubFindOwned(transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false)));

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID,
                request(EXPENSE, "10.00", DATE, ACCOUNT_ID, CARD_ID, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe accountId ou creditCardId (exatamente um dos dois)");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException quando o tipo da categoria difere do tipo da transação")
    void update_shouldThrow_whenCategoryTypeDiffers() {
        stubFindOwned(transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false)));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category(CATEGORY_ID, INCOME, false)));

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, accountRequest(EXPENSE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O tipo da categoria deve ser igual ao tipo da transação");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException ao trocar para categoria excluída")
    void update_shouldThrow_whenChangingToDeletedCategory() {
        stubFindOwned(transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false)));
        when(categoryRepository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(category(11L, EXPENSE, true)));

        TransactionRequest request = new TransactionRequest(EXPENSE, new BigDecimal("10.00"), 11L, DATE,
                null, null, ACCOUNT_ID, null, null);

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível usar uma categoria excluída");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException ao trocar para conta arquivada")
    void update_shouldThrow_whenChangingToArchivedAccount() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        stubFindOwned(transaction(TX_ID, EXPENSE, category, account(ACCOUNT_ID, false)));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(accountRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(account(6L, true)));

        TransactionRequest request = request(EXPENSE, "10.00", DATE, 6L, null, null);

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível usar uma conta arquivada");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando a nova conta não existe")
    void update_shouldThrow_whenAccountNotFound() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        stubFindOwned(transaction(TX_ID, EXPENSE, category, account(ACCOUNT_ID, false)));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(accountRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.empty());

        TransactionRequest request = request(EXPENSE, "10.00", DATE, 6L, null, null);

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ACCOUNT_NOT_FOUND);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ---------- update (compra parcelada) ----------

    @Test
    @DisplayName("update parcelada: deve atualizar categoria, descrição e observações de todo o grupo sem alterar valor")
    void update_shouldUpdateWholeInstallmentGroupKeepingAmount() {
        Category oldCategory = category(CATEGORY_ID, EXPENSE, false);
        Category newCategory = category(11L, EXPENSE, false);
        Transaction p1 = installmentTx(TX_ID, oldCategory, false);
        Transaction p2 = installmentTx(101L, oldCategory, false);
        stubFindOwned(p1);
        when(categoryRepository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(newCategory));
        when(transactionRepository.findAllByInstallmentGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(List.of(p1, p2));

        TransactionRequest request = new TransactionRequest(EXPENSE, new BigDecimal("9999.00"), 11L, DATE,
                "Notebook", "12x", null, CARD_ID, 12);

        TransactionResponse response = transactionService.update(USER_ID, TX_ID, request);

        assertThat(List.of(p1, p2)).allSatisfy(t -> {
            assertThat(t.getCategory()).isSameAs(newCategory);
            assertThat(t.getDescription()).isEqualTo("Notebook");
            assertThat(t.getNotes()).isEqualTo("12x");
            assertThat(t.getAmount()).isEqualByComparingTo("50.00");
            assertThat(t.getDate()).isEqualTo(DATE);
        });
        assertThat(response.id()).isEqualTo(TX_ID);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.category().id()).isEqualTo(11L);
        verify(transactionRepository).saveAll(List.of(p1, p2));
    }

    @Test
    @DisplayName("update parcelada: não deve alterar parcelas excluídas do grupo")
    void update_shouldSkipDeletedInstallments() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        Transaction active = installmentTx(TX_ID, category, false);
        Transaction deleted = installmentTx(101L, category, true);
        stubFindOwned(active);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(category));
        when(transactionRepository.findAllByInstallmentGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(List.of(active, deleted));

        transactionService.update(USER_ID, TX_ID, cardRequest(DATE));

        assertThat(deleted.getDescription()).isEqualTo("Descrição original");
        verify(transactionRepository).saveAll(List.of(active));
    }

    @Test
    @DisplayName("update parcelada: deve lançar BusinessRuleException quando tenta alterar o tipo")
    void update_shouldThrow_whenChangingTypeOfInstallmentPurchase() {
        stubFindOwned(installmentTx(TX_ID, category(CATEGORY_ID, EXPENSE, false), false));

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID,
                request(INCOME, "10.00", DATE, null, CARD_ID, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O tipo não pode ser alterado em compra parcelada");

        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("update parcelada: deve lançar ResourceNotFoundException quando a categoria não existe")
    void update_shouldThrow_whenInstallmentCategoryNotFound() {
        stubFindOwned(installmentTx(TX_ID, category(CATEGORY_ID, EXPENSE, false), false));
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(USER_ID, TX_ID, cardRequest(DATE)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CATEGORY_NOT_FOUND);

        verify(transactionRepository, never()).saveAll(any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve fazer soft delete da transação simples")
    void delete_shouldSoftDeleteSingleTransaction() {
        Transaction tx = transaction(TX_ID, EXPENSE, category(CATEGORY_ID, EXPENSE, false), account(ACCOUNT_ID, false));
        stubFindOwned(tx);

        transactionService.delete(USER_ID, TX_ID);

        assertThat(tx.isDeleted()).isTrue();
        verify(transactionRepository).saveAll(List.of(tx));
        verify(transactionRepository, never()).findAllByInstallmentGroupIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("delete: deve fazer soft delete de todo o grupo quando a compra é parcelada")
    void delete_shouldSoftDeleteWholeInstallmentGroup() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        Transaction p1 = installmentTx(TX_ID, category, false);
        Transaction p2 = installmentTx(101L, category, false);
        Transaction p3 = installmentTx(102L, category, false);
        stubFindOwned(p1);
        when(transactionRepository.findAllByInstallmentGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(List.of(p1, p2, p3));

        transactionService.delete(USER_ID, TX_ID);

        assertThat(List.of(p1, p2, p3)).allMatch(Transaction::isDeleted);
        verify(transactionRepository).saveAll(List.of(p1, p2, p3));
    }

    @Test
    @DisplayName("delete: não deve reprocessar parcelas do grupo que já estavam excluídas")
    void delete_shouldSkipAlreadyDeletedInstallments() {
        Category category = category(CATEGORY_ID, EXPENSE, false);
        Transaction active = installmentTx(TX_ID, category, false);
        Transaction alreadyDeleted = installmentTx(101L, category, true);
        stubFindOwned(active);
        when(transactionRepository.findAllByInstallmentGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(List.of(active, alreadyDeleted));

        transactionService.delete(USER_ID, TX_ID);

        verify(transactionRepository).saveAll(List.of(active));
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException quando a transação não existe, está excluída ou é de outro usuário")
    void delete_shouldThrowNotFound_whenNotOwnedOrDeleted() {
        when(transactionRepository.findByIdAndUserIdAndDeletedFalse(TX_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(USER_ID, TX_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TX_NOT_FOUND);

        verify(transactionRepository, never()).saveAll(any());
    }
}