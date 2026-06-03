package com.miniagoda.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagoda.auth.dto.LoginRequest;
import com.miniagoda.auth.dto.LoginResponse;
import com.miniagoda.auth.dto.RegisterRequest;
import com.miniagoda.auth.dto.RegisterResponse;
import com.miniagoda.auth.security.JwtAuthFilter;
import com.miniagoda.auth.service.AuthService;
import com.miniagoda.auth.util.JwtUtil;
import com.miniagoda.common.config.JpaAuditingConfig;
import com.miniagoda.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.FilterType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = {
        UserDetailsServiceAutoConfiguration.class,
        SecurityAutoConfiguration.class
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JpaAuditingConfig.class
    )
)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;

    // JwtAuthFilter dependencies
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean RedisTemplate<String, String> redisTemplate;

    // ── POST /api/v1/auth/register ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        private final String URL = "/api/v1/auth/register";

        @Test
        @DisplayName("Returns 200 with access token on valid request")
        void returns200OnSuccess() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "alice@example.com", "password123");

            when(authService.register(any(), any()))
                    .thenReturn(new RegisterResponse("access-token-xyz"));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token-xyz"));
        }

        @Test
        @DisplayName("Returns 400 when firstName is blank")
        void returns400WhenFirstNameBlank() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "", "Smith", "alice@example.com", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when lastName is blank")
        void returns400WhenLastNameBlank() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "", "alice@example.com", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when email is blank")
        void returns400WhenEmailBlank() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when email format is invalid")
        void returns400WhenEmailInvalid() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "not-an-email", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when password is shorter than 8 characters")
        void returns400WhenPasswordTooShort() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "alice@example.com", "short");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when password is blank")
        void returns400WhenPasswordBlank() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "alice@example.com", "");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Is publicly accessible without authentication")
        void isPublicEndpoint() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "Alice", "Smith", "alice@example.com", "password123");

            when(authService.register(any(), any()))
                    .thenReturn(new RegisterResponse("token"));

            // No Authorization header — should not be 401/403
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /api/v1/auth/login ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        private final String URL = "/api/v1/auth/login";

        @Test
        @DisplayName("Returns 200 with access token on valid credentials")
        void returns200OnSuccess() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "password123");

            when(authService.login(any(), any()))
                    .thenReturn(new LoginResponse("access-token-xyz"));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token-xyz"));
        }

        @Test
        @DisplayName("Returns 400 when email is blank")
        void returns400WhenEmailBlank() throws Exception {
            LoginRequest request = new LoginRequest("", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when email format is invalid")
        void returns400WhenEmailInvalid() throws Exception {
            LoginRequest request = new LoginRequest("not-an-email", "password123");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when password is blank")
        void returns400WhenPasswordBlank() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Returns 400 when password is shorter than 8 characters")
        void returns400WhenPasswordTooShort() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "short");

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Is publicly accessible without authentication")
        void isPublicEndpoint() throws Exception {
            LoginRequest request = new LoginRequest("alice@example.com", "password123");

            when(authService.login(any(), any()))
                    .thenReturn(new LoginResponse("token"));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /api/v1/auth/logout ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        private final String URL = "/api/v1/auth/logout";

        @Test
        @DisplayName("Returns 204 when authenticated")
        @WithMockUser
        void returns204WhenAuthenticated() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post(URL))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Returns 401 when not authenticated")
        void returns401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post(URL))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(authService);
        }
    }

    // ── POST /api/v1/auth/refresh ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        private final String URL = "/api/v1/auth/refresh";

        @Test
        @DisplayName("Returns 200 with new access token")
        void returns200OnSuccess() throws Exception {
            when(authService.refresh(any(), any()))
                    .thenReturn(new RegisterResponse("new-access-token"));

            mockMvc.perform(post(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"));
        }

        @Test
        @DisplayName("Is publicly accessible without authentication")
        void isPublicEndpoint() throws Exception {
            when(authService.refresh(any(), any()))
                    .thenReturn(new RegisterResponse("token"));

            // No Authorization header — should not be 401/403
            mockMvc.perform(post(URL))
                    .andExpect(status().isOk());
        }
    }

    // ── GET /api/v1/auth/verify ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/auth/verify")
    class VerifyEmail {

        private final String URL = "/api/v1/auth/verify";

        @Test
        @DisplayName("Returns 200 with message on valid token")
        void returns200OnSuccess() throws Exception {
            when(authService.verifyEmail("valid-token"))
                    .thenReturn("Email verified successfully");

            mockMvc.perform(get(URL).param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Email verified successfully"));
        }

        @Test
        @DisplayName("Returns 400 when token param is missing")
        void returns400WhenTokenMissing() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Is publicly accessible without authentication")
        void isPublicEndpoint() throws Exception {
            when(authService.verifyEmail(any()))
                    .thenReturn("Email verified successfully");

            mockMvc.perform(get(URL).param("token", "some-token"))
                    .andExpect(status().isOk());
        }
    }
}