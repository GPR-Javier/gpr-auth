package com.gpr.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs/verifies the short-lived, stateless tokens used by the OAuth flow:
 *  - {@code state} — CSRF + carries the company slug + provider across the redirect.
 *  - {@code pending-link} — carries the verified provider profile to the link-confirmation step.
 * Both are HMAC-signed with the shared JWT secret; no DB row needed.
 */
@Service
public class OAuthStateService {

    private static final long TTL_MS = 600_000; // 10 minutes

    private final SecretKey key;

    public OAuthStateService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    // ── state ────────────────────────────────────────────────────────────

    public String signState(String slug, String provider) {
        return Jwts.builder()
                .claim("typ", "oauth_state")
                .claim("slug", slug)
                .claim("provider", provider)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL_MS))
                .signWith(key)
                .compact();
    }

    public record StateData(String slug, String provider) {}

    public StateData verifyState(String token) {
        Claims c = parse(token);
        require("oauth_state".equals(c.get("typ", String.class)), "Invalid state token");
        return new StateData(c.get("slug", String.class), c.get("provider", String.class));
    }

    // ── pending link ─────────────────────────────────────────────────────

    public String signPendingLink(
            String slug, String provider, String sub, String email, String name, String picture) {
        return Jwts.builder()
                .claim("typ", "oauth_link")
                .claim("slug", slug)
                .claim("provider", provider)
                .claim("sub", sub)
                .claim("email", email)
                .claim("name", name)
                .claim("picture", picture)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL_MS))
                .signWith(key)
                .compact();
    }

    public record PendingLink(
            String slug, String provider, String sub, String email, String name, String picture) {}

    public PendingLink verifyPendingLink(String token) {
        Claims c = parse(token);
        require("oauth_link".equals(c.get("typ", String.class)), "Invalid link token");
        return new PendingLink(
                c.get("slug", String.class),
                c.get("provider", String.class),
                c.get("sub", String.class),
                c.get("email", String.class),
                c.get("name", String.class),
                c.get("picture", String.class));
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalArgumentException(msg);
    }
}
