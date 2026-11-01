package com.finance.account.controller;

import com.finance.account.dto.request.AccountRequest;
import com.finance.account.dto.response.AccountDeleteResponse;
import com.finance.account.dto.response.AccountListResponse;
import com.finance.account.dto.response.AccountResponse;
import com.finance.account.service.AccountService;
import com.finance.common.enums.AccountType;
import com.finance.common.exception.ConflictException;
import com.finance.common.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final String URL = "/api/v1/accounts";
    private static final Long USER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final AccountType TYPE = AccountType.values()[0];
    private static final String TYPE_NAME = TYPE.name();

    private static final String VALID_BODY = """
            {"name":"Nubank","type":"%s","initialBalance":1000.50,"color":"#8A05BE","icon":"bank"}"""
            .formatted(TYPE_NAME);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static AccountResponse sampleResponse() {
        return new AccountResponse(ACCOUNT_ID, "Nubank", TYPE, new BigDecimal("1000.50"), "#8A05BE", "bank", false);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("nome em branco", """
                        {"name":" ","type":"%s"}""".formatted(TYPE_NAME)),
                Arguments.of("nome com mais de 60 caracteres", """
                        {"name":"%s","type":"%s"}""".formatted("a".repeat(61), TYPE_NAME)),
                Arguments.of("tipo ausente", """
                        {"name":"Nubank"}"""),
                Arguments.of("tipo inexistente", """
                        {"name":"Nubank","type":"NAO_EXISTE"}"""),
                Arguments.of("saldo com 3 casas decimais", """
                        {"name":"Nubank","type":"%s","initialBalance":10.123}""".formatted(TYPE_NAME)),
                Arguments.of("cor inválida", """
                        {"name":"Nubank","type":"%s","color":"azul"}""".formatted(TYPE_NAME)),
                Arguments.of("ícone inválido", """
                        {"name":"Nubank","type":"%s","icon":"Icone Invalido"}""".formatted(TYPE_NAME)));
    }

    // ---------- GET /api/v1/accounts ----------

    @Test
    @DisplayName("GET /accounts: deve usar includeArchived=true por padrão e retornar a lista")
    void list_shouldDefaultIncludeArchivedToTrue() throws Exception {
        when(accountService.list(USER_ID, true)).thenReturn(
                new AccountListResponse(List.of(sampleResponse()), new BigDecimal("1000.50"), new BigDecimal("0.00")));

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.accounts[0].name").value("Nubank"))
                .andExpect(jsonPath("$.accounts[0].balance").value(1000.5))
                .andExpect(jsonPath("$.accounts[0].archived").value(false))
                .andExpect(jsonPath("$.totalBalance").value(1000.5))
                .andExpect(jsonPath("$.creditCardDebt").value(0.0));

        verify(accountService).list(USER_ID, true);
    }

    @Test
    @DisplayName("GET /accounts?includeArchived=false: deve repassar o parâmetro ao service")
    void list_shouldPassIncludeArchivedParameter() throws Exception {
        when(accountService.list(USER_ID, false)).thenReturn(
                new AccountListResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO));

        mockMvc.perform(get(URL).param("includeArchived", "false").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(0));

        verify(accountService).list(USER_ID, false);
    }

    // ---------- GET /api/v1/accounts/{id} ----------

    @Test
    @DisplayName("GET /accounts/{id}: deve retornar 200 com a conta")
    void getById_shouldReturnAccount() throws Exception {
        when(accountService.getById(USER_ID, ACCOUNT_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get(URL + "/" + ACCOUNT_ID).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.name").value("Nubank"))
                .andExpect(jsonPath("$.type").value(TYPE_NAME))
                .andExpect(jsonPath("$.color").value("#8A05BE"))
                .andExpect(jsonPath("$.icon").value("bank"));
    }

    @Test
    @DisplayName("GET /accounts/{id}: deve retornar 404 quando a conta não for encontrada")
    void getById_shouldReturnNotFound() throws Exception {
        when(accountService.getById(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(get(URL + "/99").with(user1()))
                .andExpect(status().isNotFound());
    }

    // ---------- POST /api/v1/accounts ----------

    @Test
    @DisplayName("POST /accounts: deve retornar 201 e repassar o request com o userId do JWT")
    void create_shouldReturnCreated() throws Exception {
        when(accountService.create(eq(USER_ID), any(AccountRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.name").value("Nubank"))
                .andExpect(jsonPath("$.balance").value(1000.5));

        ArgumentCaptor<AccountRequest> captor = ArgumentCaptor.forClass(AccountRequest.class);
        verify(accountService).create(eq(USER_ID), captor.capture());
        AccountRequest sent = captor.getValue();
        assertThat(sent.name()).isEqualTo("Nubank");
        assertThat(sent.type()).isEqualTo(TYPE);
        assertThat(sent.initialBalance()).isEqualByComparingTo("1000.50");
        assertThat(sent.color()).isEqualTo("#8A05BE");
        assertThat(sent.icon()).isEqualTo("bank");
    }

    @Test
    @DisplayName("POST /accounts: deve aceitar apenas nome e tipo (demais campos opcionais)")
    void create_shouldAcceptMinimalBody() throws Exception {
        when(accountService.create(eq(USER_ID), any(AccountRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carteira","type":"%s"}""".formatted(TYPE_NAME)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("POST /accounts: deve retornar 400 para body inválido")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /accounts: deve retornar 409 quando o nome já existe")
    void create_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(accountService.create(eq(USER_ID), any(AccountRequest.class)))
                .thenThrow(new ConflictException("Já existe uma conta com esse nome"));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    // ---------- PUT /api/v1/accounts/{id} ----------

    @Test
    @DisplayName("PUT /accounts/{id}: deve retornar 200 com a conta atualizada")
    void update_shouldReturnOk() throws Exception {
        when(accountService.update(eq(USER_ID), eq(ACCOUNT_ID), any(AccountRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put(URL + "/" + ACCOUNT_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.name").value("Nubank"));

        ArgumentCaptor<AccountRequest> captor = ArgumentCaptor.forClass(AccountRequest.class);
        verify(accountService).update(eq(USER_ID), eq(ACCOUNT_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Nubank");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("PUT /accounts/{id}: deve retornar 400 para body inválido")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + ACCOUNT_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("PUT /accounts/{id}: deve retornar 404 quando a conta não for encontrada")
    void update_shouldReturnNotFound() throws Exception {
        when(accountService.update(eq(USER_ID), eq(99L), any(AccountRequest.class)))
                .thenThrow(new ResourceNotFoundException("Conta não encontrada"));

        mockMvc.perform(put(URL + "/99").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /api/v1/accounts/{id} ----------

    @Test
    @DisplayName("DELETE /accounts/{id}: deve retornar 200 com archived=true quando a conta foi arquivada")
    void delete_shouldReturnArchivedTrue() throws Exception {
        when(accountService.delete(USER_ID, ACCOUNT_ID)).thenReturn(new AccountDeleteResponse(true));

        mockMvc.perform(delete(URL + "/" + ACCOUNT_ID).with(user1()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    @DisplayName("DELETE /accounts/{id}: deve retornar 200 com archived=false quando a conta foi excluída")
    void delete_shouldReturnArchivedFalse() throws Exception {
        when(accountService.delete(USER_ID, ACCOUNT_ID)).thenReturn(new AccountDeleteResponse(false));

        mockMvc.perform(delete(URL + "/" + ACCOUNT_ID).with(user1()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                get(URL),
                get(URL + "/" + ACCOUNT_ID),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                put(URL + "/" + ACCOUNT_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY),
                delete(URL + "/" + ACCOUNT_ID).with(csrf()));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(accountService);
    }
}