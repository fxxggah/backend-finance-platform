package com.finance.transfer.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.dto.PageResponse;
import com.finance.common.enums.AccountType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.DeleteSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class TransferServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRANSFER_ID = 100L;
    private static final Long FROM_ACCOUNT_ID = 5L;
    private static final Long TO_ACCOUNT_ID = 6L;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);
    private static final BigDecimal AMOUNT = new BigDecimal("150.00");

    private static final String TRANSFER_NOT_FOUND = "Transferência não encontrada.";
    private static final String ACCOUNT_NOT_FOUND = "Conta não encontrada.";
    private static final String SAME_ACCOUNT_ERROR = "A conta de origem não pode ser igual à conta de destino.";
    private static final String ARCHIVED_ACCOUNT_ERROR = "Não é possível realizar transferências envolvendo contas arquivadas.";

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferService transferService;

    // ---------- helpers ----------

    private User user() {
        User user = new User("google-1", "joao@example.com", "João", null);
        user.setId(USER_ID);
        return user;
    }

    private Account account(Long id, String name, boolean archived) {
        Account account = new Account(user(), name, AccountType.BANK, BigDecimal.ZERO, "#1E90FF", "bank-icon");
        account.setId(id);
        account.setArchived(archived);
        return account;
    }

    private Transfer transfer(Long id, Account fromAccount, Account toAccount) {
        Transfer transfer = new Transfer(user(), fromAccount, toAccount, AMOUNT, DATE, "Transferência para poupança");
        transfer.setId(id);
        return transfer;
    }

    private TransferRequest request(Long fromAccountId, Long toAccountId, BigDecimal amount, LocalDate date, String description) {
        return new TransferRequest(fromAccountId, toAccountId, amount, date, description);
    }

    private TransferRequest defaultRequest() {
        return request(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, AMOUNT, DATE, "Transferência PIX");
    }

    private void stubFindOwnedTransfer(Transfer transfer) {
        when(transferRepository.findByIdAndUserId(transfer.getId(), USER_ID)).thenReturn(Optional.of(transfer));
    }

    private void stubPage(Transfer... transfers) {
        when(transferRepository.findAll(ArgumentMatchers.<Specification<Transfer>>any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(transfers), invocation.getArgument(1), transfers.length));
    }

    private Transfer captureSavedTransfer() {
        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: deve retornar a página de transferências convertidas para response")
    void list_shouldReturnMappedPage() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        Account to = account(TO_ACCOUNT_ID, "Poupança", false);
        Transfer transfer = transfer(TRANSFER_ID, from, to);
        stubPage(transfer);

        PageResponse<TransferResponse> response = transferService.list(USER_ID, null, null, null, PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        TransferResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo(TRANSFER_ID);
        assertThat(item.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(item.date()).isEqualTo(DATE);
        assertThat(item.description()).isEqualTo("Transferência para poupança");
        assertThat(item.fromAccount().id()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(item.fromAccount().name()).isEqualTo("Conta Corrente");
        assertThat(item.toAccount().id()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(item.toAccount().name()).isEqualTo("Poupança");
    }

    @Test
    @DisplayName("list: deve retornar página vazia quando não houver transferências")
    void list_shouldReturnEmptyPage_whenNoTransfersFound() {
        stubPage();

        PageResponse<TransferResponse> response = transferService.list(USER_ID, null, null, null, PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    // ---------- findById ----------

    @Test
    @DisplayName("findById: deve retornar a transferência do usuário com os resumos das contas")
    void findById_shouldReturnTransfer() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        Account to = account(TO_ACCOUNT_ID, "Poupança", false);
        stubFindOwnedTransfer(transfer(TRANSFER_ID, from, to));

        TransferResponse response = transferService.findById(USER_ID, TRANSFER_ID);

        assertThat(response.id()).isEqualTo(TRANSFER_ID);
        assertThat(response.fromAccount().id()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(response.toAccount().id()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(response.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.description()).isEqualTo("Transferência para poupança");
    }

    @Test
    @DisplayName("findById: deve lançar ResourceNotFoundException quando a transferência não existir ou for de outro usuário")
    void findById_shouldThrowNotFound_whenTransferDoesNotExistOrNotOwned() {
        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.findById(USER_ID, TRANSFER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TRANSFER_NOT_FOUND);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: deve criar e persistir uma transferência com sucesso")
    void create_shouldCreateTransferSuccessfully() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        Account to = account(TO_ACCOUNT_ID, "Poupança", false);

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(to));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer saved = invocation.getArgument(0);
            saved.setId(TRANSFER_ID);
            return saved;
        });

        TransferResponse response = transferService.create(USER_ID, defaultRequest());

        Transfer persisted = captureSavedTransfer();
        assertThat(persisted.getUser()).isEqualTo(from.getUser());
        assertThat(persisted.getFromAccount()).isSameAs(from);
        assertThat(persisted.getToAccount()).isSameAs(to);
        assertThat(persisted.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(persisted.getDate()).isEqualTo(DATE);
        assertThat(persisted.getDescription()).isEqualTo("Transferência PIX");

        assertThat(response.id()).isEqualTo(TRANSFER_ID);
        assertThat(response.fromAccount().id()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(response.toAccount().id()).isEqualTo(TO_ACCOUNT_ID);
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando conta de origem e destino forem iguais")
    void create_shouldThrow_whenFromAndToAccountsAreSame() {
        TransferRequest request = request(FROM_ACCOUNT_ID, FROM_ACCOUNT_ID, AMOUNT, DATE, "PIX");

        assertThatThrownBy(() -> transferService.create(USER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(SAME_ACCOUNT_ERROR);

        verify(transferRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("create: deve lançar ResourceNotFoundException quando a conta de origem não for encontrada")
    void create_shouldThrow_whenFromAccountNotFound() {
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.create(USER_ID, defaultRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ACCOUNT_NOT_FOUND);

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: deve lançar ResourceNotFoundException quando a conta de destino não for encontrada")
    void create_shouldThrow_whenToAccountNotFound() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.create(USER_ID, defaultRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ACCOUNT_NOT_FOUND);

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando a conta de origem estiver arquivada")
    void create_shouldThrow_whenFromAccountIsArchived() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", true);
        Account to = account(TO_ACCOUNT_ID, "Poupança", false);

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> transferService.create(USER_ID, defaultRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ARCHIVED_ACCOUNT_ERROR);

        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: deve lançar BusinessRuleException quando a conta de destino estiver arquivada")
    void create_shouldThrow_whenToAccountIsArchived() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        Account to = account(TO_ACCOUNT_ID, "Poupança", true);

        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(from));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> transferService.create(USER_ID, defaultRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ARCHIVED_ACCOUNT_ERROR);

        verify(transferRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: deve atualizar todos os campos da transferência com sucesso")
    void update_shouldUpdateTransferSuccessfully() {
        Account oldFrom = account(FROM_ACCOUNT_ID, "Conta Antiga 1", false);
        Account oldTo = account(TO_ACCOUNT_ID, "Conta Antiga 2", false);
        Transfer transfer = transfer(TRANSFER_ID, oldFrom, oldTo);

        Long newFromId = 15L;
        Long newToId = 16L;
        Account newFrom = account(newFromId, "Nova Origem", false);
        Account newTo = account(newToId, "Novo Destino", false);

        stubFindOwnedTransfer(transfer);
        when(accountRepository.findByIdAndUserId(newFromId, USER_ID)).thenReturn(Optional.of(newFrom));
        when(accountRepository.findByIdAndUserId(newToId, USER_ID)).thenReturn(Optional.of(newTo));
        when(transferRepository.save(transfer)).thenReturn(transfer);

        LocalDate newDate = LocalDate.of(2026, 4, 1);
        BigDecimal newAmount = new BigDecimal("300.00");
        TransferRequest request = request(newFromId, newToId, newAmount, newDate, "Transferência Atualizada");

        TransferResponse response = transferService.update(USER_ID, TRANSFER_ID, request);

        assertThat(transfer.getFromAccount()).isSameAs(newFrom);
        assertThat(transfer.getToAccount()).isSameAs(newTo);
        assertThat(transfer.getAmount()).isEqualByComparingTo(newAmount);
        assertThat(transfer.getDate()).isEqualTo(newDate);
        assertThat(transfer.getDescription()).isEqualTo("Transferência Atualizada");

        assertThat(response.id()).isEqualTo(TRANSFER_ID);
        assertThat(response.fromAccount().id()).isEqualTo(newFromId);
        assertThat(response.toAccount().id()).isEqualTo(newToId);
        assertThat(response.amount()).isEqualByComparingTo(newAmount);
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando a transferência a ser atualizada não for encontrada")
    void update_shouldThrowNotFound_whenTransferDoesNotExist() {
        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.update(USER_ID, TRANSFER_ID, defaultRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TRANSFER_NOT_FOUND);

        verify(transferRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException quando atualizar para contas de origem e destino iguais")
    void update_shouldThrow_whenUpdatedAccountsAreSame() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        stubFindOwnedTransfer(transfer(TRANSFER_ID, from, account(TO_ACCOUNT_ID, "Poupança", false)));

        TransferRequest request = request(FROM_ACCOUNT_ID, FROM_ACCOUNT_ID, AMOUNT, DATE, "PIX");

        assertThatThrownBy(() -> transferService.update(USER_ID, TRANSFER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(SAME_ACCOUNT_ERROR);

        verify(transferRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("update: deve lançar BusinessRuleException ao atualizar para uma conta arquivada")
    void update_shouldThrow_whenUpdatedAccountIsArchived() {
        Account oldFrom = account(FROM_ACCOUNT_ID, "Origem", false);
        Account oldTo = account(TO_ACCOUNT_ID, "Destino", false);
        stubFindOwnedTransfer(transfer(TRANSFER_ID, oldFrom, oldTo));

        Account archivedFrom = account(FROM_ACCOUNT_ID, "Origem Arquivada", true);
        when(accountRepository.findByIdAndUserId(FROM_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(archivedFrom));
        when(accountRepository.findByIdAndUserId(TO_ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(oldTo));

        assertThatThrownBy(() -> transferService.update(USER_ID, TRANSFER_ID, defaultRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ARCHIVED_ACCOUNT_ERROR);

        verify(transferRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve deletar a transferência do banco")
    void delete_shouldDeleteTransferSuccessfully() {
        Account from = account(FROM_ACCOUNT_ID, "Conta Corrente", false);
        Account to = account(TO_ACCOUNT_ID, "Poupança", false);
        Transfer transfer = transfer(TRANSFER_ID, from, to);
        stubFindOwnedTransfer(transfer);

        transferService.delete(USER_ID, TRANSFER_ID);

        verify(transferRepository).delete(transfer);
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException quando tentar deletar transferência inexistente ou de outro usuário")
    void delete_shouldThrowNotFound_whenTransferDoesNotExist() {
        when(transferRepository.findByIdAndUserId(TRANSFER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.delete(USER_ID, TRANSFER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TRANSFER_NOT_FOUND);

        verify(transferRepository, never()).delete((DeleteSpecification<Transfer>) any());
    }
}