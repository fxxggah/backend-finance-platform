package com.finance.user.controller;

import com.finance.security.CurrentUser;
import com.finance.user.dto.response.UserResponse;
import com.finance.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String ME_URL = "/api/v1/users/me";
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CurrentUser currentUser;

    // ---------- GET /api/v1/users/me ----------

    @Test
    @DisplayName("GET /me: deve retornar 200 com os dados do usuário autenticado")
    void getMe_shouldReturnOkWithUser() throws Exception {
        when(currentUser.id(any(Jwt.class))).thenReturn(USER_ID);
        when(userService.get(USER_ID)).thenReturn(
                new UserResponse(USER_ID, "João Silva", "joao@example.com", "https://img.example.com/joao.png"));

        mockMvc.perform(get(ME_URL).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.pictureUrl").value("https://img.example.com/joao.png"));

        verify(userService).get(USER_ID);
    }

    @Test
    @DisplayName("GET /me: deve retornar 401 quando não houver autenticação")
    void getMe_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    // ---------- DELETE /api/v1/users/me ----------

    @Test
    @DisplayName("DELETE /me: deve retornar 204 e excluir o usuário autenticado")
    void deleteMe_shouldReturnNoContentAndDeleteUser() throws Exception {
        when(currentUser.id(any(Jwt.class))).thenReturn(USER_ID);

        mockMvc.perform(delete(ME_URL).with(jwt()).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).delete(USER_ID);
    }

    @Test
    @DisplayName("DELETE /me: deve retornar 401 quando não houver autenticação")
    void deleteMe_shouldReturnUnauthorized_whenNotAuthenticated() throws Exception {
        mockMvc.perform(delete(ME_URL).with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).delete(any());
    }
}