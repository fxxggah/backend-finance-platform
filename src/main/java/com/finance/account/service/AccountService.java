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

    private final AccountRepository accountRepository;
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
     * TODO (quando existirem Transaction, Transfer e InvoicePayment):
     * saldo = initialBalance + receitas + transferências recebidas
     *         - despesas - transferências enviadas - pagamentos de fatura.
     * Por enquanto só existe o saldo inicial.
     */
    private BigDecimal calculateBalance(Account account) {
        return account.getInitialBalance();
    }

    /** TODO: somar faturas em aberto quando o módulo de cartão existir. */
    private BigDecimal calculateCreditCardDebt(Long userId) {
        return BigDecimal.ZERO;
    }

    /**
     * TODO: retornar true se houver transações, transferências (origem/destino)
     * ou pagamentos de fatura ligados à conta.
     */
    private boolean hasMovements(Account account) {
        return false;
    }
}