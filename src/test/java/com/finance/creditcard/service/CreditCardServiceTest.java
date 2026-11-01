package com.finance.creditcard.service;

import com.finance.common.enums.InvoiceStatus;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 10L;

    private static final String NOT_FOUND = "Cartão de crédito não encontrado.";
    private static final String CONFLICT = "Já existe um cartão com este nome.";

    private static final InvoiceResponse INVOICE = new InvoiceResponse(
            "2026-10", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 17),
            BigDecimal.ZERO, BigDecimal.ZERO, InvoiceStatus.OPEN);

    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private InvoicePaymentRepository paymentRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CreditCardService creditCardService;

    // ---------- helpers ----------

    private CreditCard card(Long id, String name) {
        CreditCard card = new CreditCard(
                new User("google-1", "joao@example.com", "João", null),
                name, new BigDecimal("5000.00"), 10, 17, "#8A05BE", "credit-card");
        card.setId(id);
        return card;
    }

    private void stubFindOwned(CreditCard card) {
        when(creditCardRepository.findByIdAndUserId(card.getId(), USER_ID)).thenReturn(Optional.of(card));
    }

    /** Stub dos gastos e pagamentos de um cartão específico (usado no cálculo de limites). */
    private void stubTotals(Long cardId, String spent, String paid) {
        when(transactionRepository.sumTotalAmountByCreditCardId(cardId)).thenReturn(new BigDecimal(spent));
        when(paymentRepository.sumTotalPaymentsByCreditCardId(cardId)).thenReturn(new BigDecimal(paid));
    }

    /** Stub genérico da fatura atual; deve ser chamado uma única vez por teste. */
    private void stubCurrentInvoice() {
        when(invoiceService.getInvoiceSummary(any(CreditCard.class), any(LocalDate.class))).thenReturn(INVOICE);
    }

    /** Atalho para testes com um único cartão. */
    private void stubResponseData(Long cardId, String spent, String paid) {
        stubTotals(cardId, spent, paid);
        stubCurrentInvoice();
    }

    private CreditCardRequest request(String name) {
        return new CreditCardRequest(name, new BigDecimal("8000.00"), 5, 12, "#000000", "wallet");
    }

    // ---------- list ----------

    @Test
    @DisplayName("list: deve retornar somente os cartões ativos do usuário com limites calculados")
    void list_shouldReturnActiveCardsWithComputedLimits() {
        CreditCard nubank = card(1L, "Nubank");
        CreditCard inter = card(2L, "Inter");
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of(nubank, inter));
        stubTotals(1L, "1500.00", "500.00");
        stubTotals(2L, "0.00", "0.00");
        stubCurrentInvoice();

        List<CreditCardResponse> response = creditCardService.list(USER_ID);

        assertThat(response).extracting(CreditCardResponse::name).containsExactly("Nubank", "Inter");
        assertThat(response.get(0).usedLimit()).isEqualByComparingTo("1000.00");
        assertThat(response.get(0).availableLimit()).isEqualByComparingTo("4000.00");
        assertThat(response.get(1).usedLimit()).isEqualByComparingTo("0.00");
        assertThat(response.get(1).availableLimit()).isEqualByComparingTo("5000.00");
        verify(creditCardRepository, never()).findAllByUserId(anyLong());
    }

    @Test
    @DisplayName("list: deve retornar lista vazia quando o usuário não tem cartões")
    void list_shouldReturnEmpty_whenUserHasNoCards() {
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of());

        assertThat(creditCardService.list(USER_ID)).isEmpty();
        verifyNoInteractions(transactionRepository, paymentRepository, invoiceService);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: deve remover espaços do nome e persistir o cartão vinculado ao usuário")
    void create_shouldTrimNameAndPersistCard() {
        User userRef = new User("google-1", "joao@example.com", "João", null);
        when(creditCardRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nubank")).thenReturn(false);
        when(entityManager.getReference(User.class, USER_ID)).thenReturn(userRef);
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> {
            CreditCard saved = invocation.getArgument(0);
            saved.setId(CARD_ID);
            return saved;
        });
        stubResponseData(CARD_ID, "0.00", "0.00");

        CreditCardResponse response = creditCardService.create(USER_ID, request("  Nubank  "));

        ArgumentCaptor<CreditCard> captor = ArgumentCaptor.forClass(CreditCard.class);
        verify(creditCardRepository).save(captor.capture());
        CreditCard persisted = captor.getValue();
        assertThat(persisted.getUser()).isSameAs(userRef);
        assertThat(persisted.getName()).isEqualTo("Nubank");
        assertThat(persisted.getCreditLimit()).isEqualByComparingTo("8000.00");
        assertThat(persisted.getClosingDay()).isEqualTo(5);
        assertThat(persisted.getDueDay()).isEqualTo(12);
        assertThat(persisted.getColor()).isEqualTo("#000000");
        assertThat(persisted.getIcon()).isEqualTo("wallet");
        assertThat(persisted.isArchived()).isFalse();

        assertThat(response.id()).isEqualTo(CARD_ID);
        assertThat(response.name()).isEqualTo("Nubank");
        assertThat(response.availableLimit()).isEqualByComparingTo("8000.00");
        assertThat(response.currentInvoice()).isEqualTo(INVOICE);
    }

    @Test
    @DisplayName("create: deve lançar ConflictException quando já existe cartão com o mesmo nome")
    void create_shouldThrowConflict_whenNameAlreadyExists() {
        when(creditCardRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nubank")).thenReturn(true);

        assertThatThrownBy(() -> creditCardService.create(USER_ID, request(" Nubank ")))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        verify(creditCardRepository, never()).save(any(CreditCard.class));
        verifyNoInteractions(entityManager);
    }

    // ---------- update ----------

    @Test
    @DisplayName("update: deve atualizar todos os campos editáveis do cartão")
    void update_shouldUpdateAllFields() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Roxinho", CARD_ID))
                .thenReturn(false);
        when(creditCardRepository.save(card)).thenReturn(card);
        stubResponseData(CARD_ID, "0.00", "0.00");

        CreditCardResponse response = creditCardService.update(USER_ID, CARD_ID, request("  Roxinho "));

        assertThat(card.getName()).isEqualTo("Roxinho");
        assertThat(card.getCreditLimit()).isEqualByComparingTo("8000.00");
        assertThat(card.getClosingDay()).isEqualTo(5);
        assertThat(card.getDueDay()).isEqualTo(12);
        assertThat(card.getColor()).isEqualTo("#000000");
        assertThat(card.getIcon()).isEqualTo("wallet");

        assertThat(response.id()).isEqualTo(CARD_ID);
        assertThat(response.name()).isEqualTo("Roxinho");
        assertThat(response.creditLimit()).isEqualByComparingTo("8000.00");
    }

    @Test
    @DisplayName("update: deve lançar ResourceNotFoundException quando o cartão não existe ou é de outro usuário")
    void update_shouldThrowNotFound_whenNotOwned() {
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.update(USER_ID, CARD_ID, request("Nubank")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(creditCardRepository, never())
                .existsByUserIdAndNameIgnoreCaseAndIdNot(anyLong(), anyString(), anyLong());
        verify(creditCardRepository, never()).save(any(CreditCard.class));
    }

    @Test
    @DisplayName("update: deve lançar ConflictException quando outro cartão já usa o nome e não alterar o cartão")
    void update_shouldThrowConflict_whenAnotherCardHasTheName() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Inter", CARD_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> creditCardService.update(USER_ID, CARD_ID, request("Inter")))
                .isInstanceOf(ConflictException.class)
                .hasMessage(CONFLICT);

        assertThat(card.getName()).isEqualTo("Nubank");
        assertThat(card.getCreditLimit()).isEqualByComparingTo("5000.00");
        verify(creditCardRepository, never()).save(any(CreditCard.class));
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete: deve excluir fisicamente o cartão sem transações nem pagamentos")
    void delete_shouldHardDelete_whenCardHasNoMovements() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(transactionRepository.existsByCreditCardIdAndDeletedFalse(CARD_ID)).thenReturn(false);
        when(paymentRepository.existsByCreditCardId(CARD_ID)).thenReturn(false);

        creditCardService.delete(USER_ID, CARD_ID);

        assertThat(card.isArchived()).isFalse();
        verify(creditCardRepository).delete(card);
        verify(creditCardRepository, never()).save(any(CreditCard.class));
    }

    @Test
    @DisplayName("delete: deve arquivar o cartão quando possui transações")
    void delete_shouldArchive_whenCardHasTransactions() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(transactionRepository.existsByCreditCardIdAndDeletedFalse(CARD_ID)).thenReturn(true);

        creditCardService.delete(USER_ID, CARD_ID);

        assertThat(card.isArchived()).isTrue();
        verify(creditCardRepository).save(card);
        verify(creditCardRepository, never()).delete(any(CreditCard.class));
    }

    @Test
    @DisplayName("delete: deve arquivar o cartão quando possui pagamentos de fatura")
    void delete_shouldArchive_whenCardHasPayments() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(transactionRepository.existsByCreditCardIdAndDeletedFalse(CARD_ID)).thenReturn(false);
        when(paymentRepository.existsByCreditCardId(CARD_ID)).thenReturn(true);

        creditCardService.delete(USER_ID, CARD_ID);

        assertThat(card.isArchived()).isTrue();
        verify(creditCardRepository).save(card);
        verify(creditCardRepository, never()).delete(any(CreditCard.class));
    }

    @Test
    @DisplayName("delete: deve lançar ResourceNotFoundException quando o cartão não existe ou é de outro usuário")
    void delete_shouldThrowNotFound_whenNotOwned() {
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.delete(USER_ID, CARD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);

        verify(creditCardRepository, never()).delete(any(CreditCard.class));
        verify(creditCardRepository, never()).save(any(CreditCard.class));
        verifyNoInteractions(transactionRepository, paymentRepository);
    }

    // ---------- getCardOrThrow ----------

    @Test
    @DisplayName("getCardOrThrow: deve retornar o cartão do usuário")
    void getCardOrThrow_shouldReturnCard() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);

        assertThat(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).isSameAs(card);
    }

    @Test
    @DisplayName("getCardOrThrow: deve lançar ResourceNotFoundException quando não encontrado")
    void getCardOrThrow_shouldThrowNotFound() {
        when(creditCardRepository.findByIdAndUserId(CARD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.getCardOrThrow(CARD_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND);
    }

    // ---------- cálculo da resposta ----------

    @Test
    @DisplayName("toResponse: limite usado = gastos - pagamentos; disponível = limite - usado")
    void response_shouldComputeUsedAndAvailableLimit() {
        CreditCard card = card(CARD_ID, "Nubank");
        stubFindOwned(card);
        when(creditCardRepository.save(card)).thenReturn(card);
        when(creditCardRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Nubank", CARD_ID))
                .thenReturn(false);
        stubResponseData(CARD_ID, "3200.75", "1200.25");

        CreditCardResponse response = creditCardService.update(USER_ID, CARD_ID,
                new CreditCardRequest("Nubank", new BigDecimal("5000.00"), 10, 17, null, null));

        assertThat(response.usedLimit()).isEqualByComparingTo("2000.50");
        assertThat(response.availableLimit()).isEqualByComparingTo("2999.50");
    }

    @Test
    @DisplayName("toResponse: deve consultar a fatura atual com referência no dia 1 do mês correto")
    void response_shouldResolveCurrentInvoiceReference() {
        CreditCard card = card(CARD_ID, "Nubank");
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of(card));
        stubResponseData(CARD_ID, "0.00", "0.00");

        creditCardService.list(USER_ID);

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(invoiceService).getInvoiceSummary(eq(card), captor.capture());

        // Após o dia de fechamento, a fatura atual é a do mês seguinte.
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate expected = today.withDayOfMonth(1);
        if (today.getDayOfMonth() > card.getClosingDay()) {
            expected = expected.plusMonths(1);
        }
        assertThat(captor.getValue().getDayOfMonth()).isEqualTo(1);
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("toResponse: deve expor cartão, cor, ícone, arquivamento e fatura atual")
    void response_shouldMapAllFields() {
        CreditCard card = card(CARD_ID, "Nubank");
        when(creditCardRepository.findAllByUserIdAndArchivedFalse(USER_ID)).thenReturn(List.of(card));
        stubResponseData(CARD_ID, "0.00", "0.00");

        CreditCardResponse response = creditCardService.list(USER_ID).getFirst();

        assertThat(response.id()).isEqualTo(CARD_ID);
        assertThat(response.name()).isEqualTo("Nubank");
        assertThat(response.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(response.closingDay()).isEqualTo(10);
        assertThat(response.dueDay()).isEqualTo(17);
        assertThat(response.color()).isEqualTo("#8A05BE");
        assertThat(response.icon()).isEqualTo("credit-card");
        assertThat(response.archived()).isFalse();
        assertThat(response.currentInvoice()).isEqualTo(INVOICE);
    }
}