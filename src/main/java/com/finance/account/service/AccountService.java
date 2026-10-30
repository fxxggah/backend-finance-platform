package com.finance.account.service;

import com.finance.account.dto.request.AccountRequest;
import com.finance.account.dto.response.AccountDeleteResponse;
import com.finance.account.dto.response.AccountListResponse;
import com.finance.account.dto.response.AccountResponse;
import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transfer.repository.TransferRepository;
import com.finance.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final CreditCardRepository creditCardRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AccountListResponse list(Long userId, boolean includeArchived) {
        List<Account> accounts = includeArchived
                ? accountRepository.findAllByUserId(userId)
                : accountRepository.findAllByUserIdAndArchivedFalse(userId);

        List<AccountResponse> responses = accounts.stream()
                .map(a -> AccountResponse.from(a, calculateBalance(a)))
                .toList();

        // O patrimônio total considera só contas ativas
        BigDecimal totalBalance = responses.stream()
                .filter(r -> !r.archived())
                .map(AccountResponse::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountListResponse(responses, totalBalance, calculateCreditCardDebt(userId));
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(Long userId, Long id) {
        Account account = findOwned(userId, id);
        return AccountResponse.from(account, calculateBalance(account));
    }

    @Transactional
    public AccountResponse create(Long userId, AccountRequest request) {
        String name = request.name().trim();

        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe uma conta com esse nome");
        }

        User userRef = entityManager.getReference(User.class, userId);
        Account account = new Account(
                userRef,
                name,
                request.type(),
                request.initialBalance(),
                request.color(),
                request.icon());

        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved, calculateBalance(saved));
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, AccountRequest request) {
        Account account = findOwned(userId, id);
        String name = request.name().trim();

        if (accountRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new ConflictException("Já existe uma conta com esse nome");
        }

        account.setName(name);
        account.setType(request.type());
        account.setInitialBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);
        account.setColor(request.color());
        account.setIcon(request.icon());

        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved, calculateBalance(saved));
    }

    /**
     * Conta com movimentação é arquivada; conta vazia é excluída de vez.
     * Não permite remover/arquivar a última conta ativa.
     */
    @Transactional
    public AccountDeleteResponse delete(Long userId, Long id) {
        Account account = findOwned(userId, id);

        if (!account.isArchived() && accountRepository.countByUserIdAndArchivedFalse(userId) <= 1) {
            throw new BusinessRuleException("Não é possível excluir ou arquivar a última conta ativa");
        }

        if (hasMovements(account)) {
            account.setArchived(true);
            accountRepository.save(account);
            return new AccountDeleteResponse(true);
        }

        accountRepository.delete(account);
        return new AccountDeleteResponse(false);
    }

    // Recurso de outro usuário também retorna 404 (não vaza a existência do ID)
    private Account findOwned(Long userId, Long id) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
    }

    /**
     * Calcula o saldo atual:
     * Saldo = initialBalance + (receitas - despesas) + (transferências recebidas - enviadas) - pagamentos de fatura
     */
    private BigDecimal calculateBalance(Account account) {
        BigDecimal netTransactions = transactionRepository.getNetBalanceByAccountId(account.getId());

        BigDecimal totalReceived = transferRepository.sumAmountReceivedByAccountId(account.getId());
        BigDecimal totalSent = transferRepository.sumAmountSentByAccountId(account.getId());

        // Busca o total de faturas pagas usando esta conta
        BigDecimal totalInvoicePayments = invoicePaymentRepository.sumPaymentsByAccountId(account.getId());

        return account.getInitialBalance()
                .add(netTransactions)
                .add(totalReceived)
                .subtract(totalSent)
                .subtract(totalInvoicePayments);
    }

    /** Calcula a dívida total de faturas de cartão de crédito em aberto (não pagas). */
    private BigDecimal calculateCreditCardDebt(Long userId) {
        List<CreditCard> activeCards = creditCardRepository.findAllByUserIdAndArchivedFalse(userId);

        BigDecimal totalDebt = BigDecimal.ZERO;

        for (CreditCard card : activeCards) {
            BigDecimal totalSpent = transactionRepository.sumTotalAmountByCreditCardId(card.getId());
            BigDecimal totalPaid = invoicePaymentRepository.sumTotalPaymentsByCreditCardId(card.getId());
            BigDecimal cardDebt = totalSpent.subtract(totalPaid);

            if (cardDebt.compareTo(BigDecimal.ZERO) > 0) {
                totalDebt = totalDebt.add(cardDebt);
            }
        }

        return totalDebt;
    }

    /**
     * Verifica se a conta já foi movimentada para impedir exclusão física.
     */
    private boolean hasMovements(Account account) {
        boolean hasTransactions = transactionRepository.existsByAccountIdAndDeletedFalse(account.getId());
        boolean hasTransfers = transferRepository.existsByFromAccountIdOrToAccountId(account.getId(), account.getId());
        boolean hasInvoicePayments = invoicePaymentRepository.existsByAccountId(account.getId());

        return hasTransactions || hasTransfers || hasInvoicePayments;
    }
}