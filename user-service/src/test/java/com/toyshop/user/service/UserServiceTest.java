package com.toyshop.user.service;

import com.toyshop.user.dto.*;
import com.toyshop.user.entity.User;
import com.toyshop.user.entity.UserRole;
import com.toyshop.user.exception.*;
import com.toyshop.user.repository.UserRepository;
import com.toyshop.user.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private RegistrationRequest registrationRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .phone("+1234567890")
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .createdAt(LocalDateTime.now())
                .build();

        registrationRequest = RegistrationRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("Password123!")
                .firstName("New")
                .lastName("User")
                .phone("+1234567890")
                .build();

        loginRequest = LoginRequest.builder()
                .usernameOrEmail("testuser")
                .password("password123")
                .build();

        // Set up JwtUtil mock
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateAccessToken(anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);
        ReflectionTestUtils.setField(userService, "jwtUtil", jwtUtil);
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register new user successfully")
        void shouldRegisterNewUserSuccessfully() {
            when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(false);
            when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            UserResponse response = userService.register(registrationRequest);

            assertNotNull(response);
            assertEquals(registrationRequest.getUsername(), response.getUsername());
            assertEquals(registrationRequest.getEmail(), response.getEmail());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when username already exists")
        void shouldThrowExceptionWhenUsernameExists() {
            when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(true);

            assertThrows(UsernameAlreadyExistsException.class,
                    () -> userService.register(registrationRequest));
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void shouldThrowExceptionWhenEmailExists() {
            when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(false);
            when(userRepository.existsByEmail(registrationRequest.getEmail())).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class,
                    () -> userService.register(registrationRequest));
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfullyWithValidCredentials() {
            when(userRepository.findByUsernameOrEmail(loginRequest.getUsernameOrEmail()))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                    .thenReturn(true);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            AuthResponse response = userService.login(loginRequest);

            assertNotNull(response);
            assertNotNull(response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            when(userRepository.findByUsernameOrEmail(loginRequest.getUsernameOrEmail()))
                    .thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class,
                    () -> userService.login(loginRequest));
        }

        @Test
        @DisplayName("Should throw exception when password is incorrect")
        void shouldThrowExceptionWhenPasswordIncorrect() {
            when(userRepository.findByUsernameOrEmail(loginRequest.getUsernameOrEmail()))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                    .thenReturn(false);

            assertThrows(InvalidCredentialsException.class,
                    () -> userService.login(loginRequest));
        }

        @Test
        @DisplayName("Should throw exception when account is disabled")
        void shouldThrowExceptionWhenAccountDisabled() {
            testUser.setEnabled(false);
            when(userRepository.findByUsernameOrEmail(loginRequest.getUsernameOrEmail()))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                    .thenReturn(true);

            assertThrows(AccountDisabledException.class,
                    () -> userService.login(loginRequest));
        }

        @Test
        @DisplayName("Should throw exception when account is locked")
        void shouldThrowExceptionWhenAccountLocked() {
            testUser.setAccountNonLocked(false);
            when(userRepository.findByUsernameOrEmail(loginRequest.getUsernameOrEmail()))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(loginRequest.getPassword(), testUser.getPassword()))
                    .thenReturn(true);

            assertThrows(AccountLockedException.class,
                    () -> userService.login(loginRequest));
        }
    }

    @Nested
    @DisplayName("Get User Tests")
    class GetUserTests {

        @Test
        @DisplayName("Should get user by ID successfully")
        void shouldGetUserByIdSuccessfully() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUserById(1L);

            assertNotNull(response);
            assertEquals(testUser.getId(), response.getId());
            assertEquals(testUser.getUsername(), response.getUsername());
        }

        @Test
        @DisplayName("Should throw exception when user not found by ID")
        void shouldThrowExceptionWhenUserNotFoundById() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.getUserById(999L));
        }

        @Test
        @DisplayName("Should get user by username successfully")
        void shouldGetUserByUsernameSuccessfully() {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUserByUsername("testuser");

            assertNotNull(response);
            assertEquals(testUser.getUsername(), response.getUsername());
        }
    }

    @Nested
    @DisplayName("Update User Tests")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .lastName("Name")
                    .phone("+9876543210")
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            UserResponse response = userService.updateUser(1L, request);

            assertNotNull(response);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent user")
        void shouldThrowExceptionWhenUpdatingNonExistentUser() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .build();

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.updateUser(999L, request));
        }
    }

    @Nested
    @DisplayName("Delete User Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user successfully")
        void shouldDeleteUserSuccessfully() {
            when(userRepository.existsById(1L)).thenReturn(true);
            doNothing().when(userRepository).deleteById(1L);

            assertDoesNotThrow(() -> userService.deleteUser(1L));
            verify(userRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent user")
        void shouldThrowExceptionWhenDeletingNonExistentUser() {
            when(userRepository.existsById(999L)).thenReturn(false);

            assertThrows(UserNotFoundException.class,
                    () -> userService.deleteUser(999L));
        }
    }

    @Nested
    @DisplayName("Password Change Tests")
    class PasswordChangeTests {

        @Test
        @DisplayName("Should change password successfully")
        void shouldChangePasswordSuccessfully() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("currentPassword", testUser.getPassword())).thenReturn(true);
            when(passwordEncoder.encode("newPassword")).thenReturn("encodedNewPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            assertDoesNotThrow(() ->
                    userService.changePassword(1L, "currentPassword", "newPassword"));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when current password is wrong")
        void shouldThrowExceptionWhenCurrentPasswordWrong() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

            assertThrows(InvalidCredentialsException.class,
                    () -> userService.changePassword(1L, "wrongPassword", "newPassword"));
        }
    }
}