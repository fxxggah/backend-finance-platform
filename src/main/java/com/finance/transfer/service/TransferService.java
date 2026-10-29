package com.finance.transfer.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.dto.PageResponse;
import com.finance.transfer.dto.request.TransferRequest;
import com.finance.transfer.dto.response.TransferResponse;
import com.finance.transfer.entity.Transfer;
import com.finance.transfer.repository.TransferRepository;
import com.finance.transfer.repository.TransferSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(Long userId, LocalDate from, LocalDate to, Long accountId, Pageable pageable) {
        Page<Transfer> page = transferRepository.findAll(
                TransferSpecification.withFilters(userId, from, to, accountId),
                pageable
        );
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TransferResponse findById(Long userId, Long id) {
        Transfer transfer = getTransferOrThrow(id, userId);
        return toResponse(transfer);
    }

    @Transactional
    public TransferResponse create(Long userId, TransferRequest request) {
        validateAccounts(request.fromAccountId(), request.toAccountId());

        Account fromAccount = getAccountOrThrow(request.fromAccountId(), userId);
        Account toAccount = getAccountOrThrow(request.toAccountId(), userId);

        validateActiveAccounts(fromAccount, toAccount);

        Transfer transfer = new Transfer(
                fromAccount.getUser(),
                fromAccount,
                toAccount,
                request.amount(),
                request.date(),
                request.description()
        );

        return toResponse(transferRepository.save(transfer));
    }

    @Transactional
    public TransferResponse update(Long userId, Long id, TransferRequest request) {
        Transfer transfer = getTransferOrThrow(id, userId);

        validateAccounts(request.fromAccountId(), request.toAccountId());

        Account fromAccount = getAccountOrThrow(request.fromAccountId(), userId);
        Account toAccount = getAccountOrThrow(request.toAccountId(), userId);

        validateActiveAccounts(fromAccount, toAccount);

        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(request.amount());
        transfer.setDate(request.date());
        transfer.setDescription(request.description());

        return toResponse(transferRepository.save(transfer));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transfer transfer = getTransferOrThrow(id, userId);
        transferRepository.delete(transfer);
    }

    private Transfer getTransferOrThrow(Long id, Long userId) {
        return transferRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transferência não encontrada."));
    }

    private Account getAccountOrThrow(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
    }

    private void validateAccounts(Long fromAccountId, Long toAccountId) {
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessRuleException("A conta de origem não pode ser igual à conta de destino.");
        }
    }

    private void validateActiveAccounts(Account from, Account to) {
        if (from.isArchived() || to.isArchived()) {
            throw new BusinessRuleException("Não é possível realizar transferências envolvendo contas arquivadas.");
        }
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                new TransferResponse.AccountSummary(transfer.getFromAccount().getId(), transfer.getFromAccount().getName(), transfer.getFromAccount().getColor(), transfer.getFromAccount().getIcon()),
                new TransferResponse.AccountSummary(transfer.getToAccount().getId(), transfer.getToAccount().getName(), transfer.getToAccount().getColor(), transfer.getToAccount().getIcon()),
                transfer.getAmount(),
                transfer.getDate(),
                transfer.getDescription()
        );
    }
}