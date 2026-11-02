package com.finance.transfer.repository;

import com.finance.account.entity.Account;
import com.finance.common.enums.AccountType;
import com.finance.transfer.entity.Transfer;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;
    private Account ownerAccount1;
    private Account ownerAccount2;
    private Account otherAccount;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        otherUser = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));

        ownerAccount1 = persistAccount(owner, "Conta Corrente");
        ownerAccount2 = persistAccount(owner, "Poupança");
        otherAccount = persistAccount(otherUser, "Conta de Outro");
    }

    private Account persistAccount(User user, String name) {
        Account account = new Account(
                user,
                name,
                AccountType.BANK,
                BigDecimal.ZERO,
                "#000000",
                "icon"
        );
        entityManager.persist(account);
        entityManager.flush();
        return account;
    }

    private Transfer persistTransfer(User user, Account from, Account to, BigDecimal amount, LocalDate date) {
        Transfer transfer = new Transfer(user, from, to, amount, date, "Transferência teste");
        return transferRepository.saveAndFlush(transfer);
    }

    // ---------- persistência (save) ----------

    @Test
    @DisplayName("save: deve persistir transferência com id, auditoria e relacionamentos")
    void save_shouldPersistTransfer() {
        Transfer transfer = new Transfer(owner, ownerAccount1, ownerAccount2, new BigDecimal("150.00"), LocalDate.now(), "Pagamento");

        Transfer saved = transferRepository.saveAndFlush(transfer);
        entityManager.clear();

        Transfer reloaded = transferRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("150.00");
        assertThat(reloaded.getDescription()).isEqualTo("Pagamento");
        assertThat(reloaded.getFromAccount().getId()).isEqualTo(ownerAccount1.getId());
        assertThat(reloaded.getToAccount().getId()).isEqualTo(ownerAccount2.getId());
        assertThat(reloaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve falhar quando dados obrigatórios forem nulos")
    void save_shouldFail_whenRequiredFieldsAreNull() {
        Transfer invalid = new Transfer(owner, ownerAccount1, ownerAccount2, null, null, null);

        assertThatThrownBy(() -> transferRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar a transferência quando for do usuário")
    void findByIdAndUserId_shouldReturnTransfer_whenOwned() {
        Transfer transfer = persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());

        Optional<Transfer> result = transferRepository.findByIdAndUserId(transfer.getId(), owner.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(transfer.getId());
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando for de outro usuário ou não existir")
    void findByIdAndUserId_shouldReturnEmpty_whenNotOwnedOrNotExists() {
        Transfer transfer = persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());

        assertThat(transferRepository.findByIdAndUserId(transfer.getId(), otherUser.getId())).isEmpty();
        assertThat(transferRepository.findByIdAndUserId(999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByUserId ----------

    @Test
    @DisplayName("findAllByUserId: deve retornar página com transferências apenas do usuário")
    void findAllByUserId_shouldReturnOnlyOwnedTransfers() {
        persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());
        persistTransfer(owner, ownerAccount2, ownerAccount1, BigDecimal.ONE, LocalDate.now());
        persistTransfer(otherUser, otherAccount, otherAccount, BigDecimal.TEN, LocalDate.now());

        Page<Transfer> page = transferRepository.findAllByUserId(owner.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Transfer::getUser).extracting(User::getId)
                .containsOnly(owner.getId());
    }

    // ---------- existsByIdAndUserId ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente se a transferência for do usuário")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        Transfer transfer = persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());

        assertThat(transferRepository.existsByIdAndUserId(transfer.getId(), owner.getId())).isTrue();
        assertThat(transferRepository.existsByIdAndUserId(transfer.getId(), otherUser.getId())).isFalse();
        assertThat(transferRepository.existsByIdAndUserId(999L, owner.getId())).isFalse();
    }

    // ---------- existsByFromAccountIdOrToAccountId ----------

    @Test
    @DisplayName("existsByFromAccountIdOrToAccountId: deve retornar true se a conta for origem ou destino de alguma transferência")
    void existsByFromAccountIdOrToAccountId_shouldReturnTrueIfAccountIsInvolved() {
        persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());

        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(ownerAccount1.getId(), ownerAccount1.getId())).isTrue();
        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(ownerAccount2.getId(), ownerAccount2.getId())).isTrue();
        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(otherAccount.getId(), otherAccount.getId())).isFalse();
    }

    // ---------- sumAmountSentByAccountId ----------

    @Test
    @DisplayName("sumAmountSentByAccountId: deve retornar a soma correta dos valores enviados e 0 se não houver")
    void sumAmountSentByAccountId_shouldReturnSum() {
        persistTransfer(owner, ownerAccount1, ownerAccount2, new BigDecimal("50.00"), LocalDate.now());
        persistTransfer(owner, ownerAccount1, otherAccount, new BigDecimal("25.50"), LocalDate.now());

        BigDecimal sumSent = transferRepository.sumAmountSentByAccountId(ownerAccount1.getId());
        BigDecimal sumSentEmpty = transferRepository.sumAmountSentByAccountId(otherAccount.getId());

        assertThat(sumSent).isEqualByComparingTo("75.50");
        assertThat(sumSentEmpty).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- sumAmountReceivedByAccountId ----------

    @Test
    @DisplayName("sumAmountReceivedByAccountId: deve retornar a soma correta dos valores recebidos e 0 se não houver")
    void sumAmountReceivedByAccountId_shouldReturnSum() {
        persistTransfer(owner, ownerAccount1, ownerAccount2, new BigDecimal("100.00"), LocalDate.now());
        persistTransfer(owner, otherAccount, ownerAccount2, new BigDecimal("50.00"), LocalDate.now());

        BigDecimal sumReceived = transferRepository.sumAmountReceivedByAccountId(ownerAccount2.getId());
        BigDecimal sumReceivedEmpty = transferRepository.sumAmountReceivedByAccountId(ownerAccount1.getId());

        assertThat(sumReceived).isEqualByComparingTo("150.00");
        assertThat(sumReceivedEmpty).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todas as transferências do usuário e preservar as de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserTransfers() {
        persistTransfer(owner, ownerAccount1, ownerAccount2, BigDecimal.TEN, LocalDate.now());
        persistTransfer(owner, ownerAccount2, ownerAccount1, BigDecimal.TEN, LocalDate.now());
        persistTransfer(otherUser, otherAccount, otherAccount, BigDecimal.TEN, LocalDate.now());

        transferRepository.deleteAllByUserId(owner.getId());
        transferRepository.flush();
        entityManager.clear();

        Page<Transfer> ownerTransfers = transferRepository.findAllByUserId(owner.getId(), PageRequest.of(0, 10));
        Page<Transfer> otherTransfers = transferRepository.findAllByUserId(otherUser.getId(), PageRequest.of(0, 10));

        assertThat(ownerTransfers.getTotalElements()).isZero();
        assertThat(otherTransfers.getTotalElements()).isEqualTo(1);
    }
}