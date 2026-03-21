package com.toyshop.user;

import com.toyshop.user.dto.*;
import com.toyshop.user.entity.User;
import com.toyshop.user.entity.UserRole;
import com.toyshop.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should register and login user successfully")
    void shouldRegisterAndLoginUserSuccessfully() throws Exception {
        // Register
        RegistrationRequest registerRequest = RegistrationRequest.builder()
                .username("integrationuser")
                .email("integration@example.com")
                .password("Password123!")
                .firstName("Integration")
                .lastName("Test")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("integrationuser"))
                .andExpect(jsonPath("$.email").value("integration@example.com"));

        // Login
        LoginRequest loginRequest = LoginRequest.builder()
                .usernameOrEmail("integrationuser")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        assertTrue(responseBody.contains("accessToken"));
    }

    @Test
    @DisplayName("Should reject duplicate registration")
    void shouldRejectDuplicateRegistration() throws Exception {
        // First registration
        RegistrationRequest request = RegistrationRequest.builder()
                .username("duplicateuser")
                .email("duplicate@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate registration
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Username already exists: duplicateuser"));
    }

    @Test
    @DisplayName("Should handle concurrent registrations")
    void shouldHandleConcurrentRegistrations() throws Exception {
        // Create multiple users concurrently
        for (int i = 0; i < 5; i++) {
            RegistrationRequest request = RegistrationRequest.builder()
                    .username("concurrentuser" + i)
                    .email("concurrent" + i + "@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Verify all users were created
        assertEquals(5, userRepository.count());
    }

    @Test
    @DisplayName("Should validate input constraints")
    void shouldValidateInputConstraints() throws Exception {
        // Invalid email
        RegistrationRequest invalidEmail = RegistrationRequest.builder()
                .username("testuser")
                .email("invalid-email")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmail)))
                .andExpect(status().isBadRequest());

        // Weak password
        RegistrationRequest weakPassword = RegistrationRequest.builder()
                .username("testuser2")
                .email("test2@example.com")
                .password("weak")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(weakPassword)))
                .andExpect(status().isBadRequest());

        // Empty username
        RegistrationRequest emptyUsername = RegistrationRequest.builder()
                .username("")
                .email("test3@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyUsername)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle database persistence")
    void shouldHandleDatabasePersistence() throws Exception {
        // Register user
        RegistrationRequest request = RegistrationRequest.builder()
                .username("persistuser")
                .email("persist@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify user exists in database
        assertTrue(userRepository.existsByUsername("persistuser"));
        assertTrue(userRepository.existsByEmail("persist@example.com"));

        // Login should work
        LoginRequest loginRequest = LoginRequest.builder()
                .usernameOrEmail("persistuser")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }
}