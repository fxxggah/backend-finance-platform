package com.finance.account.repository;

import com.finance.account.entity.Account;
import com.finance.common.enums.AccountType;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest {

    private static final AccountType TYPE = AccountType.values()[0];

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(new User("google-owner", "owner@example.com", "Owner", null));
        other = userRepository.saveAndFlush(new User("google-other", "other@example.com", "Other", null));
    }

    private Account persist(User user, String name, boolean archived) {
        Account account = new Account(user, name, TYPE, new BigDecimal("100.00"), "#3B82F6", "wallet");
        account.setArchived(archived);
        return accountRepository.saveAndFlush(account);
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir conta com id, auditoria e valores padrão")
    void save_shouldPersistAccountWithDefaults() {
        Account account = accountRepository.saveAndFlush(new Account(owner, "Nubank", TYPE, null, null, null));
        entityManager.clear();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getName()).isEqualTo("Nubank");
        assertThat(reloaded.getType()).isEqualTo(TYPE);
        assertThat(reloaded.getInitialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.getColor()).isNull();
        assertThat(reloaded.getIcon()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve preservar a precisão de duas casas decimais do saldo inicial")
    void save_shouldKeepInitialBalancePrecision() {
        Account account = accountRepository.saveAndFlush(
                new Account(owner, "Carteira", TYPE, new BigDecimal("1234.56"), "#3B82F6", "wallet"));
        entityManager.clear();

        Account reloaded = accountRepository.findById(account.getId()).orElseThrow();

        assertThat(reloaded.getInitialBalance()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getColor()).isEqualTo("#3B82F6");
        assertThat(reloaded.getIcon()).isEqualTo("wallet");
    }

    @Test
    @DisplayName("save: deve falhar quando o nome (obrigatório) for nulo")
    void save_shouldFail_whenNameIsNull() {
        Account invalid = new Account(owner, null, TYPE, BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar a conta quando pertence ao usuário")
    void findByIdAndUserId_shouldReturnAccount_whenOwned() {
        Account account = persist(owner, "Nubank", false);

        Optional<Account> result = accountRepository.findByIdAndUserId(account.getId(), owner.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Nubank");
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a conta é de outro usuário")
    void findByIdAndUserId_shouldReturnEmpty_whenOwnedByAnotherUser() {
        Account account = persist(owner, "Nubank", false);

        assertThat(accountRepository.findByIdAndUserId(account.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando a conta não existe")
    void findByIdAndUserId_shouldReturnEmpty_whenNotExists() {
        assertThat(accountRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByUserId ----------

    @Test
    @DisplayName("findAllByUserId: deve retornar contas ativas e arquivadas somente do usuário")
    void findAllByUserId_shouldReturnOnlyUserAccounts() {
        persist(owner, "Ativa", false);
        persist(owner, "Arquivada", true);
        persist(other, "De outro", false);

        List<Account> result = accountRepository.findAllByUserId(owner.getId());

        assertThat(result).extracting(Account::getName).containsExactlyInAnyOrder("Ativa", "Arquivada");
    }

    @Test
    @DisplayName("findAllByUserId: deve retornar lista vazia quando o usuário não tem contas")
    void findAllByUserId_shouldReturnEmpty_whenUserHasNoAccounts() {
        assertThat(accountRepository.findAllByUserId(owner.getId())).isEmpty();
    }

    // ---------- findAllByUserIdAndArchivedFalse ----------

    @Test
    @DisplayName("findAllByUserIdAndArchivedFalse: deve retornar somente contas ativas do usuário")
    void findAllByUserIdAndArchivedFalse_shouldReturnOnlyActiveAccounts() {
        persist(owner, "Ativa 1", false);
        persist(owner, "Ativa 2", false);
        persist(owner, "Arquivada", true);
        persist(other, "De outro", false);

        List<Account> result = accountRepository.findAllByUserIdAndArchivedFalse(owner.getId());

        assertThat(result).extracting(Account::getName).containsExactlyInAnyOrder("Ativa 1", "Ativa 2");
    }

    // ---------- existsByIdAndUserId ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono da conta")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        Account account = persist(owner, "Nubank", false);

        assertThat(accountRepository.existsByIdAndUserId(account.getId(), owner.getId())).isTrue();
        assertThat(accountRepository.existsByIdAndUserId(account.getId(), other.getId())).isFalse();
        assertThat(accountRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCase ----------

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve ignorar maiúsculas/minúsculas")
    void existsByUserIdAndNameIgnoreCase_shouldIgnoreCase() {
        persist(owner, "Nubank", false);

        assertThat(accountRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Nubank")).isTrue();
        assertThat(accountRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "nubank")).isTrue();
        assertThat(accountRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "NUBANK")).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve retornar false para nome inexistente")
    void existsByUserIdAndNameIgnoreCase_shouldReturnFalse_whenNameNotExists() {
        persist(owner, "Nubank", false);

        assertThat(accountRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Inter")).isFalse();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve considerar apenas contas do próprio usuário")
    void existsByUserIdAndNameIgnoreCase_shouldNotSeeOtherUsersAccounts() {
        persist(other, "Nubank", false);

        assertThat(accountRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Nubank")).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCaseAndIdNot ----------

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndIdNot: deve ignorar a própria conta")
    void existsByNameAndIdNot_shouldReturnFalse_whenOnlyTheSameAccountHasTheName() {
        Account account = persist(owner, "Nubank", false);

        assertThat(accountRepository
                .existsByUserIdAndNameIgnoreCaseAndIdNot(owner.getId(), "nubank", account.getId())).isFalse();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndIdNot: deve retornar true quando outra conta tem o mesmo nome")
    void existsByNameAndIdNot_shouldReturnTrue_whenAnotherAccountHasTheName() {
        persist(owner, "Nubank", false);
        Account second = persist(owner, "Inter", false);

        assertThat(accountRepository
                .existsByUserIdAndNameIgnoreCaseAndIdNot(owner.getId(), "NUBANK", second.getId())).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCaseAndIdNot: deve ignorar contas de outros usuários")
    void existsByNameAndIdNot_shouldIgnoreOtherUsersAccounts() {
        persist(other, "Nubank", false);
        Account mine = persist(owner, "Inter", false);

        assertThat(accountRepository
                .existsByUserIdAndNameIgnoreCaseAndIdNot(owner.getId(), "Nubank", mine.getId())).isFalse();
    }

    // ---------- countByUserIdAndArchivedFalse ----------

    @Test
    @DisplayName("countByUserIdAndArchivedFalse: deve contar somente contas ativas do usuário")
    void countByUserIdAndArchivedFalse_shouldCountOnlyActiveAccountsOfUser() {
        persist(owner, "Ativa 1", false);
        persist(owner, "Ativa 2", false);
        persist(owner, "Arquivada", true);
        persist(other, "De outro", false);

        assertThat(accountRepository.countByUserIdAndArchivedFalse(owner.getId())).isEqualTo(2);
        assertThat(accountRepository.countByUserIdAndArchivedFalse(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("countByUserIdAndArchivedFalse: deve retornar zero quando não há contas ativas")
    void countByUserIdAndArchivedFalse_shouldReturnZero_whenNoActiveAccounts() {
        persist(owner, "Arquivada", true);

        assertThat(accountRepository.countByUserIdAndArchivedFalse(owner.getId())).isZero();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todas as contas do usuário e preservar as de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserAccounts() {
        persist(owner, "Ativa", false);
        persist(owner, "Arquivada", true);
        persist(other, "De outro", false);

        accountRepository.deleteAllByUserId(owner.getId());
        accountRepository.flush();
        entityManager.clear();

        assertThat(accountRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(accountRepository.findAllByUserId(other.getId()))
                .extracting(Account::getName).containsExactly("De outro");
    }
}