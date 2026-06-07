package com.gpr.auth.security;

import com.gpr.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints and validates IDENTITY tokens. gpr-auth proves <em>who</em> the caller is (sub = userId,
 * plus email + audience); it no longer stamps roles. WorkOS (wos-hr {@code /auth/session}) reads
 * this identity token and mints its own role-bearing access token.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiry;

    /** Default audience while WorkOS is the only app; overridden by the caller's clientId. */
    private static final String DEFAULT_AUD = "workos";

    public String generateAccessToken(User user) {
        return generateAccessToken(user, null, DEFAULT_AUD);
    }

    public String generateAccessToken(User user, String aud) {
        return generateAccessToken(user, null, aud);
    }

    /**
     * Generates an identity token. Carries the platform super-admin flag always, and the selected
     * tenant ({@code companyId}) once the user has chosen a company.
     */
    public String generateAccessToken(User user, Long companyId, String aud) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("super_admin", user.isSuperAdmin())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry));
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        if (aud != null && !aud.isBlank()) {
            builder.audience().add(aud).and();
        }
        return builder.signWith(signingKey()).compact();
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

    public Long extractCompanyId(String token) {
        Object v = extractAllClaims(token).get("companyId");
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
