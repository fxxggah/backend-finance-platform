package com.finance.creditcard;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.account.service.AccountService;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.enums.AccountType;
import com.finance.common.enums.Period;
import com.finance.common.enums.TransactionType;
import com.finance.creditcard.dto.request.InvoicePaymentRequest;
import com.finance.creditcard.dto.request.CreditCardRequest;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.entity.InvoicePayment;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.creditcard.service.InvoiceService;
import com.finance.creditcard.service.CreditCardService;
import com.finance.dashboard.service.DashboardService;
import com.finance.security.JwtService;
import com.finance.transaction.entity.Transaction;
import com.finance.transaction.dto.request.TransactionRequest;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transaction.service.TransactionService;
import com.finance.transfer.dto.request.TransferRequest;
import com.finance.transfer.service.TransferService;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.name=invoice-payment-test", "app.ai.enabled=false", "app.billing.enabled=false",
        "app.jwt.secret=invoice-payment-synthetic-test-key-at-least-32-bytes",
        "app.jwt.access-token-expiration=900", "app.jwt.refresh-token-expiration=604800",
        "app.google.client-id=synthetic-client", "app.cors.origins=http://localhost:3000",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InvoicePaymentIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final LocalDate MONTH = LocalDate.of(2026, 11, 1);
    private static final String PAYMENT_URL = "/api/v1/credit-cards/{id}/invoices/{reference}/payments";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired CreditCardRepository cards;
    @Autowired CategoryRepository categories;
    @Autowired TransactionRepository transactions;
    @Autowired InvoicePaymentRepository payments;
    @Autowired InvoiceService invoices;
    @Autowired CreditCardService creditCards;
    @Autowired TransactionService transactionService;
    @Autowired TransferService transfers;
    @Autowired DashboardService dashboard;
    @Autowired AccountService balances;
    @Autowired PlatformTransactionManager transactionManager;
    private User owner;
    private User other;
    private Account account;
    private CreditCard card;
    private CreditCard anotherCard;
    private CreditCard othersCard;
    private Category expenseCategory;

    @BeforeEach
    void setup() {
        owner = user();
        other = user();
        account = accounts.saveAndFlush(new Account(owner, "Conta", AccountType.CASH, BigDecimal.ZERO, null, null));
        card = card(owner, "Cartão");
        anotherCard = card(owner, "Outro cartão");
        othersCard = card(other, "Cartão de outro usuário");
        expenseCategory = categories.saveAndFlush(new Category(owner, "Compras", TransactionType.EXPENSE, null, null));
        var purchase = new Transaction();
        purchase.setUser(owner);
        purchase.setCategory(expenseCategory);
        purchase.setCreditCard(card);
        purchase.setType(TransactionType.EXPENSE);
        purchase.setAmount(new BigDecimal("100.00"));
        purchase.setDate(MONTH);
        purchase.setInvoiceReference(MONTH);
        transactions.saveAndFlush(purchase);
    }

    @Test
    void swaggerDescribesPaymentScopeAndConcurrency() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/credit-cards/{id}/invoices/{reference}/payments'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credit-cards/{id}/invoices/{reference}/payments'].post.description",
                        org.hamcrest.Matchers.containsString("concorrentes")))
                .andExpect(jsonPath("$.paths['/api/v1/credit-cards/{id}/invoices/{reference}/payments/{paymentId}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credit-cards/{id}/invoices/{reference}/payments/{paymentId}'].delete.description",
                        org.hamcrest.Matchers.containsString("404 sem excluir")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.description",
                        org.hamcrest.Matchers.containsString("revoga todos os refresh tokens")));
    }

    @Test
    void wrongCardReturns404AndPreservesPayment() throws Exception {
        InvoicePayment payment = payment();
        rejectDelete(owner, anotherCard.getId(), "2026-11", payment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10", "2026-13", "novembro"})
    void wrongOrMalformedMonthReturns404AndPreservesPayment(String reference) throws Exception {
        InvoicePayment payment = payment();
        rejectDelete(owner, card.getId(), reference, payment);
    }

    @Test
    void anotherUserCannotDeletePaymentUsingEitherUsersCard() throws Exception {
        InvoicePayment payment = payment();
        rejectDelete(other, card.getId(), "2026-11", payment);
        rejectDelete(other, othersCard.getId(), "2026-11", payment);
    }

    @Test
    void correctUrlDeletesOnlyThePaymentAndRestoresAccountBalance() throws Exception {
        InvoicePayment payment = payment();
        InvoicePayment preserved = payments.saveAndFlush(new InvoicePayment(owner, anotherCard, account,
                MONTH, new BigDecimal("10.00"), MONTH));
        assertThat(payments.sumPaymentsByAccountId(account.getId())).isEqualByComparingTo("25.00");

        mvc.perform(delete(PAYMENT_URL + "/{paymentId}", card.getId(), "2026-11", payment.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(payments.findById(payment.getId())).isEmpty();
        assertThat(payments.findById(preserved.getId())).isPresent();
        assertThat(payments.sumPaymentsByAccountId(account.getId())).isEqualByComparingTo("10.00");
        assertThat(payments.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), MONTH)).isZero();
        mvc.perform(get("/api/v1/accounts").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalBalance").value(-10));
    }

    @ParameterizedTest
    @CsvSource({"60.00, 422, 60.00, 1", "40.00, 201, 100.00, 2"})
    void concurrentPaymentsUseCommittedBalance(String secondAmount, int expectedStatus,
                                               String expectedPaid, int expectedCount) throws Exception {
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        String authorization = bearer(owner);
        var request = new InvoicePaymentRequest(account.getId(), new BigDecimal("60.00"), MONTH);

        // Real service + database; no mocked aggregates. The first INSERT is uncommitted.
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    invoices.payInvoice(owner.getId(), card, "2026-11", request);
                    inserted.countDown();
                    await(release);
                }));
                assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    secondStarted.countDown();
                    return mvc.perform(post(PAYMENT_URL, card.getId(), "2026-11")
                                    .header("Authorization", authorization).contentType("application/json")
                                    .content("{\"accountId\":" + account.getId() + ",\"amount\":" + secondAmount
                                            + ",\"date\":\"2026-11-01\"}"))
                            .andReturn();
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    second.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException expected) {
                    // Correct serialization waits for commit before reading the balance.
                } finally {
                    release.countDown();
                }
                first.get(10, TimeUnit.SECONDS);
                var response = second.get(10, TimeUnit.SECONDS).getResponse();
                assertThat(response.getStatus()).isEqualTo(expectedStatus);
                if (expectedStatus == 422) {
                    assertThat(response.getContentType()).startsWith("application/problem+json");
                    assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                            .contains("O valor do pagamento não pode ser maior que o saldo restante da fatura.");
                }
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
        }
        assertThat(payments.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), MONTH))
                .isEqualByComparingTo(expectedPaid).isLessThanOrEqualTo(new BigDecimal("100.00"));
        assertThat(payments.findAllByUserIdAndCreditCardIdAndInvoiceReference(owner.getId(), card.getId(), MONTH))
                .hasSize(expectedCount);
        assertThat(payments.sumPaymentsByAccountId(account.getId())).isEqualByComparingTo(expectedPaid);
    }

    @Test
    void anotherUsersCardOrAccountCannotReceivePayment() throws Exception {
        Account othersAccount = accounts.saveAndFlush(new Account(other, "Conta", AccountType.CASH,
                BigDecimal.ZERO, null, null));
        for (Long accountId : new Long[]{account.getId(), othersAccount.getId()}) {
            Long cardId = accountId.equals(account.getId()) ? othersCard.getId() : card.getId();
            mvc.perform(post(PAYMENT_URL, cardId, "2026-11").header("Authorization", bearer(owner))
                            .contentType("application/json").content("{\"accountId\":" + accountId
                                    + ",\"amount\":10.00,\"date\":\"2026-11-01\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
        assertThat(payments.sumPaymentsByAccountId(account.getId())).isZero();
        assertThat(payments.sumPaymentsByAccountId(othersAccount.getId())).isZero();
    }

    @Test
    void changingClosingDayKeepsExistingPurchaseInItsOriginalInvoice() {
        Long userId = owner.getId();
        var input = new TransactionRequest(TransactionType.EXPENSE, new BigDecimal("10.11"), expenseCategory.getId(),
                LocalDate.of(2026, 11, 10), null, null, null, card.getId(), 1);
        var original = transactionService.create(userId, input);

        creditCards.update(userId, card.getId(),
                new CreditCardRequest(card.getName(), card.getCreditLimit(), 5, card.getDueDay(), null, null));
        var later = transactionService.create(userId, input);

        assertThat(transactions.findById(original.id()).orElseThrow().getInvoiceReference()).isEqualTo(MONTH);
        assertThat(transactions.findById(later.id()).orElseThrow().getInvoiceReference())
                .isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(transactions.sumAmountByCreditCardIdAndInvoiceReference(card.getId(), MONTH))
                .isEqualByComparingTo("110.11");
    }

    @Test
    void transfersAndInvoicePaymentsChangeAccountBalanceButDoNotChangeDashboard() {
        Long userId = owner.getId();
        var incomeCategory = categories.saveAndFlush(new Category(owner, "Receita", TransactionType.INCOME, null, null));
        transactionService.create(userId, new TransactionRequest(TransactionType.INCOME, new BigDecimal("200.11"),
                incomeCategory.getId(), MONTH, null, null, account.getId(), null, null));
        var before = dashboard.getSummary(userId, Period.MONTH, MONTH, null, null, null, null);

        Account destination = newDestination();
        transfers.create(userId, transferTo(destination, "40.07"));
        invoices.payInvoice(userId, card, "2026-11",
                new InvoicePaymentRequest(account.getId(), new BigDecimal("15.04"), MONTH));

        var after = dashboard.getSummary(userId, Period.MONTH, MONTH, null, null, null, null);
        assertThat(after).isEqualTo(before);
        assertThat(after.totalIncome()).isEqualByComparingTo("200.11");
        assertThat(after.totalExpense()).isEqualByComparingTo("100.00");
        assertThat(after.balance()).isEqualByComparingTo("100.11");
        assertBalance(account, "145.00");
        assertBalance(destination, "40.07");
    }

    @Test
    void recalculatesBothAccountBalancesAfterEditsAndDeletions() {
        Long userId = owner.getId();
        Account destination = newDestination();
        var expense = transactionService.create(userId, accountExpense("10.11"));
        var transfer = transfers.create(userId, transferTo(destination, "20.23"));
        assertBalance(account, "-30.34");
        assertBalance(destination, "20.23");

        transactionService.update(userId, expense.id(), accountExpense("12.45"));
        assertBalance(account, "-32.68");
        transfers.update(userId, transfer.id(), transferTo(destination, "18.19"));
        assertBalance(account, "-30.64");
        assertBalance(destination, "18.19");

        transactionService.delete(userId, expense.id());
        assertBalance(account, "-18.19");
        transfers.delete(userId, transfer.id());
        assertBalance(account, "0.00");
        assertBalance(destination, "0.00");
    }

    private Account newDestination() {
        return accounts.saveAndFlush(new Account(owner, "Destino", AccountType.CASH, BigDecimal.ZERO, null, null));
    }

    private TransferRequest transferTo(Account destination, String amount) {
        return new TransferRequest(account.getId(), destination.getId(), new BigDecimal(amount), MONTH, null);
    }

    private TransactionRequest accountExpense(String amount) {
        return new TransactionRequest(TransactionType.EXPENSE, new BigDecimal(amount), expenseCategory.getId(),
                MONTH, null, null, account.getId(), null, null);
    }

    private void assertBalance(Account target, String expected) {
        assertThat(balances.getById(owner.getId(), target.getId()).balance()).isEqualByComparingTo(expected);
    }

    private void rejectDelete(User actor, Long cardId, String reference, InvoicePayment payment) throws Exception {
        mvc.perform(delete(PAYMENT_URL + "/{paymentId}", cardId, reference, payment.getId())
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
        assertThat(payments.findById(payment.getId())).isPresent();
        assertThat(payments.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), MONTH))
                .isEqualByComparingTo("15.00");
    }

    private InvoicePayment payment() {
        return payments.saveAndFlush(new InvoicePayment(owner, card, account, MONTH, new BigDecimal("15.00"), MONTH));
    }

    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user.getId()); }

    private CreditCard card(User user, String name) {
        return cards.saveAndFlush(new CreditCard(user, name, new BigDecimal("1000.00"), 10, 20, null, null));
    }

    private User user() {
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(new User(suffix, suffix + "@example.test", "Teste", null));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Barreira expirou");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
