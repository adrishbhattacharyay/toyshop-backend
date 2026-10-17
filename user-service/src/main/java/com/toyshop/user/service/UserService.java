package com.toyshop.user.service;

import com.toyshop.user.dto.*;
import com.toyshop.user.entity.User;
import com.toyshop.user.entity.UserRole;
import com.toyshop.user.event.UserEvent;
import com.toyshop.user.event.UserEventType;
import com.toyshop.user.exception.*;
import com.toyshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final com.toyshop.user.security.JwtUtil jwtUtil;
    private static final String USER_EVENTS_TOPIC = "user-events";

    @Value("${app.cache.user-ttl:3600}")
    private long userCacheTtl;

    @Value("${app.cache.session-ttl:1800}")
    private long sessionCacheTtl;

    @Transactional
    public UserResponse register(RegistrationRequest request) {
        log.info("Registering new user with username: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .verificationToken(UUID.randomUUID().toString())
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        // Publish user registration event
        publishUserEvent(savedUser, UserEventType.REGISTERED);

        return UserResponse.from(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsernameOrEmail());

        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for user: {}", request.getUsernameOrEmail());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (!user.isEnabled()) {
            throw new AccountDisabledException("Account is disabled");
        }

        if (!user.isAccountNonLocked()) {
            throw new AccountLockedException("Account is locked");
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in successfully: {}", user.getUsername());

        // Publish login event
        publishUserEvent(user, UserEventType.LOGGED_IN);

        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtUtil.getExpirationMs(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        String username = jwtUtil.extractUsername(request.getRefreshToken());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtUtil.getExpirationMs(),
                UserResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user by ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#username")
    public UserResponse getUserByUsername(String username) {
        log.debug("Fetching user by username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.debug("Fetching all users");

        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(UserRole role) {
        log.debug("Fetching users by role: {}", role);

        return userRepository.findByRole(role).stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#id"),
        @CacheEvict(value = "userTokens", allEntries = true)
    })
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", updatedUser.getId());

        publishUserEvent(updatedUser, UserEventType.UPDATED);

        return UserResponse.from(updatedUser);
    }

    @Caching(evict = {
        @CacheEvict(value = "users", key = "#id"),
        @CacheEvict(value = "userTokens", allEntries = true)
    })
    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user with ID: {}", id);

        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with ID: " + id);
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully with ID: {}", id);

        // Publish user deletion event
        try {
            UserEvent event = UserEvent.builder()
                    .userId(id)
                    .eventType(UserEventType.DELETED)
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(USER_EVENTS_TOPIC, event);
        } catch (Exception e) {
            log.warn("Failed to publish delete event (non-fatal): {}", e.getMessage());
        }
    }

    @CacheEvict(value = "userTokens", allEntries = true)
    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        log.info("Changing password for user ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password changed successfully for user ID: {}", id);
        publishUserEvent(user, UserEventType.PASSWORD_CHANGED);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordExpiresAt(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        // In a real application, send email with reset link
        log.info("Password reset token generated for user: {}", user.getUsername());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("Resetting password with token");

        User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getResetPasswordToken()))
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset token"));

        if (user.getResetPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiresAt(null);
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", user.getUsername());
        publishUserEvent(user, UserEventType.PASSWORD_RESET);
    }

    private void publishUserEvent(User user, UserEventType eventType) {
        try {
            UserEvent event = UserEvent.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .eventType(eventType)
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaTemplate.send(USER_EVENTS_TOPIC, event);
        } catch (Exception e) {
            log.warn("Failed to publish user event (non-fatal): {}", e.getMessage());
        }
    }
}