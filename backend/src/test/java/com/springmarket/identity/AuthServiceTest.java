package com.springmarket.identity;

import com.springmarket.identity.dto.AuthResponse;
import com.springmarket.identity.dto.LoginRequest;
import com.springmarket.identity.dto.RegisterRequest;
import com.springmarket.shared.error.DomainException;
import com.springmarket.shared.security.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;

    private final Map<String, User> usersByEmail = new HashMap<>();
    private final Map<String, RefreshToken> refreshTokensByHash = new HashMap<>();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(userRepository.findByEmail(any())).thenAnswer(invocation ->
                Optional.ofNullable(usersByEmail.get(invocation.getArgument(0))));
        org.mockito.Mockito.lenient().when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            usersByEmail.put(user.getEmail(), user);
            return user;
        });
        org.mockito.Mockito.lenient().when(refreshTokenRepository.findByTokenHash(any())).thenAnswer(invocation ->
                Optional.ofNullable(refreshTokensByHash.get(invocation.getArgument(0))));
        org.mockito.Mockito.lenient().when(refreshTokenRepository.save(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            refreshTokensByHash.put(token.getTokenHash(), token);
            return token;
        });
        org.mockito.Mockito.lenient().when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        org.mockito.Mockito.lenient()
                .when(passwordEncoder.matches("Password1!", "bcrypt-hash"))
                .thenReturn(true);
        jwtService = new JwtService("test-secret-must-be-at-least-32-bytes", Clock.fixed(NOW, ZoneOffset.UTC));
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void register_defaultsToBuyerAndHashesPassword() {
        AuthResponse response = authService.register(
                new RegisterRequest("Buyer@Example.com", "Password1!", Set.of()));

        assertEquals(Set.of("BUYER"), response.roles());
        assertEquals("Bearer", response.tokenType());
        assertEquals("buyer@example.com", usersByEmail.get("buyer@example.com").getEmail());
    }

    @Test
    void refreshAfterLogout_fails() {
        AuthResponse first = authService.register(new RegisterRequest(
                "buyer@example.com", "Password1!", Set.of("BUYER")));
        authService.logout(first.refreshToken());

        DomainException exception = assertThrows(
                DomainException.class,
                () -> authService.refresh(first.refreshToken()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void expiredRefreshToken_rejected() {
        User user = savedUser("buyer@example.com");
        String rawToken = "expired-token";
        RefreshToken expired = new RefreshToken(
                UUID.randomUUID(),
                user,
                AuthService.hashRefreshToken(rawToken),
                NOW.minusSeconds(1),
                false,
                NOW.minusSeconds(100));
        refreshTokensByHash.put(expired.getTokenHash(), expired);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> authService.refresh(rawToken));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void rotatedRefreshToken_oldTokenRejected() {
        savedUser("buyer@example.com");
        AuthResponse first = authService.login(
                new LoginRequest("buyer@example.com", "Password1!"));
        AuthResponse second = authService.refresh(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        DomainException exception = assertThrows(
                DomainException.class,
                () -> authService.refresh(first.refreshToken()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    private User savedUser(String email) {
        User user = new User(
                UUID.randomUUID(),
                email,
                "bcrypt-hash",
                true,
                NOW,
                NOW,
                Set.of(Role.BUYER));
        usersByEmail.put(email, user);
        return user;
    }
}
