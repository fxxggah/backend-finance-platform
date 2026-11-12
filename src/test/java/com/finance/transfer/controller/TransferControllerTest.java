package com.finance.transfer.controller;

import com.finance.common.dto.PageResponse;
import com.finance.common.exception.BusinessRuleException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.transfer.dto.request.TransferRequest;
import com.finance.transfer.dto.response.TransferResponse;
import com.finance.transfer.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org. springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String URL = "/api/v1/transfers";
    private static final Long USER_ID = 1L;
    private static final Long TRANSFER_ID = 100L;
    private static final Long FROM_ACCOUNT_ID = 10L;
    private static final Long TO_ACCOUNT_ID = 20L;
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private static final String VALID_BODY = """
            {
                "fromAccountId": %d,
                "toAccountId": %d,
                "amount": 150.00,
                "date": "%s",
                "description": "Transferência para reserva"
            }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static TransferResponse sampleResponse() {
        return new TransferResponse(
                TRANSFER_ID,
                new TransferResponse.AccountSummary(FROM_ACCOUNT_ID, "Conta Corrente", "#1E90FF", "bank-icon"),
                new TransferResponse.AccountSummary(TO_ACCOUNT_ID, "Poupança", "#32CD32", "piggy-bank-icon"),
                new BigDecimal("150.00"),
                DATE,
                "Transferência para reserva"
        );
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("conta de origem ausente", """
                        {
                            "toAccountId": %d,
                            "amount": 150.00,
                            "date": "%s"
                        }""".formatted(TO_ACCOUNT_ID, DATE)),

                Arguments.of("conta de destino ausente", """
                        {
                            "fromAccountId": %d,
                            "amount": 150.00,
                            "date": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, DATE)),

                Arguments.of("valor ausente", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "date": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE)),

                Arguments.of("valor igual a zero", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "amount": 0.00,
                            "date": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE)),

                Arguments.of("valor negativo", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "amount": -50.00,
                            "date": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE)),

                Arguments.of("valor com mais de 2 casas decimais", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "amount": 150.005,
                            "date": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE)),

                Arguments.of("data ausente", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "amount": 150.00
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID)),

                Arguments.of("descrição com mais de 255 caracteres", """
                        {
                            "fromAccountId": %d,
                            "toAccountId": %d,
                            "amount": 150.00,
                            "date": "%s",
                            "description": "%s"
                        }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE, "a".repeat(256)))
        );
    }

    // ---------- GET /api/v1/transfers ----------

    @Test
    @DisplayName("GET /transfers: deve retornar a lista paginada de transferências sem filtros")
    void list_shouldReturnPaginatedTransfers_withoutFilters() throws Exception {
        PageResponse<TransferResponse> pageResponse = new PageResponse<>(
                List.of(sampleResponse()), 0, 20, 1L, 1
        );

        when(transferService.list(eq(USER_ID), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(TRANSFER_ID))
                .andExpect(jsonPath("$.content[0].fromAccount.id").value(FROM_ACCOUNT_ID))
                .andExpect(jsonPath("$.content[0].toAccount.id").value(TO_ACCOUNT_ID))
                .andExpect(jsonPath("$.content[0].amount").value(150.00))
                .andExpect(jsonPath("$.content[0].date").value(DATE.toString()))
                .andExpect(jsonPath("$.content[0].description").value("Transferência para reserva"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).list(eq(USER_ID), eq(null), eq(null), eq(null), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "date"));
    }

    @Test
    @DisplayName("GET /transfers: deve aplicar os parâmetros de filtro (from, to, accountId e pageable)")
    void list_shouldPassFilterParameters() throws Exception {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        PageResponse<TransferResponse> pageResponse = new PageResponse<>(List.of(), 1, 10, 0L, 0);

        when(transferService.list(eq(USER_ID), eq(from), eq(to), eq(FROM_ACCOUNT_ID), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(URL)
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31")
                        .param("accountId", FROM_ACCOUNT_ID.toString())
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "amount,asc")
                        .with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).list(eq(USER_ID), eq(from), eq(to), eq(FROM_ACCOUNT_ID), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "amount"));
    }

    @Test
    @DisplayName("GET /transfers: deve retornar 400 quando o formato das datas for inválido")
    void list_shouldReturnBadRequest_whenDateFormatIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("from", "01/03/2026").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    // ---------- GET /api/v1/transfers/{id} ----------

    @Test
    @DisplayName("GET /transfers/{id}: deve retornar os detalhes da transferência")
    void findById_shouldReturnTransferDetails() throws Exception {
        when(transferService.findById(USER_ID, TRANSFER_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get(URL + "/" + TRANSFER_ID).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSFER_ID))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.fromAccount.name").value("Conta Corrente"))
                .andExpect(jsonPath("$.toAccount.name").value("Poupança"));

        verify(transferService).findById(USER_ID, TRANSFER_ID);
    }

    @Test
    @DisplayName("GET /transfers/{id}: deve retornar 404 quando a transferência não existir")
    void findById_shouldReturnNotFound() throws Exception {
        when(transferService.findById(USER_ID, 999L))
                .thenThrow(new ResourceNotFoundException("Transferência não encontrada."));

        mockMvc.perform(get(URL + "/999").with(user1()))
                .andExpect(status().isNotFound());
    }

    // ---------- POST /api/v1/transfers ----------

    @Test
    @DisplayName("POST /transfers: deve criar a transferência e retornar 201")
    void create_shouldReturnCreated() throws Exception {
        when(transferService.create(eq(USER_ID), any(TransferRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRANSFER_ID))
                .andExpect(jsonPath("$.amount").value(150.00));

        ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService).create(eq(USER_ID), captor.capture());
        TransferRequest request = captor.getValue();
        assertThat(request.fromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(request.toAccountId()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(request.amount()).isEqualByComparingTo("150.00");
        assertThat(request.date()).isEqualTo(DATE);
        assertThat(request.description()).isEqualTo("Transferência para reserva");
    }

    @Test
    @DisplayName("POST /transfers: deve aceitar criação sem descrição")
    void create_shouldAcceptNullDescription() throws Exception {
        String bodyWithoutDescription = """
                {
                    "fromAccountId": %d,
                    "toAccountId": %d,
                    "amount": 100.00,
                    "date": "%s"
                }""".formatted(FROM_ACCOUNT_ID, TO_ACCOUNT_ID, DATE);

        when(transferService.create(eq(USER_ID), any(TransferRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutDescription))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("POST /transfers: deve retornar 400 para payloads inválidos")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("POST /transfers: deve retornar 422 em erro de regra de negócio (ex: contas iguais)")
    void create_shouldReturnUnprocessableEntity_onBusinessRuleException() throws Exception {
        when(transferService.create(eq(USER_ID), any(TransferRequest.class)))
                .thenThrow(new BusinessRuleException("A conta de origem não pode ser igual à conta de destino."));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- PUT /api/v1/transfers/{id} ----------

    @Test
    @DisplayName("PUT /transfers/{id}: deve atualizar a transferência e retornar 200")
    void update_shouldReturnOk() throws Exception {
        when(transferService.update(eq(USER_ID), eq(TRANSFER_ID), any(TransferRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put(URL + "/" + TRANSFER_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSFER_ID));

        ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferService).update(eq(USER_ID), eq(TRANSFER_ID), captor.capture());
        TransferRequest request = captor.getValue();
        assertThat(request.fromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(request.toAccountId()).isEqualTo(TO_ACCOUNT_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("PUT /transfers/{id}: deve retornar 400 para payloads inválidos")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + TRANSFER_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("PUT /transfers/{id}: deve retornar 404 quando a transferência não for encontrada")
    void update_shouldReturnNotFound() throws Exception {
        when(transferService.update(eq(USER_ID), eq(999L), any(TransferRequest.class)))
                .thenThrow(new ResourceNotFoundException("Transferência não encontrada."));

        mockMvc.perform(put(URL + "/999").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /api/v1/transfers/{id} ----------

    @Test
    @DisplayName("DELETE /transfers/{id}: deve excluir e retornar 204")
    void delete_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/" + TRANSFER_ID).with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(transferService).delete(USER_ID, TRANSFER_ID);
    }

    @Test
    @DisplayName("DELETE /transfers/{id}: deve retornar 404 quando a transferência não for encontrada")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Transferência não encontrada."))
                .when(transferService).delete(USER_ID, 999L);

        mockMvc.perform(delete(URL + "/999").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- Autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                get(URL),
                get(URL + "/" + TRANSFER_ID),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                put(URL + "/" + TRANSFER_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                delete(URL + "/" + TRANSFER_ID).with(csrf())
        );

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(transferService);
    }
}