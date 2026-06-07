package com.gpr.auth.controller;

import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.service.AuthService;
import com.gpr.common.dto.AuthRequest;
import com.gpr.common.dto.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Identity endpoints. Establishes <em>who</em> the caller is and sets the identity cookie; WorkOS
 * (wos-hr {@code /auth/session}) takes it from here to resolve roles and mint the role token.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private static final int ACCESS_TOKEN_MAX_AGE  = 3600;       // 1 hour
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 3600; // 7 days

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResult result = authService.register(request, resolveClientId(httpRequest));
        setTokenCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request, resolveClientId(httpRequest));
        setTokenCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractCookie(request, "refresh_token");
        LoginResult result = authService.refresh(refreshToken);
        setAccessTokenCookie(response, result.accessToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refresh_token");
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearTokenCookies(response);
        return ResponseEntity.ok().build();
    }

    /** The app a login/register request is for; sent via X-App-Id, defaults to "workos". */
    private String resolveClientId(HttpServletRequest request) {
        String appId = request.getHeader("X-App-Id");
        return (appId == null || appId.isBlank()) ? "workos" : appId.trim();
    }

    // ── cookie helpers ────────────────────────────────────────────────

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        setAccessTokenCookie(response, accessToken);
        response.addCookie(buildCookie("refresh_token", refreshToken, REFRESH_TOKEN_MAX_AGE));
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        response.addCookie(buildCookie("access_token", accessToken, ACCESS_TOKEN_MAX_AGE));
    }

    private void clearTokenCookies(HttpServletResponse response) {
        response.addCookie(buildCookie("access_token", "", 0));
        response.addCookie(buildCookie("refresh_token", "", 0));
    }

    private Cookie buildCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
