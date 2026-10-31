package com.finance.user.repository;

import com.finance.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        savedUser = userRepository.saveAndFlush(
                new User("google-123", "joao@example.com", "João Silva", "https://img.example.com/joao.png"));
    }

    @Test
    @DisplayName("save: deve persistir usuário gerando id e campos de auditoria")
    void save_shouldPersistUserWithIdAndAuditFields() {
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update: deve atualizar updatedAt e manter createdAt")
    void update_shouldChangeUpdatedAtAndKeepCreatedAt() throws InterruptedException {
        entityManager.clear();
        User persisted = userRepository.findById(savedUser.getId()).orElseThrow();
        Instant createdAt = persisted.getCreatedAt();
        Instant updatedAt = persisted.getUpdatedAt();

        Thread.sleep(1100);

        persisted.setName("João Atualizado");
        userRepository.saveAndFlush(persisted);
        entityManager.clear();

        User reloaded = userRepository.findById(savedUser.getId()).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("João Atualizado");
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    @DisplayName("findByGoogleId: deve retornar o usuário quando existir")
    void findByGoogleId_shouldReturnUser_whenExists() {
        Optional<User> result = userRepository.findByGoogleId("google-123");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("joao@example.com");
    }

    @Test
    @DisplayName("findByGoogleId: deve retornar vazio quando não existir")
    void findByGoogleId_shouldReturnEmpty_whenNotExists() {
        assertThat(userRepository.findByGoogleId("inexistente")).isEmpty();
    }

    @Test
    @DisplayName("findByEmail: deve retornar o usuário quando existir")
    void findByEmail_shouldReturnUser_whenExists() {
        Optional<User> result = userRepository.findByEmail("joao@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getGoogleId()).isEqualTo("google-123");
    }

    @Test
    @DisplayName("findByEmail: deve retornar vazio quando não existir")
    void findByEmail_shouldReturnEmpty_whenNotExists() {
        assertThat(userRepository.findByEmail("nao@existe.com")).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail: deve retornar true quando o email existir")
    void existsByEmail_shouldReturnTrue_whenExists() {
        assertThat(userRepository.existsByEmail("joao@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail: deve retornar false quando o email não existir")
    void existsByEmail_shouldReturnFalse_whenNotExists() {
        assertThat(userRepository.existsByEmail("nao@existe.com")).isFalse();
    }

    @Test
    @DisplayName("save: deve falhar ao inserir email duplicado")
    void save_shouldFail_whenEmailIsDuplicated() {
        User duplicated = new User("google-999", "joao@example.com", "Outro", null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save: deve falhar ao inserir googleId duplicado")
    void save_shouldFail_whenGoogleIdIsDuplicated() {
        User duplicated = new User("google-123", "outro@example.com", "Outro", null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicated))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("save: deve falhar quando campos obrigatórios forem nulos")
    void save_shouldFail_whenRequiredFieldIsNull() {
        User invalid = new User("google-555", null, "Sem Email", null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(invalid))
                .isInstanceOf(Exception.class);
    }
}