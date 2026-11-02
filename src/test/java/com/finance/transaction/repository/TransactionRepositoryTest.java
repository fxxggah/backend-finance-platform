package com.finance.transaction.repository;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.AccountType;
import com.finance.common.enums.TransactionType;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.dashboard.dto.CategoryExpenseDTO;
import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.entity.Transaction;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

    private static final TransactionType EXPENSE = TransactionType.EXPENSE;
    private static final TransactionType INCOME = TransactionType.INCOME;

    private static final LocalDate JAN_10 = LocalDate.of(2026, 1, 10);
    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);
    private static final LocalDate JAN_20 = LocalDate.of(2026, 1, 20);
    private static final LocalDate FEB_05 = LocalDate.of(2026, 2, 5);

    private static final LocalDate INVOICE_JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate INVOICE_FEB = LocalDate.of(2026, 2, 1);
    private static final LocalDate INVOICE_MAR = LocalDate.of(2026, 3, 1);

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CreditCardRepository creditCardRepository;
    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;
    private Category expenseCategory;
    private Category incomeCategory;
    private Category otherCategory;
    private Account account;
    private Account account2;
    private CreditCard card;
    private CreditCard card2;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));

        expenseCategory = categoryRepository.saveAndFlush(new Category(owner, "Mercado", EXPENSE, "#3B82F6", "tag"));
        incomeCategory = categoryRepository.saveAndFlush(new Category(owner, "Salário", INCOME, "#10B981", "wallet"));
        otherCategory = categoryRepository.saveAndFlush(new Category(owner, "Transporte", EXPENSE, "#F59E0B", "bus"));

        // Contas
        account = accountRepository.saveAndFlush(
                new Account(
                        owner,
                        "Conta corrente",
                        AccountType.BANK,
                        BigDecimal.ZERO,
                        "#1E90FF",
                        "bank-icon"
                )
        );

        account2 = accountRepository.saveAndFlush(
                new Account(
                        owner,
                        "Poupança",
                        AccountType.BANK,
                        new BigDecimal("150.00"),
                        "#32CD32",
                        "piggy-bank-icon"
                )
        );

// Cartões de Crédito
        card = creditCardRepository.saveAndFlush(
                new CreditCard(
                        owner,
                        "Nubank",
                        new BigDecimal("5000.00"),
                        8,
                        15,
                        "#8A05BE",
                        "credit-card-icon"
                )
        );

        card2 = creditCardRepository.saveAndFlush(
                new CreditCard(
                        owner,
                        "Itaú",
                        new BigDecimal("12000.00"),
                        3,
                        10,
                        "#EC7000",
                        "credit-card-icon"
                )
        );
    }

    // ---------- helpers ----------

    private Transaction newTx(User user, Category category, TransactionType type, String amount, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setCategory(category);
        tx.setType(type);
        tx.setAmount(new BigDecimal(amount));
        tx.setDate(date);
        tx.setDescription("Descrição");
        return tx;
    }

    private Transaction save(Transaction tx) {
        return transactionRepository.saveAndFlush(tx);
    }

    private Transaction expenseOnAccount(String amount, LocalDate date) {
        Transaction tx = newTx(owner, expenseCategory, EXPENSE, amount, date);
        tx.setAccount(account);
        return save(tx);
    }

    private Transaction incomeOnAccount(String amount, LocalDate date) {
        Transaction tx = newTx(owner, incomeCategory, INCOME, amount, date);
        tx.setAccount(account);
        return save(tx);
    }

    private Transaction expenseOnCard(String amount, LocalDate date, LocalDate invoiceReference) {
        Transaction tx = newTx(owner, expenseCategory, EXPENSE, amount, date);
        tx.setCreditCard(card);
        tx.setInvoiceReference(invoiceReference);
        return save(tx);
    }

    private Transaction markDeleted(Transaction tx) {
        tx.setDeleted(true);
        return save(tx);
    }

    private static TransactionFilter noFilter() {
        return new TransactionFilter(null, null, null, null, null, null, null);
    }

    private static TransactionFilter filterByText(String q) {
        return new TransactionFilter(null, null, null, null, null, null, q);
    }

    private List<Transaction> findWithSpec(TransactionFilter filter) {
        return transactionRepository.findAll(
                TransactionSpecifications.filter(owner.getId(), filter),
                Sort.by(Sort.Direction.ASC, "id"));
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir transação com id, auditoria e valores padrão")
    void save_shouldPersistTransactionWithDefaults() {
        Transaction saved = expenseOnAccount("49.90", JAN_10);
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getType()).isEqualTo(EXPENSE);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("49.90");
        assertThat(reloaded.getDate()).isEqualTo(JAN_10);
        assertThat(reloaded.isDeleted()).isFalse();
        assertThat(reloaded.getInvoiceReference()).isNull();
        assertThat(reloaded.getInstallmentGroupId()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve preservar os dados de parcelamento e de fatura")
    void save_shouldKeepInstallmentAndInvoiceData() {
        Transaction tx = newTx(owner, expenseCategory, EXPENSE, "100.00", JAN_10);
        tx.setCreditCard(card);
        tx.setInvoiceReference(INVOICE_JAN);
        tx.setInstallmentGroupId("group-1");
        tx.setInstallmentNumber(2);
        tx.setInstallmentTotal(5);
        Transaction saved = save(tx);
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getInvoiceReference()).isEqualTo(INVOICE_JAN);
        assertThat(reloaded.getInstallmentGroupId()).isEqualTo("group-1");
        assertThat(reloaded.getInstallmentNumber()).isEqualTo(2);
        assertThat(reloaded.getInstallmentTotal()).isEqualTo(5);
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar a transação do usuário, inclusive excluída")
    void findByIdAndUserId_shouldReturnTransaction_whenOwned() {
        Transaction active = expenseOnAccount("10.00", JAN_10);
        Transaction deleted = markDeleted(expenseOnAccount("20.00", JAN_10));

        assertThat(transactionRepository.findByIdAndUserId(active.getId(), owner.getId())).isPresent();
        assertThat(transactionRepository.findByIdAndUserId(deleted.getId(), owner.getId())).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a transação é de outro usuário ou não existe")
    void findByIdAndUserId_shouldReturnEmpty_whenOwnedByAnotherUserOrNotExists() {
        Transaction tx = expenseOnAccount("10.00", JAN_10);

        assertThat(transactionRepository.findByIdAndUserId(tx.getId(), other.getId())).isEmpty();
        assertThat(transactionRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findByIdAndUserIdAndDeletedFalse ----------

    @Test
    @DisplayName("findByIdAndUserIdAndDeletedFalse: deve retornar somente transações ativas do usuário")
    void findByIdAndUserIdAndDeletedFalse_shouldReturnOnlyActiveOwned() {
        Transaction active = expenseOnAccount("10.00", JAN_10);
        Transaction deleted = markDeleted(expenseOnAccount("20.00", JAN_10));

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedFalse(active.getId(), owner.getId())).isPresent();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedFalse(deleted.getId(), owner.getId())).isEmpty();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedFalse(active.getId(), other.getId())).isEmpty();
    }

    // ---------- findAllByUserIdAndDeletedFalse ----------

    @Test
    @DisplayName("findAllByUserIdAndDeletedFalse: deve retornar somente transações ativas do usuário de forma paginada")
    void findAllByUserIdAndDeletedFalse_shouldReturnOnlyActiveOfUserPaged() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnAccount("20.00", JAN_15);
        expenseOnAccount("30.00", JAN_20);
        markDeleted(expenseOnAccount("40.00", JAN_20));
        Transaction fromOther = newTx(other, expenseCategory, EXPENSE, "50.00", JAN_10);
        save(fromOther);

        Page<Transaction> page = transactionRepository.findAllByUserIdAndDeletedFalse(owner.getId(), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    // ---------- findAllByInstallmentGroupIdAndUserId ----------

    @Test
    @DisplayName("findAllByInstallmentGroupIdAndUserId: deve retornar o grupo do usuário, inclusive parcelas excluídas")
    void findAllByInstallmentGroupIdAndUserId_shouldReturnWholeGroupOfUser() {
        Transaction p1 = expenseOnCard("100.00", JAN_10, INVOICE_JAN);
        Transaction p2 = expenseOnCard("100.00", JAN_10, INVOICE_FEB);
        Transaction p3 = expenseOnCard("100.00", JAN_10, INVOICE_MAR);
        for (Transaction p : List.of(p1, p2, p3)) {
            p.setInstallmentGroupId("group-1");
            save(p);
        }
        markDeleted(p3);

        Transaction otherGroup = expenseOnCard("100.00", JAN_10, INVOICE_JAN);
        otherGroup.setInstallmentGroupId("group-2");
        save(otherGroup);

        Transaction sameGroupOtherUser = newTx(other, expenseCategory, EXPENSE, "100.00", JAN_10);
        sameGroupOtherUser.setInstallmentGroupId("group-1");
        save(sameGroupOtherUser);

        List<Transaction> result = transactionRepository.findAllByInstallmentGroupIdAndUserId("group-1", owner.getId());

        assertThat(result).extracting(Transaction::getId).containsExactlyInAnyOrder(p1.getId(), p2.getId(), p3.getId());
    }

    // ---------- existsBy... ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono, inclusive se excluída")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        Transaction active = expenseOnAccount("10.00", JAN_10);
        Transaction deleted = markDeleted(expenseOnAccount("20.00", JAN_10));

        assertThat(transactionRepository.existsByIdAndUserId(active.getId(), owner.getId())).isTrue();
        assertThat(transactionRepository.existsByIdAndUserId(deleted.getId(), owner.getId())).isTrue();
        assertThat(transactionRepository.existsByIdAndUserId(active.getId(), other.getId())).isFalse();
        assertThat(transactionRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    @Test
    @DisplayName("existsByAccountIdAndDeletedFalse: deve considerar apenas transações ativas da conta")
    void existsByAccountIdAndDeletedFalse_shouldConsiderOnlyActiveTransactions() {
        expenseOnAccount("10.00", JAN_10);
        markDeleted(newAccountTx(account2));

        assertThat(transactionRepository.existsByAccountIdAndDeletedFalse(account.getId())).isTrue();
        assertThat(transactionRepository.existsByAccountIdAndDeletedFalse(account2.getId())).isFalse();
        assertThat(transactionRepository.existsByAccountIdAndDeletedFalse(999_999L)).isFalse();
    }

    private Transaction newAccountTx(Account target) {
        Transaction tx = newTx(owner, expenseCategory, EXPENSE, "10.00", JAN_10);
        tx.setAccount(target);
        return save(tx);
    }

    @Test
    @DisplayName("existsByCreditCardIdAndDeletedFalse: deve considerar apenas transações ativas do cartão")
    void existsByCreditCardIdAndDeletedFalse_shouldConsiderOnlyActiveTransactions() {
        expenseOnCard("10.00", JAN_10, INVOICE_JAN);
        Transaction deletedOnCard2 = newTx(owner, expenseCategory, EXPENSE, "10.00", JAN_10);
        deletedOnCard2.setCreditCard(card2);
        deletedOnCard2.setDeleted(true);
        save(deletedOnCard2);

        assertThat(transactionRepository.existsByCreditCardIdAndDeletedFalse(card.getId())).isTrue();
        assertThat(transactionRepository.existsByCreditCardIdAndDeletedFalse(card2.getId())).isFalse();
        assertThat(transactionRepository.existsByCreditCardIdAndDeletedFalse(999_999L)).isFalse();
    }

    @Test
    @DisplayName("existsByCategoryIdAndDeletedFalse: deve considerar apenas transações ativas da categoria")
    void existsByCategoryIdAndDeletedFalse_shouldConsiderOnlyActiveTransactions() {
        expenseOnAccount("10.00", JAN_10);
        Transaction deleted = newTx(owner, otherCategory, EXPENSE, "10.00", JAN_10);
        deleted.setAccount(account);
        deleted.setDeleted(true);
        save(deleted);

        assertThat(transactionRepository.existsByCategoryIdAndDeletedFalse(expenseCategory.getId())).isTrue();
        assertThat(transactionRepository.existsByCategoryIdAndDeletedFalse(otherCategory.getId())).isFalse();
        assertThat(transactionRepository.existsByCategoryIdAndDeletedFalse(incomeCategory.getId())).isFalse();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todas as transações do usuário e preservar as de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserTransactions() {
        expenseOnAccount("10.00", JAN_10);
        markDeleted(expenseOnAccount("20.00", JAN_10));
        Transaction fromOther = save(newTx(other, expenseCategory, EXPENSE, "50.00", JAN_10));

        transactionRepository.deleteAllByUserId(owner.getId());
        transactionRepository.flush();
        entityManager.clear();

        assertThat(transactionRepository.findAllByUserIdAndDeletedFalse(owner.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(transactionRepository.findAll()).extracting(Transaction::getId).containsExactly(fromOther.getId());
    }

    // ---------- getNetBalanceByAccountId ----------

    @Test
    @DisplayName("getNetBalanceByAccountId: deve somar receitas e subtrair despesas da conta")
    void getNetBalanceByAccountId_shouldSumIncomeMinusExpense() {
        incomeOnAccount("1000.00", JAN_10);
        expenseOnAccount("300.50", JAN_15);
        expenseOnAccount("0.50", JAN_20);

        assertThat(transactionRepository.getNetBalanceByAccountId(account.getId())).isEqualByComparingTo("699.00");
    }

    @Test
    @DisplayName("getNetBalanceByAccountId: deve ignorar transações excluídas e de outras contas")
    void getNetBalanceByAccountId_shouldIgnoreDeletedAndOtherAccounts() {
        incomeOnAccount("1000.00", JAN_10);
        markDeleted(expenseOnAccount("300.00", JAN_15));
        newAccountTx(account2);

        assertThat(transactionRepository.getNetBalanceByAccountId(account.getId())).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("getNetBalanceByAccountId: deve retornar zero quando a conta não tem transações")
    void getNetBalanceByAccountId_shouldReturnZero_whenNoTransactions() {
        assertThat(transactionRepository.getNetBalanceByAccountId(account.getId())).isEqualByComparingTo("0");
    }

    // ---------- sumTotalAmountByCreditCardId ----------

    @Test
    @DisplayName("sumTotalAmountByCreditCardId: deve somar as transações ativas do cartão")
    void sumTotalAmountByCreditCardId_shouldSumActiveTransactionsOfCard() {
        expenseOnCard("100.00", JAN_10, INVOICE_JAN);
        expenseOnCard("50.25", FEB_05, INVOICE_FEB);
        markDeleted(expenseOnCard("999.00", FEB_05, INVOICE_FEB));
        Transaction onCard2 = newTx(owner, expenseCategory, EXPENSE, "70.00", JAN_10);
        onCard2.setCreditCard(card2);
        save(onCard2);

        assertThat(transactionRepository.sumTotalAmountByCreditCardId(card.getId())).isEqualByComparingTo("150.25");
    }

    @Test
    @DisplayName("sumTotalAmountByCreditCardId: deve retornar zero quando o cartão não tem transações")
    void sumTotalAmountByCreditCardId_shouldReturnZero_whenNoTransactions() {
        assertThat(transactionRepository.sumTotalAmountByCreditCardId(card.getId())).isEqualByComparingTo("0");
    }

    // ---------- sumAmountByCreditCardIdAndInvoiceReference ----------

    @Test
    @DisplayName("sumAmountByCreditCardIdAndInvoiceReference: deve somar somente a fatura informada, ignorando excluídas")
    void sumAmountByCreditCardIdAndInvoiceReference_shouldSumOnlyGivenInvoice() {
        expenseOnCard("100.00", JAN_10, INVOICE_JAN);
        expenseOnCard("25.00", JAN_20, INVOICE_JAN);
        expenseOnCard("80.00", FEB_05, INVOICE_FEB);
        markDeleted(expenseOnCard("500.00", JAN_10, INVOICE_JAN));

        assertThat(transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(card.getId(), INVOICE_JAN))
                .isEqualByComparingTo("125.00");
        assertThat(transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(card.getId(), INVOICE_FEB))
                .isEqualByComparingTo("80.00");
        assertThat(transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(card.getId(), INVOICE_MAR))
                .isEqualByComparingTo("0");
    }

    // ---------- findDistinctInvoiceReferencesByCreditCardId ----------

    @Test
    @DisplayName("findDistinctInvoiceReferencesByCreditCardId: deve retornar faturas distintas em ordem decrescente, ignorando excluídas")
    void findDistinctInvoiceReferencesByCreditCardId_shouldReturnDistinctDescending() {
        expenseOnCard("10.00", JAN_10, INVOICE_JAN);
        expenseOnCard("10.00", JAN_20, INVOICE_JAN);
        expenseOnCard("10.00", FEB_05, INVOICE_FEB);
        markDeleted(expenseOnCard("10.00", FEB_05, INVOICE_MAR));
        expenseOnAccount("10.00", JAN_10);

        List<LocalDate> result = transactionRepository.findDistinctInvoiceReferencesByCreditCardId(card.getId());

        assertThat(result).containsExactly(INVOICE_FEB, INVOICE_JAN);
    }

    // ---------- findAllByCreditCardIdAndInvoiceReferenceAndDeletedFalse ----------

    @Test
    @DisplayName("findAllByCreditCardIdAndInvoiceReferenceAndDeletedFalse: deve retornar somente transações ativas da fatura")
    void findAllByCreditCardIdAndInvoiceReferenceAndDeletedFalse_shouldReturnOnlyActiveOfInvoice() {
        Transaction inJan = expenseOnCard("10.00", JAN_10, INVOICE_JAN);
        expenseOnCard("10.00", FEB_05, INVOICE_FEB);
        markDeleted(expenseOnCard("10.00", JAN_20, INVOICE_JAN));

        List<Transaction> result = transactionRepository
                .findAllByCreditCardIdAndInvoiceReferenceAndDeletedFalse(card.getId(), INVOICE_JAN);

        assertThat(result).extracting(Transaction::getId).containsExactly(inJan.getId());
    }

    // ---------- findAllByUserIdAndDeletedFalseWithFetch ----------

    @Test
    @DisplayName("findAllByUserIdAndDeletedFalseWithFetch: deve carregar categoria, conta e cartão sem lazy loading")
    void findAllByUserIdAndDeletedFalseWithFetch_shouldInitializeAssociations() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnCard("20.00", JAN_15, INVOICE_JAN);
        markDeleted(expenseOnAccount("30.00", JAN_20));
        save(newTx(other, expenseCategory, EXPENSE, "40.00", JAN_10));
        entityManager.clear();

        Page<Transaction> page = transactionRepository
                .findAllByUserIdAndDeletedFalseWithFetch(owner.getId(), PageRequest.of(0, 10, Sort.by("date")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allSatisfy(tx -> {
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "category")).isTrue();
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "account")).isTrue();
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "creditCard")).isTrue();
        });
    }

    // ---------- sumAmountByTypeAndPeriodAndFilters ----------

    @Test
    @DisplayName("sumAmountByTypeAndPeriodAndFilters: deve somar por tipo e período com limites inclusivos")
    void sumAmountByTypeAndPeriodAndFilters_shouldSumByTypeAndInclusivePeriod() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnAccount("20.00", JAN_20);
        expenseOnAccount("999.00", FEB_05);
        incomeOnAccount("500.00", JAN_15);

        BigDecimal expenses = transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, null, null);
        BigDecimal incomes = transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), INCOME, JAN_10, JAN_20, null, null);

        assertThat(expenses).isEqualByComparingTo("30.00");
        assertThat(incomes).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("sumAmountByTypeAndPeriodAndFilters: deve ignorar excluídas e transações de outros usuários")
    void sumAmountByTypeAndPeriodAndFilters_shouldIgnoreDeletedAndOtherUsers() {
        expenseOnAccount("10.00", JAN_10);
        markDeleted(expenseOnAccount("20.00", JAN_10));
        save(newTx(other, expenseCategory, EXPENSE, "300.00", JAN_10));

        assertThat(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, null, null)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("sumAmountByTypeAndPeriodAndFilters: deve filtrar por conta ou por cartão quando informados")
    void sumAmountByTypeAndPeriodAndFilters_shouldFilterByAccountOrCard() {
        expenseOnAccount("10.00", JAN_10);
        newAccountTx(account2);
        expenseOnCard("40.00", JAN_10, INVOICE_JAN);

        assertThat(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, account.getId(), null)).isEqualByComparingTo("10.00");
        assertThat(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, null, card.getId())).isEqualByComparingTo("40.00");
        assertThat(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, null, null)).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("sumAmountByTypeAndPeriodAndFilters: deve retornar zero quando não há transações no período")
    void sumAmountByTypeAndPeriodAndFilters_shouldReturnZero_whenNoTransactions() {
        assertThat(transactionRepository.sumAmountByTypeAndPeriodAndFilters(
                owner.getId(), EXPENSE, JAN_10, JAN_20, null, null)).isEqualByComparingTo("0");
    }

    // ---------- findExpensesByCategory ----------

    @Test
    @DisplayName("findExpensesByCategory: deve agrupar despesas por categoria em ordem decrescente de valor")
    void findExpensesByCategory_shouldGroupByCategoryOrderedByTotalDesc() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnAccount("15.00", JAN_15);
        Transaction transport = newTx(owner, otherCategory, EXPENSE, "100.00", JAN_20);
        transport.setAccount(account);
        save(transport);
        incomeOnAccount("5000.00", JAN_10);
        markDeleted(expenseOnAccount("700.00", JAN_10));

        List<CategoryExpenseDTO> result = transactionRepository
                .findExpensesByCategory(owner.getId(), JAN_10, JAN_20, null, null);

        // ATENÇÃO: ajuste os acessores do CategoryExpenseDTO (name/total/count) para os reais
        assertThat(result).extracting(CategoryExpenseDTO::name).containsExactly("Transporte", "Mercado");
        assertThat(result.get(0).total()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).total()).isEqualByComparingTo("25.00");
        assertThat(result.get(1).count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findExpensesByCategory: deve respeitar período e filtro de conta")
    void findExpensesByCategory_shouldRespectPeriodAndAccountFilter() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnAccount("20.00", FEB_05);
        newAccountTx(account2);

        List<CategoryExpenseDTO> result = transactionRepository
                .findExpensesByCategory(owner.getId(), JAN_10, JAN_20, account.getId(), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).total()).isEqualByComparingTo("10.00");
    }

    // ---------- findEvolutionData ----------

    @Test
    @DisplayName("findEvolutionData: deve agrupar o total por data e tipo")
    void findEvolutionData_shouldGroupByDateAndType() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnAccount("5.00", JAN_10);
        incomeOnAccount("100.00", JAN_10);
        expenseOnAccount("7.00", JAN_15);
        expenseOnAccount("999.00", FEB_05);
        markDeleted(expenseOnAccount("888.00", JAN_10));

        List<Object[]> rows = transactionRepository.findEvolutionData(owner.getId(), JAN_10, JAN_20, null, null);

        assertThat(rows).hasSize(3);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(JAN_10);
            assertThat(row[1]).isEqualTo(EXPENSE);
            assertThat((BigDecimal) row[2]).isEqualByComparingTo("15.00");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(JAN_10);
            assertThat(row[1]).isEqualTo(INCOME);
            assertThat((BigDecimal) row[2]).isEqualByComparingTo("100.00");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(JAN_15);
            assertThat(row[1]).isEqualTo(EXPENSE);
            assertThat((BigDecimal) row[2]).isEqualByComparingTo("7.00");
        });
    }

    @Test
    @DisplayName("findEvolutionData: deve filtrar por cartão quando informado")
    void findEvolutionData_shouldFilterByCreditCard() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnCard("40.00", JAN_10, INVOICE_JAN);

        List<Object[]> rows = transactionRepository.findEvolutionData(owner.getId(), JAN_10, JAN_20, null, card.getId());

        assertThat(rows).hasSize(1);
        assertThat((BigDecimal) rows.get(0)[2]).isEqualByComparingTo("40.00");
    }

    // ---------- TransactionSpecifications.filter ----------

    @Test
    @DisplayName("spec: sem filtros deve retornar todas as transações ativas do usuário")
    void spec_withoutFilters_shouldReturnAllActiveOfUser() {
        Transaction a = expenseOnAccount("10.00", JAN_10);
        Transaction b = incomeOnAccount("20.00", JAN_15);
        markDeleted(expenseOnAccount("30.00", JAN_20));
        save(newTx(other, expenseCategory, EXPENSE, "40.00", JAN_10));

        assertThat(findWithSpec(noFilter())).extracting(Transaction::getId).containsExactly(a.getId(), b.getId());
    }

    @Test
    @DisplayName("spec: deve filtrar por período com limites inclusivos")
    void spec_shouldFilterByInclusivePeriod() {
        expenseOnAccount("10.00", JAN_10);
        Transaction inside = expenseOnAccount("20.00", JAN_15);
        Transaction lastDay = expenseOnAccount("30.00", JAN_20);
        expenseOnAccount("40.00", FEB_05);

        assertThat(findWithSpec(new TransactionFilter(JAN_15, JAN_20, null, null, null, null, null)))
                .extracting(Transaction::getId).containsExactly(inside.getId(), lastDay.getId());
        assertThat(findWithSpec(new TransactionFilter(FEB_05, null, null, null, null, null, null))).hasSize(1);
        assertThat(findWithSpec(new TransactionFilter(null, JAN_10, null, null, null, null, null))).hasSize(1);
    }

    @Test
    @DisplayName("spec: deve filtrar por tipo")
    void spec_shouldFilterByType() {
        expenseOnAccount("10.00", JAN_10);
        Transaction income = incomeOnAccount("20.00", JAN_10);

        assertThat(findWithSpec(new TransactionFilter(null, null, INCOME, null, null, null, null)))
                .extracting(Transaction::getId).containsExactly(income.getId());
    }

    @Test
    @DisplayName("spec: deve filtrar por categoria")
    void spec_shouldFilterByCategory() {
        expenseOnAccount("10.00", JAN_10);
        Transaction transport = newTx(owner, otherCategory, EXPENSE, "20.00", JAN_10);
        transport.setAccount(account);
        save(transport);

        assertThat(findWithSpec(new TransactionFilter(null, null, null, otherCategory.getId(), null, null, null)))
                .extracting(Transaction::getId).containsExactly(transport.getId());
    }

    @Test
    @DisplayName("spec: deve filtrar por conta e por cartão")
    void spec_shouldFilterByAccountAndCreditCard() {
        Transaction onAccount = expenseOnAccount("10.00", JAN_10);
        newAccountTx(account2);
        Transaction onCard = expenseOnCard("20.00", JAN_10, INVOICE_JAN);

        assertThat(findWithSpec(new TransactionFilter(null, null, null, null, account.getId(), null, null)))
                .extracting(Transaction::getId).containsExactly(onAccount.getId());
        assertThat(findWithSpec(new TransactionFilter(null, null, null, null, null, card.getId(), null)))
                .extracting(Transaction::getId).containsExactly(onCard.getId());
    }

    @Test
    @DisplayName("spec: busca textual deve ignorar maiúsculas/minúsculas e procurar em descrição e observações")
    void spec_textSearch_shouldIgnoreCaseAndSearchDescriptionAndNotes() {
        Transaction byDescription = expenseOnAccount("10.00", JAN_10);
        byDescription.setDescription("Compras no SUPERMERCADO");
        save(byDescription);

        Transaction byNotes = expenseOnAccount("20.00", JAN_10);
        byNotes.setNotes("pagar no supermercado do bairro");
        save(byNotes);

        expenseOnAccount("30.00", JAN_10);

        assertThat(findWithSpec(filterByText("SuperMercado")))
                .extracting(Transaction::getId).containsExactly(byDescription.getId(), byNotes.getId());
    }

    @Test
    @DisplayName("spec: busca textual deve remover espaços das pontas e ignorar texto em branco")
    void spec_textSearch_shouldTrimAndIgnoreBlank() {
        Transaction tx = expenseOnAccount("10.00", JAN_10);
        tx.setDescription("Padaria");
        save(tx);
        expenseOnAccount("20.00", JAN_10);

        assertThat(findWithSpec(filterByText("  padaria  "))).extracting(Transaction::getId).containsExactly(tx.getId());
        assertThat(findWithSpec(filterByText("   "))).hasSize(2);
        assertThat(findWithSpec(filterByText(null))).hasSize(2);
    }

    @Test
    @DisplayName("spec: busca textual deve tratar % e _ como caracteres literais")
    void spec_textSearch_shouldEscapeLikeWildcards() {
        Transaction percent = expenseOnAccount("10.00", JAN_10);
        percent.setDescription("Desconto 100% off");
        save(percent);
        Transaction noPercent = expenseOnAccount("20.00", JAN_10);
        noPercent.setDescription("Desconto 1000 off");
        save(noPercent);

        Transaction underscore = expenseOnAccount("30.00", JAN_10);
        underscore.setDescription("item_a");
        save(underscore);
        Transaction noUnderscore = expenseOnAccount("40.00", JAN_10);
        noUnderscore.setDescription("itemXa");
        save(noUnderscore);

        assertThat(findWithSpec(filterByText("100%"))).extracting(Transaction::getId).containsExactly(percent.getId());
        assertThat(findWithSpec(filterByText("item_a"))).extracting(Transaction::getId).containsExactly(underscore.getId());
    }

    @Test
    @DisplayName("spec: deve combinar vários filtros com AND")
    void spec_shouldCombineFiltersWithAnd() {
        Transaction match = expenseOnAccount("10.00", JAN_15);
        match.setDescription("Feira");
        save(match);
        expenseOnAccount("20.00", JAN_15);
        Transaction wrongDate = expenseOnAccount("30.00", FEB_05);
        wrongDate.setDescription("Feira");
        save(wrongDate);
        incomeOnAccount("40.00", JAN_15);

        TransactionFilter filter = new TransactionFilter(
                JAN_10, JAN_20, EXPENSE, expenseCategory.getId(), account.getId(), null, "feira");

        assertThat(findWithSpec(filter)).extracting(Transaction::getId).containsExactly(match.getId());
    }

    @Test
    @DisplayName("spec: deve paginar e calcular o total (query de count sem fetch)")
    void spec_shouldPaginateAndCount() {
        for (int day = 1; day <= 5; day++) {
            expenseOnAccount("10.00", LocalDate.of(2026, 1, day));
        }
        markDeleted(expenseOnAccount("10.00", JAN_10));
        entityManager.clear();

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.filter(owner.getId(), noFilter()),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "date")));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Transaction::getDate)
                .containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
    }

    @Test
    @DisplayName("spec: deve carregar categoria, conta e cartão na listagem (evita N+1)")
    void spec_shouldFetchAssociations() {
        expenseOnAccount("10.00", JAN_10);
        expenseOnCard("20.00", JAN_15, INVOICE_JAN);
        entityManager.clear();

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.filter(owner.getId(), noFilter()),
                PageRequest.of(0, 10, Sort.by("date")));

        assertThat(page.getContent()).hasSize(2).allSatisfy(tx -> {
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "category")).isTrue();
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "account")).isTrue();
            assertThat(Persistence.getPersistenceUtil().isLoaded(tx, "creditCard")).isTrue();
        });
    }

    @Test
    @DisplayName("spec: deve retornar vazio para usuário sem transações")
    void spec_shouldReturnEmpty_whenUserHasNoTransactions() {
        expenseOnAccount("10.00", JAN_10);

        Optional<Transaction> any = transactionRepository.findAll(
                TransactionSpecifications.filter(other.getId(), noFilter()),
                Sort.by("id")).stream().findAny();

        assertThat(any).isEmpty();
    }
}