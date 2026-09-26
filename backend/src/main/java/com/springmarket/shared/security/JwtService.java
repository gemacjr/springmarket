package com.springmarket.shared.security;

import com.springmarket.shared.error.DomainException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final long ACCESS_TTL_SECONDS = Duration.ofMinutes(15).toSeconds();

    private final SecretKey key;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generateAccessToken(UserPrincipal principal) {
        var now = clock.instant();
        return Jwts.builder()
                .subject(principal.userId().toString())
                .claim("email", principal.email())
                .claim("roles", principal.roles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            List<?> claimRoles = claims.get("roles", List.class);
            Set<String> roles = claimRoles == null
                    ? Set.of()
                    : claimRoles.stream().map(Object::toString).collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    roles,
                    true);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Invalid or expired access token");
        }
    }
}
