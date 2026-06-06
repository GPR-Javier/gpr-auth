package com.gpr.auth.security;

import com.gpr.common.entity.User;
import com.gpr.common.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiry;

    public String generateAccessToken(User user) {
        return buildAccessToken(user, user.getActiveUserRoles(java.time.LocalDateTime.now()));
    }

    public String generateAccessToken(User user, List<UserRole> roles) {
        return buildAccessToken(user, roles);
    }

    /** Generates an access token scoped to a single selected employee role. */
    public String generateAccessToken(User user, UserRole selectedRole) {
        return buildAccessToken(user, List.of(selectedRole));
    }

    private String buildAccessToken(User user, List<UserRole> roles) {
        List<String> roleNames = roles.stream().map(UserRole::getName).toList();
        boolean isAdmin = roles.stream().anyMatch(UserRole::isAdmin);
        Long userRoleId = roles.isEmpty() ? null : roles.get(0).getId();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", isAdmin ? "ADMIN" : "EMPLOYEE")
                .claim("userRoleNames", roleNames)
                .claim("userRoleId", userRoleId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(signingKey())
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .signWith(signingKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public Long extractUserRoleId(String token) {
        Object val = extractAllClaims(token).get("userRoleId");
        return val instanceof Number ? ((Number) val).longValue() : null;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

}
