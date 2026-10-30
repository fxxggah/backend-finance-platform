package com.finance.creditcard.service;

import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.dto.request.CreditCardRequest;
import com.finance.creditcard.dto.response.CreditCardResponse;
import com.finance.creditcard.dto.response.InvoiceResponse;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.repository.CreditCardRepository;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.transaction.repository.TransactionRepository;
import com.finance.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final TransactionRepository transactionRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<CreditCardResponse> list(Long userId) {
        List<CreditCard> cards = creditCardRepository.findAllByUserIdAndArchivedFalse(userId);
        return cards.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CreditCardResponse create(Long userId, CreditCardRequest request) {
        if (creditCardRepository.existsByUserIdAndNameIgnoreCase(userId, request.name().trim())) {
            throw new ConflictException("Já existe um cartão com este nome.");
        }

        User user = entityManager.getReference(User.class, userId);
        CreditCard card = new CreditCard(
                user,
                request.name().trim(),
                request.creditLimit(),
                request.closingDay(),
                request.dueDay(),
                request.color(),
                request.icon()
        );

        return toResponse(creditCardRepository.save(card));
    }

    @Transactional
    public CreditCardResponse update(Long userId, Long id, CreditCardRequest request) {
        CreditCard card = getCardOrThrow(id, userId);

        if (creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name().trim(), id)) {
            throw new ConflictException("Já existe um cartão com este nome.");
        }

        card.setName(request.name().trim());
        card.setCreditLimit(request.creditLimit());
        card.setClosingDay(request.closingDay());
        card.setDueDay(request.dueDay());
        card.setColor(request.color());
        card.setIcon(request.icon());

        return toResponse(creditCardRepository.save(card));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        CreditCard card = getCardOrThrow(id, userId);

        boolean hasMovements = transactionRepository.existsByCreditCardIdAndDeletedFalse(id) ||
                paymentRepository.existsByCreditCardId(id);

        if (hasMovements) {
            card.setArchived(true);
            creditCardRepository.save(card);
        } else {
            creditCardRepository.delete(card);
        }
    }

    public CreditCard getCardOrThrow(Long id, Long userId) {
        return creditCardRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de crédito não encontrado."));
    }

    private CreditCardResponse toResponse(CreditCard card) {
        // Cálculo do Limite
        BigDecimal totalSpent = transactionRepository.sumTotalAmountByCreditCardId(card.getId());
        BigDecimal totalPaid = paymentRepository.sumTotalPaymentsByCreditCardId(card.getId());

        BigDecimal usedLimit = totalSpent.subtract(totalPaid);
        BigDecimal availableLimit = card.getCreditLimit().subtract(usedLimit);

        // Identificação da "Current Invoice" (fatura atual / em aberto baseada no dia de hoje)
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate currentReference = today.withDayOfMonth(1);
        if (today.getDayOfMonth() > card.getClosingDay()) {
            currentReference = currentReference.plusMonths(1);
        }
        InvoiceResponse currentInvoice = invoiceService.getInvoiceSummary(card, currentReference);

        return new CreditCardResponse(
                card.getId(),
                card.getName(),
                card.getCreditLimit(),
                usedLimit,
                availableLimit,
                card.getClosingDay(),
                card.getDueDay(),
                card.getColor(),
                card.getIcon(),
                card.isArchived(),
                currentInvoice
        );
    }
}