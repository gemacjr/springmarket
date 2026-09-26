package com.springmarket.identity;

import com.springmarket.identity.dto.AuthResponse;
import com.springmarket.identity.dto.LoginRequest;
import com.springmarket.identity.dto.RegisterRequest;
import com.springmarket.shared.error.DomainException;
import com.springmarket.shared.security.JwtService;
import com.springmarket.shared.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DomainException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        Set<Role> roles = parseRegistrationRoles(request.roles());
        Instant now = clock.instant();
        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                true,
                now,
                now,
                roles);
        return issueTokens(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(User::isEnabled)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = findRefreshToken(rawRefreshToken);
        Instant now = clock.instant();
        if (stored.isRevoked() || !stored.getExpiresAt().isAfter(now) || !stored.getUser().isEnabled()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        stored.revoke();
        refreshTokenRepository.save(stored);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken stored = findRefreshToken(rawRefreshToken);
        if (stored.isRevoked()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        stored.revoke();
        refreshTokenRepository.save(stored);
    }

    private RefreshToken findRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        return refreshTokenRepository.findByTokenHash(hashRefreshToken(rawRefreshToken))
                .orElseThrow(() -> new DomainException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
    }

    private AuthResponse issueTokens(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toUnmodifiableSet());
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), roles, user.isEnabled());
        String rawRefreshToken = newRefreshToken();
        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(
                UUID.randomUUID(),
                user,
                hashRefreshToken(rawRefreshToken),
                now.plus(REFRESH_TTL),
                false,
                now));
        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                rawRefreshToken,
                "Bearer",
                JwtService.ACCESS_TTL_SECONDS,
                user.getId(),
                roles);
    }

    private Set<Role> parseRegistrationRoles(Set<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            return Set.of(Role.BUYER);
        }
        try {
            Set<Role> roles = requestedRoles.stream()
                    .map(role -> Role.valueOf(role.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
            if (roles.contains(Role.ADMIN)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "ADMIN cannot be self-assigned");
            }
            return roles;
        } catch (IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "Unknown role");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashRefreshToken(String rawRefreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
