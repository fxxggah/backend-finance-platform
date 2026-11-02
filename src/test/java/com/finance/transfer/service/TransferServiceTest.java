package com.finance.transfer.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.dto.PageResponse;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.transfer.dto.request.TransferRequest;
import com.finance.transfer.dto.response.TransferResponse;
import com.finance.transfer.entity.Transfer;
import com.finance.transfer.repository.TransferRepository;
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRANSFER_ID = 100L;
    private static final Long FROM_ACC_ID = 10L;
    private static final Long TO_ACC_ID = 20L;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferService transferService;

    private User user() {
        return new User("google-1", "teste@example.com", "Teste", null);
    }

    private Account account(Long id, boolean archived) {
        Account acc = new Account();
        acc.setId(id);
        acc.setUser(user());
        acc.setName("Conta " + id);
        acc.setColor("#000000");
        acc.setIcon("icon");
        acc.setArchived(archived);
        return acc;
    }

    private Transfer transfer(Long id) {
        Transfer t = new Transfer(user(), account(FROM_ACC_ID, false), account(TO_ACC_ID, false),
                new BigDecimal("100.00"), LocalDate.now(), "Descricao");
        t.setId(id);
        return t;
    }

    @Test
    @DisplayName("list: deve retornar lista paginada de transferências")
    void list_shouldReturnPagedTransfers() {
        PageRequest pageable = PageRequest.of(0, 10);
        Transfer t = transfer(TRANSFER_ID);
        when(transferRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(t), pageable, 1));

        PageResponse<TransferResponse> response = transferService.list(USER_ID, null, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("create: deve criar uma transferência quando contas forem válidas")
    void create_shouldCreateTransfer() {
        TransferRequest request = new TransferRequest(FROM_ACC_ID, TO_ACC_ID, new BigDecimal("50.00"), LocalDate.now(), "Teste");

        when(accountRepository.findByIdAndUserId(FROM_ACC_ID, USER_ID)).thenReturn(Optional.of(account(FROM_ACC_ID, false)));
        when(accountRepository.findByIdAndUserId(TO_ACC_ID, USER_ID)).thenReturn(Optional.of(account(TO_ACC_ID, false)));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> {
            Transfer saved = i.getArgument(0);
            saved.setId(TRANSFER_ID);
            return saved;
        });

        TransferResponse response = transferService.create(USER_ID, request);

        assertThat(response.id()).isEqualTo(TRANSFER_ID);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        verify(transferRepository).save(any(Transfer.class));
    }

    @Test
    @DisplayName("create: deve lançar erro se a conta de origem for igual à de destino")
    void create_shouldThrowException_whenAccountsAreTheSame() {
        TransferRequest request = new TransferRequest(FROM_ACC_ID, FROM_ACC_ID, new BigDecimal("50.00"), LocalDate.now(), "Teste");

        assertThatThrownBy(() -> transferService.create(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("não pode ser igual");
    }

    @Test
    @DisplayName("create: deve lançar erro se uma das contas estiver arquivada")
    void create_shouldThrowException_whenAccountIsArchived() {
        TransferRequest request = new TransferRequest(FROM_ACC_ID, TO_ACC_ID, new BigDecimal("50.00"), LocalDate.now(), "Teste");

        when(accountRepository.findByIdAndUserId(FROM_ACC_ID, USER_ID)).thenReturn(Optional.of(account(FROM_ACC_ID, true)));
        when(accountRepository.findByIdAndUserId(TO_ACC_ID, USER_ID)).thenReturn(Optional.of(account(TO_ACC_ID, false)));

        assertThatThrownBy(() -> transferService.create(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("arquivadas");
    }

    @Test
    @DisplayName("update: deve atualizar a transferência com sucesso")
    void update_shouldUpdateTransfer() {
        Transfer t = transfer(TRANSFER_ID);
        TransferRequest request = new TransferRequest(FROM_ACC_ID, TO_ACC_ID, new BigDecimal("200.00"), LocalDate.now(), "Atualizado");

        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.of(t));
        when(accountRepository.findByIdAndUserId(FROM_ACC_ID, USER_ID)).thenReturn(Optional.of(account(FROM_ACC_ID, false)));
        when(accountRepository.findByIdAndUserId(TO_ACC_ID, USER_ID)).thenReturn(Optional.of(account(TO_ACC_ID, false)));
        when(transferRepository.save(any(Transfer.class))).thenReturn(t);

        TransferResponse response = transferService.update(USER_ID, TRANSFER_ID, request);

        assertThat(response.amount()).isEqualByComparingTo("200.00");
        assertThat(t.getDescription()).isEqualTo("Atualizado");
    }

    @Test
    @DisplayName("delete: deve excluir a transferência com sucesso")
    void delete_shouldDeleteTransfer() {
        Transfer t = transfer(TRANSFER_ID);
        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.of(t));

        transferService.delete(USER_ID, TRANSFER_ID);

        verify(transferRepository).delete(t);
    }

    @Test
    @DisplayName("delete: deve lançar erro quando a transferência não for encontrada")
    void delete_shouldThrowException_whenNotFound() {
        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.delete(USER_ID, TRANSFER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}