package com.finance.creditcard.repository;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.enums.AccountType;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.entity.InvoicePayment;
import com.finance.user.entity.User;
import com.finance.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvoicePaymentRepositoryTest {

    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 10, 20);

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private InvoicePaymentRepository paymentRepository;

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;
    private CreditCard card;
    private CreditCard card2;
    private Account account;
    private Account account2;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));
        card = creditCardRepository.saveAndFlush(newCard(owner, "Nubank"));
        card2 = creditCardRepository.saveAndFlush(newCard(owner, "Inter"));
        account = accountRepository.saveAndFlush(newAccount(owner, "Conta corrente"));
        account2 = accountRepository.saveAndFlush(newAccount(owner, "Poupança"));
    }

    private CreditCard newCard(User user, String name) {
        return new CreditCard(user, name, new BigDecimal("5000.00"), 10, 17, "#8A05BE", "credit-card");
    }

    // TODO: ajuste para o construtor real de Account (esta assinatura é uma suposição).
    private Account newAccount(User user, String name) {
        return new Account(
                user,
                name,
                AccountType.BANK,
                BigDecimal.ZERO,
                "#3B82F6",
                "bank"
        );
    }
    private InvoicePayment persist(User user, CreditCard creditCard, Account acc, LocalDate reference, String amount) {
        return paymentRepository.saveAndFlush(
                new InvoicePayment(user, creditCard, acc, reference, new BigDecimal(amount), PAY_DATE));
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir pagamento com id, auditoria e relacionamentos")
    void save_shouldPersistPayment() {
        InvoicePayment payment = persist(owner, card, account, OCT, "300.00");
        entityManager.clear();

        InvoicePayment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("300.00");
        assertThat(reloaded.getDate()).isEqualTo(PAY_DATE);
        assertThat(reloaded.getInvoiceReference()).isEqualTo(OCT);
        assertThat(reloaded.getCreditCard().getId()).isEqualTo(card.getId());
        assertThat(reloaded.getAccount().getId()).isEqualTo(account.getId());
        assertThat(reloaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar o pagamento somente para o dono")
    void findByIdAndUserId_shouldReturnOnlyForOwner() {
        InvoicePayment payment = persist(owner, card, account, OCT, "100.00");

        assertThat(paymentRepository.findByIdAndUserId(payment.getId(), owner.getId())).isPresent();
        assertThat(paymentRepository.findByIdAndUserId(payment.getId(), other.getId())).isEmpty();
        assertThat(paymentRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByUserIdAndCreditCardIdAndInvoiceReference ----------

    @Test
    @DisplayName("findAllBy...InvoiceReference: deve filtrar por usuário, cartão e fatura")
    void findAllByUserCardAndReference_shouldFilterCorrectly() {
        persist(owner, card, account, OCT, "100.00");
        persist(owner, card, account2, OCT, "50.00");
        persist(owner, card, account, NOV, "70.00");
        persist(owner, card2, account, OCT, "30.00");

        List<InvoicePayment> result = paymentRepository
                .findAllByUserIdAndCreditCardIdAndInvoiceReference(owner.getId(), card.getId(), OCT);

        assertThat(result).extracting(InvoicePayment::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("findAllBy...InvoiceReference: deve retornar vazio para outro usuário ou fatura sem pagamentos")
    void findAllByUserCardAndReference_shouldReturnEmpty() {
        persist(owner, card, account, OCT, "100.00");

        assertThat(paymentRepository.findAllByUserIdAndCreditCardIdAndInvoiceReference(
                other.getId(), card.getId(), OCT)).isEmpty();
        assertThat(paymentRepository.findAllByUserIdAndCreditCardIdAndInvoiceReference(
                owner.getId(), card.getId(), NOV)).isEmpty();
    }

    // ---------- exists ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        InvoicePayment payment = persist(owner, card, account, OCT, "100.00");

        assertThat(paymentRepository.existsByIdAndUserId(payment.getId(), owner.getId())).isTrue();
        assertThat(paymentRepository.existsByIdAndUserId(payment.getId(), other.getId())).isFalse();
        assertThat(paymentRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    @Test
    @DisplayName("existsByAccountId: deve retornar true somente para conta com pagamentos")
    void existsByAccountId_shouldReturnTrueOnlyWhenAccountHasPayments() {
        persist(owner, card, account, OCT, "100.00");

        assertThat(paymentRepository.existsByAccountId(account.getId())).isTrue();
        assertThat(paymentRepository.existsByAccountId(account2.getId())).isFalse();
    }

    @Test
    @DisplayName("existsByCreditCardId: deve retornar true somente para cartão com pagamentos")
    void existsByCreditCardId_shouldReturnTrueOnlyWhenCardHasPayments() {
        persist(owner, card, account, OCT, "100.00");

        assertThat(paymentRepository.existsByCreditCardId(card.getId())).isTrue();
        assertThat(paymentRepository.existsByCreditCardId(card2.getId())).isFalse();
    }

    // ---------- somas ----------

    @Test
    @DisplayName("sumTotalPaymentsByCreditCardId: deve somar todos os pagamentos do cartão")
    void sumTotalPaymentsByCreditCardId_shouldSumAllPaymentsOfCard() {
        persist(owner, card, account, OCT, "100.00");
        persist(owner, card, account, NOV, "250.50");
        persist(owner, card2, account, OCT, "999.00");

        assertThat(paymentRepository.sumTotalPaymentsByCreditCardId(card.getId())).isEqualByComparingTo("350.50");
    }

    @Test
    @DisplayName("sumTotalPaymentsByCreditCardId: deve retornar zero quando não há pagamentos")
    void sumTotalPaymentsByCreditCardId_shouldReturnZero_whenNoPayments() {
        assertThat(paymentRepository.sumTotalPaymentsByCreditCardId(card.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sumPaymentsByCreditCardIdAndInvoiceReference: deve somar apenas a fatura informada")
    void sumPaymentsByCardAndReference_shouldSumOnlyThatInvoice() {
        persist(owner, card, account, OCT, "100.00");
        persist(owner, card, account2, OCT, "40.00");
        persist(owner, card, account, NOV, "500.00");
        persist(owner, card2, account, OCT, "999.00");

        assertThat(paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), OCT))
                .isEqualByComparingTo("140.00");
        assertThat(paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), NOV))
                .isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("sumPaymentsByCreditCardIdAndInvoiceReference: deve retornar zero para fatura sem pagamentos")
    void sumPaymentsByCardAndReference_shouldReturnZero_whenNoPayments() {
        assertThat(paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), OCT))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sumPaymentsByAccountId: deve somar apenas os pagamentos saídos da conta")
    void sumPaymentsByAccountId_shouldSumOnlyAccountPayments() {
        persist(owner, card, account, OCT, "100.00");
        persist(owner, card2, account, OCT, "60.00");
        persist(owner, card, account2, OCT, "999.00");

        assertThat(paymentRepository.sumPaymentsByAccountId(account.getId())).isEqualByComparingTo("160.00");
        assertThat(paymentRepository.sumPaymentsByAccountId(account2.getId())).isEqualByComparingTo("999.00");
    }

    @Test
    @DisplayName("sumPaymentsByAccountId: deve retornar zero quando a conta não tem pagamentos")
    void sumPaymentsByAccountId_shouldReturnZero_whenNoPayments() {
        assertThat(paymentRepository.sumPaymentsByAccountId(account.getId())).isEqualByComparingTo("0");
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar os pagamentos do usuário e preservar os de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserPayments() {
        CreditCard otherCard = creditCardRepository.saveAndFlush(newCard(other, "Do outro"));
        Account otherAccount = accountRepository.saveAndFlush(newAccount(other, "Conta do outro"));
        persist(owner, card, account, OCT, "100.00");
        persist(owner, card, account, NOV, "50.00");
        InvoicePayment others = persist(other, otherCard, otherAccount, OCT, "70.00");

        paymentRepository.deleteAllByUserId(owner.getId());
        paymentRepository.flush();
        entityManager.clear();

        assertThat(paymentRepository.existsByCreditCardId(card.getId())).isFalse();
        assertThat(paymentRepository.findById(others.getId())).isPresent();
    }
}