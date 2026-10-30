package com.finance.transaction.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.category.entity.Category;
import com.finance.category.repository.CategoryRepository;
import com.finance.common.dto.PageResponse;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.dto.request.TransactionRequest;
import com.finance.transaction.dto.response.TransactionResponse;
import com.finance.transaction.entity.Transaction;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.transaction.repository.TransactionSpecifications;
import com.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT = Set.of("date", "amount", "description", "createdAt");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CreditCardRepository creditCardRepository; // Adicionado para Fase 5/6

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(Long userId, TransactionFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("A data inicial não pode ser posterior à data final");
        }

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpecifications.filter(userId, filter),
                sanitize(pageable));

        return PageResponse.from(page.map(TransactionResponse::from));
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(Long userId, Long id) {
        return TransactionResponse.from(getOwnedOrThrow(userId, id));
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        validateSource(request);
        validateAmount(request);
        validateDate(request.date());

        Category category = loadCategory(userId, request.categoryId(), request.type(), null);

        Account account = null;
        CreditCard card = null;
        LocalDate invoiceReference = null;

        // Associa Conta OU Cartão de Crédito
        if (request.accountId() != null) {
            account = loadAccount(userId, request.accountId(), null);
        } else if (request.creditCardId() != null) {
            card = loadCreditCard(userId, request.creditCardId(), null);
            invoiceReference = calculateInvoiceReference(request.date(), card.getClosingDay());
        }

        Transaction tx = new Transaction();
        tx.setUser(userRepository.getReferenceById(userId));
        tx.setType(request.type());
        tx.setAmount(request.amount());
        tx.setDate(request.date());
        tx.setDescription(request.description());
        tx.setNotes(request.notes());
        tx.setCategory(category);
        tx.setAccount(account);
        tx.setCreditCard(card);
        tx.setInvoiceReference(invoiceReference);

        return TransactionResponse.from(transactionRepository.save(tx));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction tx = getOwnedOrThrow(userId, id);

        if (tx.getInstallmentGroupId() != null) {
            return updateInstallmentGroup(userId, tx, request);
        }
        if (tx.getCreditCard() != null) {
            throw new BusinessRuleException("A edição de compras no cartão de crédito ainda não está disponível");
        }

        validateSource(request);
        validateAmount(request);
        validateDate(request.date());

        Category category = loadCategory(userId, request.categoryId(), request.type(), tx.getCategory());
        Account account = loadAccount(userId, request.accountId(), tx.getAccount());

        tx.setType(request.type());
        tx.setAmount(request.amount());
        tx.setDate(request.date());
        tx.setDescription(request.description());
        tx.setNotes(request.notes());
        tx.setCategory(category);
        tx.setAccount(account);

        return TransactionResponse.from(transactionRepository.save(tx));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction tx = getOwnedOrThrow(userId, id);

        List<Transaction> targets = tx.getInstallmentGroupId() == null
                ? List.of(tx)
                : loadActiveGroup(userId, tx.getInstallmentGroupId());

        targets.forEach(t -> t.setDeleted(true));
        transactionRepository.saveAll(targets);
    }

    private TransactionResponse updateInstallmentGroup(Long userId, Transaction tx, TransactionRequest request) {
        if (request.type() != tx.getType()) {
            throw new BusinessRuleException("O tipo não pode ser alterado em compra parcelada");
        }

        Category category = loadCategory(userId, request.categoryId(), tx.getType(), tx.getCategory());
        List<Transaction> group = loadActiveGroup(userId, tx.getInstallmentGroupId());

        group.forEach(t -> {
            t.setCategory(category);
            t.setDescription(request.description());
            t.setNotes(request.notes());
        });
        transactionRepository.saveAll(group);

        return TransactionResponse.from(tx);
    }

    private List<Transaction> loadActiveGroup(Long userId, String groupId) {
        return transactionRepository.findAllByInstallmentGroupIdAndUserId(groupId, userId).stream()
                .filter(t -> !t.isDeleted())
                .toList();
    }

    private Transaction getOwnedOrThrow(Long userId, Long id) {
        return transactionRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));
    }

    private void validateSource(TransactionRequest request) {
        boolean hasAccount = request.accountId() != null;
        boolean hasCard = request.creditCardId() != null;

        if (hasAccount == hasCard) {
            throw new BusinessRuleException("Informe accountId ou creditCardId (exatamente um dos dois)");
        }
        if (hasCard) {
            if (request.type() != TransactionType.EXPENSE) {
                throw new BusinessRuleException("Cartão de crédito aceita apenas despesas");
            }
        }
        if (request.installments() != null && request.installments() > 1) {
            throw new BusinessRuleException("Parcelamento disponível apenas para compras no cartão de crédito");
        }
    }

    private void validateAmount(TransactionRequest request) {
        if (request.amount() == null) {
            throw new BusinessRuleException("O valor (amount) é obrigatório");
        }
    }

    private void validateDate(LocalDate date) {
        LocalDate limit = LocalDate.now(ZONE).plusYears(1);
        if (date.isAfter(limit)) {
            throw new BusinessRuleException("A data não pode ser superior a 1 ano à frente");
        }
    }

    private Category loadCategory(Long userId, Long categoryId, TransactionType type, Category current) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

        boolean changed = current == null || !current.getId().equals(category.getId());
        if (changed && category.isDeleted()) {
            throw new BusinessRuleException("Não é possível usar uma categoria excluída");
        }
        if (category.getType() != type) {
            throw new BusinessRuleException("O tipo da categoria deve ser igual ao tipo da transação");
        }
        return category;
    }

    private Account loadAccount(Long userId, Long accountId, Account current) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));

        boolean changed = current == null || !current.getId().equals(account.getId());
        if (changed && account.isArchived()) {
            throw new BusinessRuleException("Não é possível usar uma conta arquivada");
        }
        return account;
    }

    // Carrega o Cartão e verifica se é válido
    private CreditCard loadCreditCard(Long userId, Long cardId, CreditCard current) {
        CreditCard card = creditCardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de crédito não encontrado"));

        boolean changed = current == null || !current.getId().equals(card.getId());
        if (changed && card.isArchived()) {
            throw new BusinessRuleException("Não é possível usar um cartão arquivado");
        }
        return card;
    }

    // Calcula em que fatura a compra entra (se data > dia de fecho, entra no mês seguinte)
    private LocalDate calculateInvoiceReference(LocalDate purchaseDate, int closingDay) {
        LocalDate ref = purchaseDate.withDayOfMonth(1);
        if (purchaseDate.getDayOfMonth() > closingDay) {
            ref = ref.plusMonths(1);
        }
        return ref;
    }

    private Pageable sanitize(Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().stream()
                .filter(o -> ALLOWED_SORT.contains(o.getProperty()))
                .toList();

        Sort sort = orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "date") : Sort.by(orders);
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));

        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}