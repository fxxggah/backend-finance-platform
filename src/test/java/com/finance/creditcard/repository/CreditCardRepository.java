package com.finance.creditcard.repository;

import com.finance.creditcard.entity.CreditCard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditCardRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private CreditCardRepository creditCardRepository;

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

    private CreditCard persist(User user, String name, boolean archived) {
        CreditCard card = new CreditCard(user, name, new BigDecimal("5000.00"), 10, 17, "#8A05BE", "credit-card");
        card.setArchived(archived);
        return creditCardRepository.saveAndFlush(card);
    }

    // ---------- persistência ----------

    @Test
    @DisplayName("save: deve persistir cartão com id, auditoria e valores padrão")
    void save_shouldPersistCardWithDefaults() {
        CreditCard card = creditCardRepository.saveAndFlush(
                new CreditCard(owner, "Nubank", new BigDecimal("5000.00"), 10, 17, null, null));
        entityManager.clear();

        CreditCard reloaded = creditCardRepository.findById(card.getId()).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getName()).isEqualTo("Nubank");
        assertThat(reloaded.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(reloaded.getClosingDay()).isEqualTo(10);
        assertThat(reloaded.getDueDay()).isEqualTo(17);
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.getColor()).isNull();
        assertThat(reloaded.getIcon()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: deve preservar cor, ícone e arquivamento")
    void save_shouldKeepColorIconAndArchived() {
        CreditCard card = persist(owner, "Nubank", true);
        entityManager.clear();

        CreditCard reloaded = creditCardRepository.findById(card.getId()).orElseThrow();

        assertThat(reloaded.getColor()).isEqualTo("#8A05BE");
        assertThat(reloaded.getIcon()).isEqualTo("credit-card");
        assertThat(reloaded.isArchived()).isTrue();
    }

    @Test
    @DisplayName("save: deve falhar quando o nome (obrigatório) for nulo")
    void save_shouldFail_whenNameIsNull() {
        CreditCard invalid = new CreditCard(owner, null, new BigDecimal("5000.00"), 10, 17, null, null);

        assertThatThrownBy(() -> creditCardRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o limite (obrigatório) for nulo")
    void save_shouldFail_whenCreditLimitIsNull() {
        CreditCard invalid = new CreditCard(owner, "Nubank", null, 10, 17, null, null);

        assertThatThrownBy(() -> creditCardRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve falhar quando o dia de fechamento ou vencimento for nulo")
    void save_shouldFail_whenClosingOrDueDayIsNull() {
        CreditCard noClosing = new CreditCard(owner, "A", new BigDecimal("100.00"), null, 17, null, null);
        assertThatThrownBy(() -> creditCardRepository.saveAndFlush(noClosing))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("save: deve permitir o mesmo nome para usuários diferentes")
    void save_shouldAllowSameNameForDifferentUsers() {
        CreditCard a = persist(owner, "Nubank", false);
        CreditCard b = persist(other, "Nubank", false);

        assertThat(a.getId()).isNotNull();
        assertThat(b.getId()).isNotNull();
    }

    // ---------- findByIdAndUserId ----------

    @Test
    @DisplayName("findByIdAndUserId: deve retornar o cartão do usuário, inclusive arquivado")
    void findByIdAndUserId_shouldReturnCard_whenOwned() {
        CreditCard active = persist(owner, "Nubank", false);
        CreditCard archived = persist(owner, "Antigo", true);

        assertThat(creditCardRepository.findByIdAndUserId(active.getId(), owner.getId())).isPresent();
        assertThat(creditCardRepository.findByIdAndUserId(archived.getId(), owner.getId())).isPresent();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando o cartão é de outro usuário")
    void findByIdAndUserId_shouldReturnEmpty_whenOwnedByAnotherUser() {
        CreditCard card = persist(owner, "Nubank", false);

        assertThat(creditCardRepository.findByIdAndUserId(card.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId: deve retornar vazio quando o cartão não existe")
    void findByIdAndUserId_shouldReturnEmpty_whenNotExists() {
        assertThat(creditCardRepository.findByIdAndUserId(999_999L, owner.getId())).isEmpty();
    }

    // ---------- findAllByUserId ----------

    @Test
    @DisplayName("findAllByUserId: deve retornar todos os cartões do usuário, inclusive arquivados")
    void findAllByUserId_shouldReturnAllOfUser() {
        persist(owner, "Nubank", false);
        persist(owner, "Antigo", true);
        persist(other, "De outro", false);

        List<CreditCard> result = creditCardRepository.findAllByUserId(owner.getId());

        assertThat(result).extracting(CreditCard::getName).containsExactlyInAnyOrder("Nubank", "Antigo");
    }

    // ---------- findAllByUserIdAndArchivedFalse ----------

    @Test
    @DisplayName("findAllByUserIdAndArchivedFalse: deve retornar somente cartões ativos do usuário")
    void findAllByUserIdAndArchivedFalse_shouldReturnOnlyActiveOfUser() {
        persist(owner, "Nubank", false);
        persist(owner, "Inter", false);
        persist(owner, "Antigo", true);
        persist(other, "De outro", false);

        List<CreditCard> result = creditCardRepository.findAllByUserIdAndArchivedFalse(owner.getId());

        assertThat(result).extracting(CreditCard::getName).containsExactlyInAnyOrder("Nubank", "Inter");
    }

    @Test
    @DisplayName("findAllByUserIdAndArchivedFalse: deve retornar lista vazia quando o usuário não tem cartões")
    void findAllByUserIdAndArchivedFalse_shouldReturnEmpty_whenUserHasNoCards() {
        assertThat(creditCardRepository.findAllByUserIdAndArchivedFalse(owner.getId())).isEmpty();
    }

    // ---------- existsByIdAndUserId ----------

    @Test
    @DisplayName("existsByIdAndUserId: deve retornar true somente para o dono, inclusive se arquivado")
    void existsByIdAndUserId_shouldReturnTrueOnlyForOwner() {
        CreditCard active = persist(owner, "Nubank", false);
        CreditCard archived = persist(owner, "Antigo", true);

        assertThat(creditCardRepository.existsByIdAndUserId(active.getId(), owner.getId())).isTrue();
        assertThat(creditCardRepository.existsByIdAndUserId(archived.getId(), owner.getId())).isTrue();
        assertThat(creditCardRepository.existsByIdAndUserId(active.getId(), other.getId())).isFalse();
        assertThat(creditCardRepository.existsByIdAndUserId(999_999L, owner.getId())).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCase ----------

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve ignorar maiúsculas/minúsculas")
    void existsByName_shouldIgnoreCase() {
        persist(owner, "Nubank", false);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Nubank")).isTrue();
        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "NUBANK")).isTrue();
        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "nubank")).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve considerar cartões arquivados")
    void existsByName_shouldIncludeArchived() {
        persist(owner, "Antigo", true);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Antigo")).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndNameIgnoreCase: deve retornar false para nome inexistente ou de outro usuário")
    void existsByName_shouldReturnFalse_whenNotExistsOrOtherUser() {
        persist(other, "Nubank", false);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Nubank")).isFalse();
        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCase(owner.getId(), "Inexistente")).isFalse();
    }

    // ---------- existsByUserIdAndNameIgnoreCaseAndIdNot ----------

    @Test
    @DisplayName("existsBy...AndIdNot: deve ignorar o próprio cartão")
    void existsByNameAndIdNot_shouldReturnFalse_whenOnlyTheSameCardHasTheName() {
        CreditCard card = persist(owner, "Nubank", false);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(
                owner.getId(), "nubank", card.getId())).isFalse();
    }

    @Test
    @DisplayName("existsBy...AndIdNot: deve retornar true quando outro cartão do usuário tem o mesmo nome")
    void existsByNameAndIdNot_shouldReturnTrue_whenAnotherCardHasTheName() {
        persist(owner, "Nubank", false);
        CreditCard second = persist(owner, "Inter", false);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(
                owner.getId(), "NUBANK", second.getId())).isTrue();
    }

    @Test
    @DisplayName("existsBy...AndIdNot: deve ignorar cartões de outros usuários")
    void existsByNameAndIdNot_shouldIgnoreOtherUsers() {
        persist(other, "Nubank", false);
        CreditCard mine = persist(owner, "Inter", false);

        assertThat(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(
                owner.getId(), "Nubank", mine.getId())).isFalse();
    }

    // ---------- deleteAllByUserId ----------

    @Test
    @DisplayName("deleteAllByUserId: deve apagar todos os cartões do usuário e preservar os de outros")
    void deleteAllByUserId_shouldDeleteOnlyUserCards() {
        persist(owner, "Nubank", false);
        persist(owner, "Antigo", true);
        persist(other, "De outro", false);

        creditCardRepository.deleteAllByUserId(owner.getId());
        creditCardRepository.flush();
        entityManager.clear();

        assertThat(creditCardRepository.findAllByUserId(owner.getId())).isEmpty();
        assertThat(creditCardRepository.findAllByUserId(other.getId()))
                .extracting(CreditCard::getName).containsExactly("De outro");
    }
}