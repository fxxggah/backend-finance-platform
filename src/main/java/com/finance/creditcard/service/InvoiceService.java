package com.finance.creditcard.service;

import com.finance.account.entity.Account;
import com.finance.account.repository.AccountRepository;
import com.finance.common.enums.InvoiceStatus;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.util.CreditCardDateUtil;
import com.finance.creditcard.dto.request.InvoicePaymentRequest;
import com.finance.creditcard.dto.response.InvoiceResponse;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.entity.InvoicePayment;
import com.finance.creditcard.repository.InvoicePaymentRepository;
import com.finance.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final TransactionRepository transactionRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final CreditCardDateUtil dateUtil;

    // Constrói o estado de uma fatura específica
    public InvoiceResponse getInvoiceSummary(CreditCard card, LocalDate reference) {
        BigDecimal total = transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(card.getId(), reference);
        BigDecimal paid = paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(card.getId(), reference);

        LocalDate closingDate = dateUtil.closingDate(reference, card.getClosingDay());
        LocalDate dueDate = dateUtil.dueDate(reference, card.getClosingDay(), card.getDueDay());

        InvoiceStatus status = determineStatus(total, paid, closingDate, dueDate);

        return new InvoiceResponse(
                dateUtil.formatReference(reference),
                closingDate,
                dueDate,
                total,
                paid,
                status
        );
    }

    // Calcula todas as faturas geradas pelo cartão
    public List<InvoiceResponse> listInvoices(CreditCard card) {
        List<LocalDate> references = transactionRepository.findDistinctInvoiceReferencesByCreditCardId(card.getId());
        return references.stream()
                .map(ref -> getInvoiceSummary(card, ref))
                .toList();
    }

    @Transactional
    public void payInvoice(Long userId, CreditCard card, String referenceStr, InvoicePaymentRequest request) {
        LocalDate reference = dateUtil.parseReference(referenceStr);
        InvoiceResponse invoice = getInvoiceSummary(card, reference);

        if (invoice.status() == InvoiceStatus.OPEN) {
            throw new BusinessRuleException("Não é possível pagar uma fatura aberta.");
        }

        BigDecimal remaining = invoice.total().subtract(invoice.paid());
        if (request.amount().compareTo(remaining) > 0) {
            throw new BusinessRuleException("O valor do pagamento não pode ser maior que o saldo restante da fatura.");
        }

        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta de origem não encontrada."));

        if (account.isArchived()) {
            throw new BusinessRuleException("A conta de origem está arquivada.");
        }

        InvoicePayment payment = new InvoicePayment(
                card.getUser(),
                card,
                account,
                reference,
                request.amount(),
                request.date()
        );

        paymentRepository.save(payment);
    }

    @Transactional
    public void deletePayment(Long userId, Long paymentId) {
        InvoicePayment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));
        paymentRepository.delete(payment);
    }

    private InvoiceStatus determineStatus(BigDecimal total, BigDecimal paid, LocalDate closingDate, LocalDate dueDate) {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        if (!today.isAfter(closingDate)) return InvoiceStatus.OPEN;
        if (total.compareTo(BigDecimal.ZERO) == 0 || paid.compareTo(total) >= 0) return InvoiceStatus.PAID;
        if (today.isAfter(dueDate)) return InvoiceStatus.OVERDUE;
        if (paid.compareTo(BigDecimal.ZERO) > 0) return InvoiceStatus.PARTIALLY_PAID;

        return InvoiceStatus.CLOSED;
    }
}