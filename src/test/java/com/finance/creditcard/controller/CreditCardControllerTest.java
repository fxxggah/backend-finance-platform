package com.finance.creditcard.controller;

import com.finance.common.enums.InvoiceStatus;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.creditcard.dto.request.CreditCardRequest;
import com.finance.creditcard.dto.request.InvoicePaymentRequest;
import com.finance.creditcard.dto.response.CreditCardResponse;
import com.finance.creditcard.dto.response.InvoiceResponse;
import com.finance.creditcard.entity.CreditCard;
import com.finance.creditcard.service.CreditCardService;
import com.finance.creditcard.service.InvoiceService;
import com.finance.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreditCardController.class)
class CreditCardControllerTest {

    private static final String URL = "/api/v1/credit-cards";
    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 10L;
    private static final String REFERENCE = "2026-10";
    private static final Long PAYMENT_ID = 55L;

    private static final String VALID_CARD_BODY = """
            {"name":"Nubank","creditLimit":5000.00,"closingDay":10,"dueDay":17,"color":"#8A05BE","icon":"credit-card"}""";

    private static final String VALID_PAYMENT_BODY = """
            {"accountId":3,"amount":500.00,"date":"2026-10-15"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditCardService creditCardService;

    @MockitoBean
    private InvoiceService invoiceService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static InvoiceResponse sampleInvoice() {
        return new InvoiceResponse(REFERENCE, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 17),
                new BigDecimal("1500.00"), new BigDecimal("500.00"), InvoiceStatus.PARTIALLY_PAID);
    }

    private static CreditCardResponse sampleResponse() {
        return new CreditCardResponse(CARD_ID, "Nubank", new BigDecimal("5000.00"), new BigDecimal("1000.00"),
                new BigDecimal("4000.00"), 10, 17, "#8A05BE", "credit-card", false, sampleInvoice());
    }

    private static CreditCard sampleCard() {
        CreditCard card = new CreditCard(new User("google-1", "joao@example.com", "João", null),
                "Nubank", new BigDecimal("5000.00"), 10, 17, null, null);
        card.setId(CARD_ID);
        return card;
    }

    static Stream<Arguments> invalidCardBodies() {
        return Stream.of(
                Arguments.of("nome em branco", """
                        {"name":" ","creditLimit":5000.00,"closingDay":10,"dueDay":17}"""),
                Arguments.of("nome ausente", """
                        {"creditLimit":5000.00,"closingDay":10,"dueDay":17}"""),
                Arguments.of("limite ausente", """
                        {"name":"Nubank","closingDay":10,"dueDay":17}"""),
                Arguments.of("limite zero", """
                        {"name":"Nubank","creditLimit":0,"closingDay":10,"dueDay":17}"""),
                Arguments.of("limite negativo", """
                        {"name":"Nubank","creditLimit":-1,"closingDay":10,"dueDay":17}"""),
                Arguments.of("dia de fechamento ausente", """
                        {"name":"Nubank","creditLimit":5000.00,"dueDay":17}"""),
                Arguments.of("dia de fechamento menor que 1", """
                        {"name":"Nubank","creditLimit":5000.00,"closingDay":0,"dueDay":17}"""),
                Arguments.of("dia de fechamento maior que 28", """
                        {"name":"Nubank","creditLimit":5000.00,"closingDay":29,"dueDay":17}"""),
                Arguments.of("dia de vencimento ausente", """
                        {"name":"Nubank","creditLimit":5000.00,"closingDay":10}"""),
                Arguments.of("dia de vencimento menor que 1", """
                        {"name":"Nubank","creditLimit":5000.00,"closingDay":10,"dueDay":0}"""),
                Arguments.of("dia de vencimento maior que 28", """
                        {"name":"Nubank","creditLimit":5000.00,"closingDay":10,"dueDay":29}"""));
    }

    static Stream<Arguments> invalidPaymentBodies() {
        return Stream.of(
                Arguments.of("conta ausente", """
                        {"amount":500.00,"date":"2026-10-15"}"""),
                Arguments.of("valor ausente", """
                        {"accountId":3,"date":"2026-10-15"}"""),
                Arguments.of("valor zero", """
                        {"accountId":3,"amount":0,"date":"2026-10-15"}"""),
                Arguments.of("valor negativo", """
                        {"accountId":3,"amount":-10.00,"date":"2026-10-15"}"""),
                Arguments.of("data ausente", """
                        {"accountId":3,"amount":500.00}"""),
                Arguments.of("data em formato inválido", """
                        {"accountId":3,"amount":500.00,"date":"15/10/2026"}"""));
    }

    // ---------- GET /api/v1/credit-cards ----------

    @Test
    @DisplayName("GET /credit-cards: deve retornar a lista de cartões com a fatura atual")
    void list_shouldReturnCards() throws Exception {
        when(creditCardService.list(USER_ID)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(CARD_ID))
                .andExpect(jsonPath("$[0].name").value("Nubank"))
                .andExpect(jsonPath("$[0].creditLimit").value(5000.0))
                .andExpect(jsonPath("$[0].usedLimit").value(1000.0))
                .andExpect(jsonPath("$[0].availableLimit").value(4000.0))
                .andExpect(jsonPath("$[0].closingDay").value(10))
                .andExpect(jsonPath("$[0].dueDay").value(17))
                .andExpect(jsonPath("$[0].color").value("#8A05BE"))
                .andExpect(jsonPath("$[0].icon").value("credit-card"))
                .andExpect(jsonPath("$[0].archived").value(false))
                .andExpect(jsonPath("$[0].currentInvoice.reference").value(REFERENCE))
                .andExpect(jsonPath("$[0].currentInvoice.status").value("PARTIALLY_PAID"));

        verify(creditCardService).list(USER_ID);
    }

    @Test
    @DisplayName("GET /credit-cards: deve retornar lista vazia quando o usuário não tem cartões")
    void list_shouldReturnEmptyList() throws Exception {
        when(creditCardService.list(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- POST /api/v1/credit-cards ----------

    @Test
    @DisplayName("POST /credit-cards: deve retornar 201 e repassar o request com o userId do JWT")
    void create_shouldReturnCreated() throws Exception {
        when(creditCardService.create(eq(USER_ID), any(CreditCardRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.name").value("Nubank"));

        ArgumentCaptor<CreditCardRequest> captor = ArgumentCaptor.forClass(CreditCardRequest.class);
        verify(creditCardService).create(eq(USER_ID), captor.capture());
        CreditCardRequest sent = captor.getValue();
        assertThat(sent.name()).isEqualTo("Nubank");
        assertThat(sent.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(sent.closingDay()).isEqualTo(10);
        assertThat(sent.dueDay()).isEqualTo(17);
        assertThat(sent.color()).isEqualTo("#8A05BE");
        assertThat(sent.icon()).isEqualTo("credit-card");
    }

    @Test
    @DisplayName("POST /credit-cards: deve aceitar apenas campos obrigatórios (cor e ícone opcionais)")
    void create_shouldAcceptMinimalBody() throws Exception {
        when(creditCardService.create(eq(USER_ID), any(CreditCardRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nubank","creditLimit":5000.00,"closingDay":10,"dueDay":17}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /credit-cards: deve ignorar um userId enviado no body e usar o do JWT")
    void create_shouldIgnoreUserIdFromBody() throws Exception {
        when(creditCardService.create(eq(USER_ID), any(CreditCardRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nubank","creditLimit":5000.00,"closingDay":10,"dueDay":17,"userId":999}"""))
                .andExpect(status().isCreated());

        verify(creditCardService).create(eq(USER_ID), any(CreditCardRequest.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCardBodies")
    @DisplayName("POST /credit-cards: deve retornar 400 para body inválido")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService);
    }

    @Test
    @DisplayName("POST /credit-cards: deve retornar 409 quando o nome já existe")
    void create_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(creditCardService.create(eq(USER_ID), any(CreditCardRequest.class)))
                .thenThrow(new ConflictException("Já existe um cartão com este nome."));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isConflict());
    }

    // ---------- PUT /api/v1/credit-cards/{id} ----------

    @Test
    @DisplayName("PUT /credit-cards/{id}: deve retornar 200 com o cartão atualizado")
    void update_shouldReturnOk() throws Exception {
        when(creditCardService.update(eq(USER_ID), eq(CARD_ID), any(CreditCardRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put(URL + "/" + CARD_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.name").value("Nubank"));

        ArgumentCaptor<CreditCardRequest> captor = ArgumentCaptor.forClass(CreditCardRequest.class);
        verify(creditCardService).update(eq(USER_ID), eq(CARD_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Nubank");
        assertThat(captor.getValue().closingDay()).isEqualTo(10);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCardBodies")
    @DisplayName("PUT /credit-cards/{id}: deve retornar 400 para body inválido")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + CARD_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService);
    }

    @Test
    @DisplayName("PUT /credit-cards/{id}: deve retornar 400 quando o id não é numérico")
    void update_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(put(URL + "/abc").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService);
    }

    @Test
    @DisplayName("PUT /credit-cards/{id}: deve retornar 404 quando o cartão não for encontrado")
    void update_shouldReturnNotFound() throws Exception {
        when(creditCardService.update(eq(USER_ID), eq(99L), any(CreditCardRequest.class)))
                .thenThrow(new ResourceNotFoundException("Cartão de crédito não encontrado."));

        mockMvc.perform(put(URL + "/99").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /credit-cards/{id}: deve retornar 409 quando o nome já existe")
    void update_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(creditCardService.update(eq(USER_ID), eq(CARD_ID), any(CreditCardRequest.class)))
                .thenThrow(new ConflictException("Já existe um cartão com este nome."));

        mockMvc.perform(put(URL + "/" + CARD_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY))
                .andExpect(status().isConflict());
    }

    // ---------- DELETE /api/v1/credit-cards/{id} ----------

    @Test
    @DisplayName("DELETE /credit-cards/{id}: deve retornar 200 (sem @ResponseStatus no controller) e usar o userId do JWT")
    void delete_shouldReturnOk() throws Exception {
        mockMvc.perform(delete(URL + "/" + CARD_ID).with(user1()).with(csrf()))
                .andExpect(status().isOk());

        verify(creditCardService).delete(USER_ID, CARD_ID);
    }

    @Test
    @DisplayName("DELETE /credit-cards/{id}: deve retornar 404 quando o cartão não for encontrado")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Cartão de crédito não encontrado."))
                .when(creditCardService).delete(USER_ID, 99L);

        mockMvc.perform(delete(URL + "/99").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/v1/credit-cards/{id}/invoices ----------

    @Test
    @DisplayName("GET /credit-cards/{id}/invoices: deve retornar as faturas do cartão do usuário")
    void listInvoices_shouldReturnInvoices() throws Exception {
        CreditCard card = sampleCard();
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(card);
        when(invoiceService.listInvoices(card)).thenReturn(List.of(sampleInvoice()));

        mockMvc.perform(get(URL + "/" + CARD_ID + "/invoices").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reference").value(REFERENCE))
                .andExpect(jsonPath("$[0].closingDate").value("2026-10-10"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-17"))
                .andExpect(jsonPath("$[0].total").value(1500.0))
                .andExpect(jsonPath("$[0].paid").value(500.0))
                .andExpect(jsonPath("$[0].status").value("PARTIALLY_PAID"));

        verify(creditCardService).getCardOrThrow(CARD_ID, USER_ID);
        verify(invoiceService).listInvoices(card);
    }

    @Test
    @DisplayName("GET /credit-cards/{id}/invoices: deve retornar 404 quando o cartão não for do usuário")
    void listInvoices_shouldReturnNotFound() throws Exception {
        when(creditCardService.getCardOrThrow(99L, USER_ID))
                .thenThrow(new ResourceNotFoundException("Cartão de crédito não encontrado."));

        mockMvc.perform(get(URL + "/99/invoices").with(user1()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("GET /credit-cards/{id}/invoices: deve retornar 400 quando o id não é numérico")
    void listInvoices_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/abc/invoices").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService, invoiceService);
    }

    // ---------- POST /api/v1/credit-cards/{id}/invoices/{reference}/payments ----------

    @Test
    @DisplayName("POST .../payments: deve retornar 201 e repassar usuário, cartão, referência e request")
    void payInvoice_shouldReturnCreated() throws Exception {
        CreditCard card = sampleCard();
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(card);

        mockMvc.perform(post(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments")
                        .with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<InvoicePaymentRequest> captor = ArgumentCaptor.forClass(InvoicePaymentRequest.class);
        verify(invoiceService).payInvoice(eq(USER_ID), eq(card), eq(REFERENCE), captor.capture());
        InvoicePaymentRequest sent = captor.getValue();
        assertThat(sent.accountId()).isEqualTo(3L);
        assertThat(sent.amount()).isEqualByComparingTo("500.00");
        assertThat(sent.date()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPaymentBodies")
    @DisplayName("POST .../payments: deve retornar 400 para body inválido")
    void payInvoice_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments")
                        .with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService, invoiceService);
    }

    @Test
    @DisplayName("POST .../payments: deve retornar 404 quando o cartão não for do usuário")
    void payInvoice_shouldReturnNotFound_whenCardNotOwned() throws Exception {
        when(creditCardService.getCardOrThrow(99L, USER_ID))
                .thenThrow(new ResourceNotFoundException("Cartão de crédito não encontrado."));

        mockMvc.perform(post(URL + "/99/invoices/" + REFERENCE + "/payments")
                        .with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isNotFound());

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("POST .../payments: deve retornar 404 quando a conta de origem não for encontrada")
    void payInvoice_shouldReturnNotFound_whenAccountNotFound() throws Exception {
        CreditCard card = sampleCard();
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(card);
        doThrow(new ResourceNotFoundException("Conta de origem não encontrada."))
                .when(invoiceService).payInvoice(eq(USER_ID), eq(card), eq(REFERENCE), any(InvoicePaymentRequest.class));

        mockMvc.perform(post(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments")
                        .with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST .../payments: deve retornar erro 4xx quando uma regra de negócio é violada")
    void payInvoice_shouldReturnClientError_whenBusinessRuleIsViolated() throws Exception {
        CreditCard card = sampleCard();
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(card);
        doThrow(new BusinessRuleException("Não é possível pagar uma fatura aberta."))
                .when(invoiceService).payInvoice(eq(USER_ID), eq(card), eq(REFERENCE), any(InvoicePaymentRequest.class));

        // O status exato depende do handler global (400/422); ajuste se quiser fixar.
        mockMvc.perform(post(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments")
                        .with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().is4xxClientError());
    }

    // ---------- DELETE /api/v1/credit-cards/{id}/invoices/{reference}/payments/{paymentId} ----------

    @Test
    @DisplayName("DELETE .../payments/{paymentId}: deve retornar 204 após validar o cartão e excluir o pagamento")
    void deletePayment_shouldReturnNoContent() throws Exception {
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(sampleCard());

        mockMvc.perform(delete(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments/" + PAYMENT_ID)
                        .with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(creditCardService).getCardOrThrow(CARD_ID, USER_ID);
        verify(invoiceService).deletePayment(USER_ID, PAYMENT_ID);
    }

    @Test
    @DisplayName("DELETE .../payments/{paymentId}: deve retornar 404 quando o cartão não for do usuário")
    void deletePayment_shouldReturnNotFound_whenCardNotOwned() throws Exception {
        when(creditCardService.getCardOrThrow(99L, USER_ID))
                .thenThrow(new ResourceNotFoundException("Cartão de crédito não encontrado."));

        mockMvc.perform(delete(URL + "/99/invoices/" + REFERENCE + "/payments/" + PAYMENT_ID)
                        .with(user1()).with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("DELETE .../payments/{paymentId}: deve retornar 404 quando o pagamento não for encontrado")
    void deletePayment_shouldReturnNotFound_whenPaymentNotFound() throws Exception {
        when(creditCardService.getCardOrThrow(CARD_ID, USER_ID)).thenReturn(sampleCard());
        doThrow(new ResourceNotFoundException("Pagamento não encontrado."))
                .when(invoiceService).deletePayment(USER_ID, 999L);

        mockMvc.perform(delete(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments/999")
                        .with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE .../payments/{paymentId}: deve retornar 400 quando o paymentId não é numérico")
    void deletePayment_shouldReturnBadRequest_whenPaymentIdIsNotNumeric() throws Exception {
        mockMvc.perform(delete(URL + "/" + CARD_ID + "/invoices/" + REFERENCE + "/payments/abc")
                        .with(user1()).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(creditCardService, invoiceService);
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        String invoicesUrl = URL + "/" + CARD_ID + "/invoices";
        List<RequestBuilder> requests = List.of(
                get(URL),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_CARD_BODY),
                put(URL + "/" + CARD_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CARD_BODY),
                delete(URL + "/" + CARD_ID).with(csrf()),
                get(invoicesUrl),
                post(invoicesUrl + "/" + REFERENCE + "/payments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PAYMENT_BODY),
                delete(invoicesUrl + "/" + REFERENCE + "/payments/" + PAYMENT_ID).with(csrf()));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(creditCardService, invoiceService);
    }
}