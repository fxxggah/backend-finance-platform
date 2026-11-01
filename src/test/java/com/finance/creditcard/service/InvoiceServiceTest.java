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
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 10L;
    private static final Long ACCOUNT_ID = 3L;
    private static final Long PAYMENT_ID = 77L;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 1);
    private static final String REFERENCE_STR = "2026-10";

    // Datas relativas a "hoje" para controlar o status da fatura de forma determinística
    private static final LocalDate CLOSED_ON = TODAY.minusDays(10);
    private static final LocalDate DUE_FUTURE = TODAY.plusDays(5);
    private static final LocalDate DUE_PAST = TODAY.minusDays(1);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InvoicePaymentRepository paymentRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardDateUtil dateUtil;

    @InjectMocks
    private InvoiceService invoiceService;

    // ---------- helpers ----------

    private CreditCard card() {
        CreditCard card = new CreditCard(
                new User("google-1", "joao@example.com", "João", null),
                "Nubank", new BigDecimal("5000.00"), 10, 17, "#8A05BE", "credit-card");
        card.setId(CARD_ID);
        return card;
    }

    private void stubInvoice(String total, String paid, LocalDate closingDate, LocalDate dueDate) {
        when(transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(CARD_ID, REFERENCE))
                .thenReturn(new BigDecimal(total));
        when(paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(CARD_ID, REFERENCE))
                .thenReturn(new BigDecimal(paid));
        when(dateUtil.closingDate(eq(REFERENCE), anyInt())).thenReturn(closingDate);
        when(dateUtil.dueDate(eq(REFERENCE), anyInt(), anyInt())).thenReturn(dueDate);
        when(dateUtil.formatReference(REFERENCE)).thenReturn(REFERENCE_STR);
    }

    /** Fatura fechada, com vencimento no futuro. */
    private void stubClosedInvoiceForPayment(String total, String paid) {
        when(dateUtil.parseReference(REFERENCE_STR)).thenReturn(REFERENCE);
        stubInvoice(total, paid, CLOSED_ON, DUE_FUTURE);
    }

    private InvoicePaymentRequest paymentRequest(String amount) {
        return new InvoicePaymentRequest(ACCOUNT_ID, new BigDecimal(amount), TODAY);
    }

    // ---------- getInvoiceSummary ----------

    @Test
    @DisplayName("getInvoiceSummary: deve montar a fatura com referência, datas, total e pago")
    void getInvoiceSummary_shouldBuildInvoice() {
        stubInvoice("1500.00", "500.00", CLOSED_ON, DUE_FUTURE);

        InvoiceResponse response = invoiceService.getInvoiceSummary(card(), REFERENCE);

        assertThat(response.reference()).isEqualTo(REFERENCE_STR);
        assertThat(response.closingDate()).isEqualTo(CLOSED_ON);
        assertThat(response.dueDate()).isEqualTo(DUE_FUTURE);
        assertThat(response.total()).isEqualByComparingTo("1500.00");
        assertThat(response.paid()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("getInvoiceSummary: status OPEN enquanto hoje não passou da data de fechamento")
    void getInvoiceSummary_shouldBeOpen_beforeClosingDate() {
        stubInvoice("1500.00", "0.00", TODAY.plusDays(3), TODAY.plusDays(10));

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.OPEN);
    }

    @Test
    @DisplayName("getInvoiceSummary: status OPEN no próprio dia do fechamento")
    void getInvoiceSummary_shouldBeOpen_onClosingDate() {
        stubInvoice("1500.00", "0.00", TODAY, TODAY.plusDays(7));

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.OPEN);
    }

    @Test
    @DisplayName("getInvoiceSummary: status CLOSED quando fechada, sem pagamentos e antes do vencimento")
    void getInvoiceSummary_shouldBeClosed() {
        stubInvoice("1500.00", "0.00", CLOSED_ON, DUE_FUTURE);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.CLOSED);
    }

    @Test
    @DisplayName("getInvoiceSummary: status PARTIALLY_PAID quando há pagamento parcial antes do vencimento")
    void getInvoiceSummary_shouldBePartiallyPaid() {
        stubInvoice("1500.00", "500.00", CLOSED_ON, DUE_FUTURE);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status())
                .isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("getInvoiceSummary: status PAID quando o pago cobre o total")
    void getInvoiceSummary_shouldBePaid_whenPaidCoversTotal() {
        stubInvoice("1500.00", "1500.00", CLOSED_ON, DUE_FUTURE);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("getInvoiceSummary: status PAID quando a fatura fechada tem total zero")
    void getInvoiceSummary_shouldBePaid_whenTotalIsZero() {
        stubInvoice("0.00", "0.00", CLOSED_ON, DUE_FUTURE);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("getInvoiceSummary: status OVERDUE quando vencida e não quitada")
    void getInvoiceSummary_shouldBeOverdue() {
        stubInvoice("1500.00", "500.00", CLOSED_ON, DUE_PAST);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.OVERDUE);
    }

    @Test
    @DisplayName("getInvoiceSummary: fatura vencida e quitada continua PAID")
    void getInvoiceSummary_shouldBePaid_evenWhenPastDueDate() {
        stubInvoice("1500.00", "1500.00", CLOSED_ON, DUE_PAST);

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("getInvoiceSummary: fatura aberta nunca é marcada como paga/vencida, mesmo com total zero")
    void getInvoiceSummary_shouldStayOpen_evenWhenTotalIsZero() {
        stubInvoice("0.00", "0.00", TODAY.plusDays(2), TODAY.plusDays(9));

        assertThat(invoiceService.getInvoiceSummary(card(), REFERENCE).status()).isEqualTo(InvoiceStatus.OPEN);
    }

    // ---------- listInvoices ----------

    @Test
    @DisplayName("listInvoices: deve montar uma fatura para cada referência com movimentação")
    void listInvoices_shouldBuildOneInvoicePerReference() {
        LocalDate sep = LocalDate.of(2026, 9, 1);
        when(transactionRepository.findDistinctInvoiceReferencesByCreditCardId(CARD_ID))
                .thenReturn(List.of(sep, REFERENCE));
        when(transactionRepository.sumAmountByCreditCardIdAndInvoiceReference(eq(CARD_ID), any(LocalDate.class)))
                .thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.sumPaymentsByCreditCardIdAndInvoiceReference(eq(CARD_ID), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(dateUtil.closingDate(any(LocalDate.class), anyInt())).thenReturn(CLOSED_ON);
        when(dateUtil.dueDate(any(LocalDate.class), anyInt(), anyInt())).thenReturn(DUE_FUTURE);
        when(dateUtil.formatReference(sep)).thenReturn("2026-09");
        when(dateUtil.formatReference(REFERENCE)).thenReturn(REFERENCE_STR);

        List<InvoiceResponse> result = invoiceService.listInvoices(card());

        assertThat(result).extracting(InvoiceResponse::reference).containsExactly("2026-09", REFERENCE_STR);
    }

    @Test
    @DisplayName("listInvoices: deve retornar lista vazia quando o cartão não tem faturas")
    void listInvoices_shouldReturnEmpty_whenNoReferences() {
        when(transactionRepository.findDistinctInvoiceReferencesByCreditCardId(CARD_ID)).thenReturn(List.of());

        assertThat(invoiceService.listInvoices(card())).isEmpty();
    }

    // ---------- payInvoice ----------

    @Test
    @DisplayName("payInvoice: deve registrar o pagamento vinculado a usuário, cartão, conta e referência")
    void payInvoice_shouldPersistPayment() {
        CreditCard card = card();
        Account account = mock(Account.class);
        stubClosedInvoiceForPayment("1000.00", "200.00");
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(account.isArchived()).thenReturn(false);

        invoiceService.payInvoice(USER_ID, card, REFERENCE_STR, paymentRequest("500.00"));

        ArgumentCaptor<InvoicePayment> captor = ArgumentCaptor.forClass(InvoicePayment.class);
        verify(paymentRepository).save(captor.capture());
        InvoicePayment saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(card.getUser());
        assertThat(saved.getCreditCard()).isSameAs(card);
        assertThat(saved.getAccount()).isSameAs(account);
        assertThat(saved.getInvoiceReference()).isEqualTo(REFERENCE);
        assertThat(saved.getAmount()).isEqualByComparingTo("500.00");
        assertThat(saved.getDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("payInvoice: deve permitir quitar exatamente o saldo restante")
    void payInvoice_shouldAllowPayingExactRemaining() {
        Account account = mock(Account.class);
        stubClosedInvoiceForPayment("1000.00", "200.00");
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(account.isArchived()).thenReturn(false);

        invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("800.00"));

        verify(paymentRepository).save(any(InvoicePayment.class));
    }

    @Test
    @DisplayName("payInvoice: deve lançar BusinessRuleException ao pagar fatura aberta")
    void payInvoice_shouldThrow_whenInvoiceIsOpen() {
        when(dateUtil.parseReference(REFERENCE_STR)).thenReturn(REFERENCE);
        stubInvoice("1000.00", "0.00", TODAY.plusDays(3), TODAY.plusDays(10));

        assertThatThrownBy(() -> invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("100.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível pagar uma fatura aberta.");

        verify(paymentRepository, never()).save(any(InvoicePayment.class));
        verify(accountRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("payInvoice: deve lançar BusinessRuleException quando o valor excede o saldo restante")
    void payInvoice_shouldThrow_whenAmountExceedsRemaining() {
        stubClosedInvoiceForPayment("1000.00", "200.00");

        assertThatThrownBy(() -> invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("800.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O valor do pagamento não pode ser maior que o saldo restante da fatura.");

        verify(paymentRepository, never()).save(any(InvoicePayment.class));
        verify(accountRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("payInvoice: deve lançar BusinessRuleException ao pagar fatura já quitada")
    void payInvoice_shouldThrow_whenInvoiceAlreadyPaid() {
        stubClosedInvoiceForPayment("1000.00", "1000.00");

        assertThatThrownBy(() -> invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("10.00")))
                .isInstanceOf(BusinessRuleException.class);

        verify(paymentRepository, never()).save(any(InvoicePayment.class));
    }

    @Test
    @DisplayName("payInvoice: deve lançar ResourceNotFoundException quando a conta não existe ou é de outro usuário")
    void payInvoice_shouldThrowNotFound_whenAccountNotOwned() {
        stubClosedInvoiceForPayment("1000.00", "0.00");
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("100.00")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conta de origem não encontrada.");

        verify(paymentRepository, never()).save(any(InvoicePayment.class));
    }

    @Test
    @DisplayName("payInvoice: deve lançar BusinessRuleException quando a conta de origem está arquivada")
    void payInvoice_shouldThrow_whenAccountIsArchived() {
        Account account = mock(Account.class);
        stubClosedInvoiceForPayment("1000.00", "0.00");
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(account.isArchived()).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.payInvoice(USER_ID, card(), REFERENCE_STR, paymentRequest("100.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A conta de origem está arquivada.");

        verify(paymentRepository, never()).save(any(InvoicePayment.class));
    }

    // ---------- deletePayment ----------

    @Test
    @DisplayName("deletePayment: deve excluir o pagamento do usuário")
    void deletePayment_shouldDelete_whenOwned() {
        InvoicePayment payment = new InvoicePayment();
        when(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));

        invoiceService.deletePayment(USER_ID, PAYMENT_ID);

        verify(paymentRepository).delete(payment);
    }

    @Test
    @DisplayName("deletePayment: deve lançar ResourceNotFoundException quando não existe ou é de outro usuário")
    void deletePayment_shouldThrowNotFound_whenNotOwned() {
        when(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.deletePayment(USER_ID, PAYMENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pagamento não encontrado.");

        verify(paymentRepository, never()).delete(any(InvoicePayment.class));
    }
}