package com.finance.transaction.controller;

import com.finance.common.dto.PageResponse;
import com.finance.common.enums.TransactionType;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.transaction.dto.request.TransactionFilter;
import com.finance.transaction.dto.request.TransactionRequest;
import com.finance.transaction.dto.response.TransactionResponse;
import com.finance.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String URL = "/api/v1/transactions";
    private static final Long USER_ID = 1L;
    private static final Long TX_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private static final String VALID_BODY = """
            {"type":"EXPENSE","amount":150.00,"categoryId":10,"date":"2026-03-10",
             "description":"Mercado","notes":"Compra do mês","accountId":5}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static TransactionResponse sampleResponse() {
        return new TransactionResponse(
                TX_ID, TransactionType.EXPENSE, new BigDecimal("150.00"), DATE, "Mercado", "Compra do mês",
                new TransactionResponse.CategoryInfo(10L, "Mercado", "#3B82F6", "tag"),
                new TransactionResponse.AccountInfo(5L, "Nubank"),
                null, null, null);
    }

    private static PageResponse<TransactionResponse> samplePage() {
        return PageResponse.from(new PageImpl<>(List.of(sampleResponse())));
    }

    private static String body(String amount, String extra) {
        return """
                {"type":"EXPENSE","amount":%s,"categoryId":10,"date":"2026-03-10","accountId":5%s}"""
                .formatted(amount, extra);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("tipo ausente", """
                        {"amount":10.00,"categoryId":10,"date":"2026-03-10","accountId":5}"""),
                Arguments.of("tipo inexistente", """
                        {"type":"NAO_EXISTE","amount":10.00,"categoryId":10,"date":"2026-03-10","accountId":5}"""),
                Arguments.of("valor zero", body("0", "")),
                Arguments.of("valor negativo", body("-5.00", "")),
                Arguments.of("valor com 3 casas decimais", body("10.123", "")),
                Arguments.of("valor acima do máximo", body("1000000000.00", "")),
                Arguments.of("categoria ausente", """
                        {"type":"EXPENSE","amount":10.00,"date":"2026-03-10","accountId":5}"""),
                Arguments.of("data ausente", """
                        {"type":"EXPENSE","amount":10.00,"categoryId":10,"accountId":5}"""),
                Arguments.of("data em formato inválido", """
                        {"type":"EXPENSE","amount":10.00,"categoryId":10,"date":"10/03/2026","accountId":5}"""),
                Arguments.of("descrição com mais de 255 caracteres", body("10.00", ",\"description\":\"" + "a".repeat(256) + "\"")),
                Arguments.of("notas com mais de 500 caracteres", body("10.00", ",\"notes\":\"" + "a".repeat(501) + "\"")),
                Arguments.of("parcelas menor que 1", body("10.00", ",\"installments\":0")),
                Arguments.of("parcelas maiores que 24", body("10.00", ",\"installments\":25")),
                Arguments.of("corpo vazio", "{}"));
    }

    // ---------- GET /api/v1/transactions ----------

    @Test
    @DisplayName("GET /transactions: deve retornar a página usando o userId do JWT e paginação padrão")
    void list_shouldReturnPageWithDefaults() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), any(Pageable.class)))
                .thenReturn(samplePage());

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(TX_ID))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.content[0].amount").value(150.00))
                .andExpect(jsonPath("$.content[0].category.name").value("Mercado"))
                .andExpect(jsonPath("$.content[0].account.name").value("Nubank"))
                .andExpect(jsonPath("$.content[0].creditCard").doesNotExist())
                .andExpect(jsonPath("$.content[0].installment").doesNotExist());

        ArgumentCaptor<TransactionFilter> filterCaptor = ArgumentCaptor.forClass(TransactionFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).list(eq(USER_ID), filterCaptor.capture(), pageableCaptor.capture());

        TransactionFilter filter = filterCaptor.getValue();
        assertThat(filter.from()).isNull();
        assertThat(filter.to()).isNull();
        assertThat(filter.type()).isNull();
        assertThat(filter.categoryId()).isNull();
        assertThat(filter.accountId()).isNull();
        assertThat(filter.creditCardId()).isNull();
        assertThat(filter.q()).isNull();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("GET /transactions: deve repassar todos os filtros ao service")
    void list_shouldPassAllFilters() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), any(Pageable.class)))
                .thenReturn(samplePage());

        mockMvc.perform(get(URL).with(user1())
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("type", "EXPENSE")
                        .param("categoryId", "10")
                        .param("accountId", "5")
                        .param("creditCardId", "7")
                        .param("q", "mercado"))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionFilter> captor = ArgumentCaptor.forClass(TransactionFilter.class);
        verify(transactionService).list(eq(USER_ID), captor.capture(), any(Pageable.class));
        TransactionFilter filter = captor.getValue();
        assertThat(filter.from()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(filter.to()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(filter.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(filter.categoryId()).isEqualTo(10L);
        assertThat(filter.accountId()).isEqualTo(5L);
        assertThat(filter.creditCardId()).isEqualTo(7L);
        assertThat(filter.q()).isEqualTo("mercado");
    }

    @Test
    @DisplayName("GET /transactions: deve repassar page, size e sort ao service")
    void list_shouldPassPagination() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), any(Pageable.class)))
                .thenReturn(samplePage());

        mockMvc.perform(get(URL).with(user1())
                        .param("page", "2").param("size", "50").param("sort", "amount,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).list(eq(USER_ID), any(TransactionFilter.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("GET /transactions: deve retornar 400 quando o tipo é inválido")
    void list_shouldReturnBadRequest_whenTypeIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("type", "INVALIDO").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("GET /transactions: deve retornar 400 quando a data tem formato inválido")
    void list_shouldReturnBadRequest_whenDateIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("from", "01/03/2026").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("GET /transactions: deve retornar 400 quando categoryId não é numérico")
    void list_shouldReturnBadRequest_whenCategoryIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL).param("categoryId", "abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("GET /transactions: deve retornar erro de cliente quando a regra de negócio é violada")
    void list_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), any(Pageable.class)))
                .thenThrow(new BusinessRuleException("A data inicial não pode ser posterior à data final"));

        mockMvc.perform(get(URL).param("from", "2026-03-31").param("to", "2026-03-01").with(user1()))
                .andExpect(status().is4xxClientError());
    }

    // ---------- GET /api/v1/transactions/{id} ----------

    @Test
    @DisplayName("GET /transactions/{id}: deve retornar a transação")
    void findById_shouldReturnTransaction() throws Exception {
        when(transactionService.findById(USER_ID, TX_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get(URL + "/" + TX_ID).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.date").value("2026-03-10"))
                .andExpect(jsonPath("$.category.color").value("#3B82F6"));

        verify(transactionService).findById(USER_ID, TX_ID);
    }

    @Test
    @DisplayName("GET /transactions/{id}: deve retornar 404 quando a transação não for encontrada")
    void findById_shouldReturnNotFound() throws Exception {
        when(transactionService.findById(USER_ID, 99L))
                .thenThrow(new ResourceNotFoundException("Transação não encontrada"));

        mockMvc.perform(get(URL + "/99").with(user1()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /transactions/{id}: deve retornar 400 quando o id não é numérico")
    void findById_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get(URL + "/abc").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    // ---------- POST /api/v1/transactions ----------

    @Test
    @DisplayName("POST /transactions: deve retornar 201 e repassar o request com o userId do JWT")
    void create_shouldReturnCreated() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.type").value("EXPENSE"));

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(USER_ID), captor.capture());
        TransactionRequest sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(sent.amount()).isEqualByComparingTo("150.00");
        assertThat(sent.categoryId()).isEqualTo(10L);
        assertThat(sent.date()).isEqualTo(DATE);
        assertThat(sent.description()).isEqualTo("Mercado");
        assertThat(sent.notes()).isEqualTo("Compra do mês");
        assertThat(sent.accountId()).isEqualTo(5L);
        assertThat(sent.creditCardId()).isNull();
        assertThat(sent.installments()).isNull();
    }

    @Test
    @DisplayName("POST /transactions: deve aceitar apenas os campos obrigatórios")
    void create_shouldAcceptMinimalBody() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("0.01", "")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /transactions: deve aceitar compra no cartão com parcelas")
    void create_shouldAcceptCreditCardWithInstallments() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXPENSE","amount":999999999.99,"categoryId":10,"date":"2026-03-10",
                                 "creditCardId":7,"installments":24}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().creditCardId()).isEqualTo(7L);
        assertThat(captor.getValue().installments()).isEqualTo(24);
    }

    @Test
    @DisplayName("POST /transactions: deve ignorar um userId enviado no body e usar o do JWT")
    void create_shouldIgnoreUserIdFromBody() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", ",\"userId\":999")))
                .andExpect(status().isCreated());

        verify(transactionService).create(eq(USER_ID), any(TransactionRequest.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("POST /transactions: deve retornar 400 para body inválido")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("POST /transactions: deve retornar 400 para JSON malformado")
    void create_shouldReturnBadRequest_whenJsonIsMalformed() throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalido"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("POST /transactions: deve retornar 404 quando categoria, conta ou cartão não forem encontrados")
    void create_shouldReturnNotFound() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Categoria não encontrada"));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /transactions: deve retornar erro de cliente quando a regra de negócio é violada")
    void create_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class)))
                .thenThrow(new BusinessRuleException("Cartão de crédito aceita apenas despesas"));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().is4xxClientError());
    }

    // ---------- PUT /api/v1/transactions/{id} ----------

    @Test
    @DisplayName("PUT /transactions/{id}: deve retornar 200 com a transação atualizada")
    void update_shouldReturnOk() throws Exception {
        when(transactionService.update(eq(USER_ID), eq(TX_ID), any(TransactionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put(URL + "/" + TX_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_ID))
                .andExpect(jsonPath("$.description").value("Mercado"));

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).update(eq(USER_ID), eq(TX_ID), captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("150.00");
        assertThat(captor.getValue().accountId()).isEqualTo(5L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("PUT /transactions/{id}: deve retornar 400 para body inválido")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + TX_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("PUT /transactions/{id}: deve retornar 400 quando o id não é numérico")
    void update_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(put(URL + "/abc").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @DisplayName("PUT /transactions/{id}: deve retornar 404 quando a transação não for encontrada")
    void update_shouldReturnNotFound() throws Exception {
        when(transactionService.update(eq(USER_ID), eq(99L), any(TransactionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Transação não encontrada"));

        mockMvc.perform(put(URL + "/99").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /transactions/{id}: deve retornar erro de cliente quando a regra de negócio é violada")
    void update_shouldReturnClientError_whenBusinessRuleFails() throws Exception {
        when(transactionService.update(eq(USER_ID), eq(TX_ID), any(TransactionRequest.class)))
                .thenThrow(new BusinessRuleException("O tipo não pode ser alterado em compra parcelada"));

        mockMvc.perform(put(URL + "/" + TX_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().is4xxClientError());
    }

    // ---------- DELETE /api/v1/transactions/{id} ----------

    @Test
    @DisplayName("DELETE /transactions/{id}: deve retornar 204 sem corpo e usar o userId do JWT")
    void delete_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/" + TX_ID).with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(USER_ID, TX_ID);
    }

    @Test
    @DisplayName("DELETE /transactions/{id}: deve retornar 404 quando a transação não for encontrada")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Transação não encontrada"))
                .when(transactionService).delete(USER_ID, 99L);

        mockMvc.perform(delete(URL + "/99").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /transactions/{id}: deve retornar 400 quando o id não é numérico")
    void delete_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(delete(URL + "/abc").with(user1()).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                get(URL),
                get(URL + "/" + TX_ID),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                put(URL + "/" + TX_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                delete(URL + "/" + TX_ID).with(csrf()));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(transactionService);
    }
}