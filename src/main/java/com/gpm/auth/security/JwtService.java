package com.gpm.auth.security;

import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.User;
import com.gpm.common.entity.Functionality;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

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
        return buildAccessToken(user, user.getUserRoles());
    }

    /** Generates an access token scoped to a single selected employee role. */
    public String generateAccessToken(User user, UserRole selectedRole) {
        return buildAccessToken(user, List.of(selectedRole));
    }

    private String buildAccessToken(User user, List<UserRole> roles) {
        List<String> authorities = roles.stream()
                .flatMap(er -> er.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .map(f -> f.getCode().getCode())
                .distinct()
                .toList();

        List<String> roleNames = roles.stream().map(UserRole::getName).toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("userRoleNames", roleNames)
                .claim("authorities", authorities)
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

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        return (List<String>) extractAllClaims(token).get("authorities");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

}
