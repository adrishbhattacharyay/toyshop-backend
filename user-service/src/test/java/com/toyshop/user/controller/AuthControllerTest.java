package com.toyshop.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyshop.user.dto.*;
import com.toyshop.user.entity.UserRole;
import com.toyshop.user.exception.*;
import com.toyshop.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private UserResponse testUserResponse;
    private AuthResponse testAuthResponse;

    @BeforeEach
    void setUp() {
        testUserResponse = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .fullName("Test User")
                .phone("+1234567890")
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        testAuthResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(testUserResponse)
                .build();
    }

    @Nested
    @DisplayName("Registration Endpoint Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register user successfully")
        void shouldRegisterUserSuccessfully() throws Exception {
            RegistrationRequest request = RegistrationRequest.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("Password123!")
                    .firstName("New")
                    .lastName("User")
                    .build();

            when(userService.register(any(RegistrationRequest.class))).thenReturn(testUserResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        @DisplayName("Should reject registration with invalid email")
        void shouldRejectRegistrationWithInvalidEmail() throws Exception {
            RegistrationRequest request = RegistrationRequest.builder()
                    .username("newuser")
                    .email("invalid-email")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject registration with weak password")
        void shouldRejectRegistrationWithWeakPassword() throws Exception {
            RegistrationRequest request = RegistrationRequest.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("weak")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject registration with existing username")
        void shouldRejectRegistrationWithExistingUsername() throws Exception {
            RegistrationRequest request = RegistrationRequest.builder()
                    .username("existinguser")
                    .email("new@example.com")
                    .password("Password123!")
                    .build();

            when(userService.register(any(RegistrationRequest.class)))
                    .thenThrow(new UsernameAlreadyExistsException("Username already exists"));

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Login Endpoint Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("password123")
                    .build();

            when(userService.login(any(LoginRequest.class))).thenReturn(testAuthResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.username").value("testuser"));
        }

        @Test
        @DisplayName("Should reject login with invalid credentials")
        void shouldRejectLoginWithInvalidCredentials() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .usernameOrEmail("testuser")
                    .password("wrongpassword")
                    .build();

            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new InvalidCredentialsException("Invalid username or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject login with missing username")
        void shouldRejectLoginWithMissingUsername() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .password("password123")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Token Refresh Tests")
    class TokenRefreshTests {

        @Test
        @DisplayName("Should refresh token successfully")
        void shouldRefreshTokenSuccessfully() throws Exception {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            when(userService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(testAuthResponse);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"));
        }
    }

    @Nested
    @DisplayName("Password Reset Tests")
    class PasswordResetTests {

        @Test
        @DisplayName("Should request password reset")
        void shouldRequestPasswordReset() throws Exception {
            doNothing().when(userService).requestPasswordReset("test@example.com");

            mockMvc.perform(post("/api/v1/auth/password/reset/request")
                    .param("email", "test@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Password reset email sent"));
        }

        @Test
        @DisplayName("Should reset password with valid token")
        void shouldResetPasswordWithValidToken() throws Exception {
            doNothing().when(userService).resetPassword("valid-token", "NewPassword123!");

            mockMvc.perform(post("/api/v1/auth/password/reset")
                    .param("token", "valid-token")
                    .param("newPassword", "NewPassword123!"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Password reset successfully"));
        }
    }
}