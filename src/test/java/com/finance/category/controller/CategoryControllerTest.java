package com.finance.category.controller;

import com.finance.category.dto.request.CategoryRequest;
import com.finance.category.dto.request.CategoryUpdateRequest;
import com.finance.category.dto.response.CategoryResponse;
import com.finance.category.service.CategoryService;
import com.finance.common.enums.TransactionType;
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

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    private static final String URL = "/api/v1/categories";
    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final TransactionType TYPE = TransactionType.EXPENSE;
    private static final String TYPE_NAME = TYPE.name();

    private static final String VALID_CREATE_BODY = """
            {"name":"Mercado","type":"%s","color":"#3B82F6","icon":"shopping-cart"}"""
            .formatted(TYPE_NAME);

    private static final String VALID_UPDATE_BODY = """
            {"name":"Supermercado","color":"#000000","icon":"store"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    // O userId vem do subject do JWT
    private static RequestPostProcessor user1() {
        return jwt().jwt(j -> j.subject(String.valueOf(USER_ID)));
    }

    private static CategoryResponse sampleResponse() {
        return new CategoryResponse(CATEGORY_ID, "Mercado", TYPE, "#3B82F6", "shopping-cart");
    }

    static Stream<Arguments> invalidCreateBodies() {
        return Stream.of(
                Arguments.of("nome em branco", """
                        {"name":" ","type":"%s"}""".formatted(TYPE_NAME)),
                Arguments.of("nome com mais de 60 caracteres", """
                        {"name":"%s","type":"%s"}""".formatted("a".repeat(61), TYPE_NAME)),
                Arguments.of("tipo ausente", """
                        {"name":"Mercado"}"""),
                Arguments.of("tipo inexistente", """
                        {"name":"Mercado","type":"NAO_EXISTE"}"""),
                Arguments.of("cor inválida", """
                        {"name":"Mercado","type":"%s","color":"azul"}""".formatted(TYPE_NAME)),
                Arguments.of("cor hex com 3 dígitos", """
                        {"name":"Mercado","type":"%s","color":"#FFF"}""".formatted(TYPE_NAME)),
                Arguments.of("ícone inválido", """
                        {"name":"Mercado","type":"%s","icon":"Icone Invalido"}""".formatted(TYPE_NAME)));
    }

    static Stream<Arguments> invalidUpdateBodies() {
        return Stream.of(
                Arguments.of("nome em branco", """
                        {"name":" "}"""),
                Arguments.of("nome com mais de 60 caracteres", """
                        {"name":"%s"}""".formatted("a".repeat(61))),
                Arguments.of("cor inválida", """
                        {"name":"Mercado","color":"#12345"}"""),
                Arguments.of("ícone inválido", """
                        {"name":"Mercado","icon":"Icone Invalido"}"""));
    }

    // ---------- GET /api/v1/categories ----------

    @Test
    @DisplayName("GET /categories: deve retornar a lista sem filtro de tipo")
    void list_shouldReturnCategoriesWithoutTypeFilter() throws Exception {
        when(categoryService.list(USER_ID, null)).thenReturn(List.of(
                sampleResponse(),
                new CategoryResponse(11L, "Salário", TransactionType.INCOME, "#10B981", "wallet")));

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(CATEGORY_ID))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[0].type").value(TYPE_NAME))
                .andExpect(jsonPath("$[0].color").value("#3B82F6"))
                .andExpect(jsonPath("$[0].icon").value("shopping-cart"))
                .andExpect(jsonPath("$[1].type").value("INCOME"));

        verify(categoryService).list(USER_ID, null);
    }

    @Test
    @DisplayName("GET /categories?type=INCOME: deve repassar o filtro de tipo ao service")
    void list_shouldPassTypeParameter() throws Exception {
        when(categoryService.list(USER_ID, TransactionType.INCOME)).thenReturn(List.of(
                new CategoryResponse(11L, "Salário", TransactionType.INCOME, null, null)));

        mockMvc.perform(get(URL).param("type", "INCOME").with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Salário"));

        verify(categoryService).list(USER_ID, TransactionType.INCOME);
    }

    @Test
    @DisplayName("GET /categories: deve retornar lista vazia quando o usuário não tem categorias")
    void list_shouldReturnEmptyList() throws Exception {
        when(categoryService.list(USER_ID, null)).thenReturn(List.of());

        mockMvc.perform(get(URL).with(user1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /categories?type=INVALIDO: deve retornar 400")
    void list_shouldReturnBadRequest_whenTypeIsInvalid() throws Exception {
        mockMvc.perform(get(URL).param("type", "INVALIDO").with(user1()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    // ---------- POST /api/v1/categories ----------

    @Test
    @DisplayName("POST /categories: deve retornar 201 e repassar o request com o userId do JWT")
    void create_shouldReturnCreated() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Mercado"))
                .andExpect(jsonPath("$.type").value(TYPE_NAME));

        ArgumentCaptor<CategoryRequest> captor = ArgumentCaptor.forClass(CategoryRequest.class);
        verify(categoryService).create(eq(USER_ID), captor.capture());
        CategoryRequest sent = captor.getValue();
        assertThat(sent.name()).isEqualTo("Mercado");
        assertThat(sent.type()).isEqualTo(TYPE);
        assertThat(sent.color()).isEqualTo("#3B82F6");
        assertThat(sent.icon()).isEqualTo("shopping-cart");
    }

    @Test
    @DisplayName("POST /categories: deve aceitar apenas nome e tipo (cor e ícone opcionais)")
    void create_shouldAcceptMinimalBody() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mercado","type":"%s"}""".formatted(TYPE_NAME)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /categories: deve ignorar um userId enviado no body e usar o do JWT")
    void create_shouldIgnoreUserIdFromBody() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mercado","type":"%s","userId":999}""".formatted(TYPE_NAME)))
                .andExpect(status().isCreated());

        verify(categoryService).create(eq(USER_ID), any(CategoryRequest.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateBodies")
    @DisplayName("POST /categories: deve retornar 400 para body inválido")
    void create_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("POST /categories: deve retornar 409 quando o nome já existe para o tipo")
    void create_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(categoryService.create(eq(USER_ID), any(CategoryRequest.class)))
                .thenThrow(new ConflictException("Já existe uma categoria com esse nome para esse tipo"));

        mockMvc.perform(post(URL).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isConflict());
    }

    // ---------- PUT /api/v1/categories/{id} ----------

    @Test
    @DisplayName("PUT /categories/{id}: deve retornar 200 com a categoria atualizada")
    void update_shouldReturnOk() throws Exception {
        when(categoryService.update(eq(USER_ID), eq(CATEGORY_ID), any(CategoryUpdateRequest.class)))
                .thenReturn(new CategoryResponse(CATEGORY_ID, "Supermercado", TYPE, "#000000", "store"));

        mockMvc.perform(put(URL + "/" + CATEGORY_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Supermercado"))
                .andExpect(jsonPath("$.color").value("#000000"))
                .andExpect(jsonPath("$.icon").value("store"));

        ArgumentCaptor<CategoryUpdateRequest> captor = ArgumentCaptor.forClass(CategoryUpdateRequest.class);
        verify(categoryService).update(eq(USER_ID), eq(CATEGORY_ID), captor.capture());
        CategoryUpdateRequest sent = captor.getValue();
        assertThat(sent.name()).isEqualTo("Supermercado");
        assertThat(sent.color()).isEqualTo("#000000");
        assertThat(sent.icon()).isEqualTo("store");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateBodies")
    @DisplayName("PUT /categories/{id}: deve retornar 400 para body inválido")
    void update_shouldReturnBadRequest_whenBodyIsInvalid(String scenario, String body) throws Exception {
        mockMvc.perform(put(URL + "/" + CATEGORY_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("PUT /categories/{id}: deve retornar 400 quando o id não é numérico")
    void update_shouldReturnBadRequest_whenIdIsNotNumeric() throws Exception {
        mockMvc.perform(put(URL + "/abc").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("PUT /categories/{id}: deve retornar 404 quando a categoria não for encontrada")
    void update_shouldReturnNotFound() throws Exception {
        when(categoryService.update(eq(USER_ID), eq(99L), any(CategoryUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Categoria não encontrada"));

        mockMvc.perform(put(URL + "/99").with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /categories/{id}: deve retornar 409 quando o nome já existe para o tipo")
    void update_shouldReturnConflict_whenNameAlreadyExists() throws Exception {
        when(categoryService.update(eq(USER_ID), eq(CATEGORY_ID), any(CategoryUpdateRequest.class)))
                .thenThrow(new ConflictException("Já existe uma categoria com esse nome para esse tipo"));

        mockMvc.perform(put(URL + "/" + CATEGORY_ID).with(user1()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isConflict());
    }

    // ---------- DELETE /api/v1/categories/{id} ----------

    @Test
    @DisplayName("DELETE /categories/{id}: deve retornar 204 sem corpo e usar o userId do JWT")
    void delete_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/" + CATEGORY_ID).with(user1()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(USER_ID, CATEGORY_ID);
    }

    @Test
    @DisplayName("DELETE /categories/{id}: deve retornar 404 quando a categoria não for encontrada")
    void delete_shouldReturnNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Categoria não encontrada"))
                .when(categoryService).delete(USER_ID, 99L);

        mockMvc.perform(delete(URL + "/99").with(user1()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- autenticação ----------

    @Test
    @DisplayName("Todos os endpoints: devem retornar 401 sem autenticação")
    void allEndpoints_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        List<RequestBuilder> requests = List.of(
                get(URL),
                post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY),
                put(URL + "/" + CATEGORY_ID).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY),
                delete(URL + "/" + CATEGORY_ID).with(csrf()));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(categoryService);
    }
}