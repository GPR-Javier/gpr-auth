package com.gpr.auth.controller;

import com.gpr.auth.dto.CompanyInfo;
import com.gpr.auth.dto.CompanyLoginResponse;
import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.dto.SelectCompanyRequest;
import com.gpr.auth.dto.UpdateCredentialsRequest;
import com.gpr.auth.dto.UpdateInfoRequest;
import com.gpr.auth.security.JwtService;
import com.gpr.auth.service.AuthService;
import com.gpr.common.dto.AuthRequest;
import com.gpr.common.dto.AuthResponse;
import com.gpr.common.dto.UserSummaryDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
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
    private final JwtService jwtService;

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
    public ResponseEntity<CompanyLoginResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        AuthService.LoginOutcome outcome = authService.login(request, resolveClientId(httpRequest));
        // Soft-deleted account: no tokens — the client prompts recover-or-fresh and calls /reactivate.
        if (outcome.requiresReactivation()) {
            return ResponseEntity.ok(new CompanyLoginResponse(false, List.of(), null, true));
        }
        setTokenCookies(response, outcome.result().accessToken(), outcome.result().refreshToken());
        return ResponseEntity.ok(new CompanyLoginResponse(
                outcome.requiresCompanySelection(), outcome.companies(), outcome.companyId(), false));
    }

    /**
     * Recovers a soft-deleted account on re-login. {@code mode=fresh} wipes accumulated app data for a
     * clean start; anything else restores everything. Re-verifies the password, then logs the user in.
     */
    @PostMapping("/reactivate")
    public ResponseEntity<CompanyLoginResponse> reactivate(
            @Valid @RequestBody AuthRequest request,
            @RequestParam(defaultValue = "recover") String mode,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        AuthService.LoginOutcome outcome = authService.reactivate(
                request, "fresh".equalsIgnoreCase(mode), resolveClientId(httpRequest));
        setTokenCookies(response, outcome.result().accessToken(), outcome.result().refreshToken());
        return ResponseEntity.ok(new CompanyLoginResponse(
                outcome.requiresCompanySelection(), outcome.companies(), outcome.companyId(), false));
    }

    /** Companies the authenticated user may act within (after login). */
    @GetMapping("/companies")
    public ResponseEntity<List<CompanyInfo>> companies(HttpServletRequest request) {
        return ResponseEntity.ok(authService.companiesForUser(currentUserId(request)));
    }

    /** Choose (or switch to) the active company; re-mints the token with the new tenant. */
    @PostMapping({"/select-company", "/switch-company"})
    public ResponseEntity<CompanyLoginResponse> selectCompany(
            @Valid @RequestBody SelectCompanyRequest body,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        AuthService.LoginOutcome outcome = authService.selectCompany(
                currentUserId(httpRequest), body.getCompanyId(), resolveClientId(httpRequest));
        setAccessTokenCookie(response, outcome.result().accessToken());
        return ResponseEntity.ok(new CompanyLoginResponse(
                outcome.requiresCompanySelection(), outcome.companies(), outcome.companyId(), false));
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

    // ── account self-service (credentials + canonical info — shared across all apps) ──

    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> me(HttpServletRequest request) {
        return ResponseEntity.ok(authService.getAccount(currentUserId(request)));
    }

    @PutMapping("/me/credentials")
    public ResponseEntity<UserSummaryDto> updateCredentials(
            @Valid @RequestBody UpdateCredentialsRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(authService.updateCredentials(currentUserId(request), body));
    }

    @PutMapping("/me/info")
    public ResponseEntity<UserSummaryDto> updateInfo(
            @Valid @RequestBody UpdateInfoRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(authService.updateInfo(currentUserId(request), body));
    }

    /** Set (first time) or change the account password. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        authService.changePassword(currentUserId(request), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @GetMapping("/me/login-methods")
    public ResponseEntity<AuthService.LoginMethodsView> loginMethods(HttpServletRequest request) {
        return ResponseEntity.ok(authService.loginMethods(currentUserId(request)));
    }

    /** Disconnect a linked OAuth provider (can't remove the only remaining sign-in method). */
    @org.springframework.web.bind.annotation.DeleteMapping("/me/login-methods/{provider}")
    public ResponseEntity<Void> removeLoginMethod(
            @PathVariable String provider, HttpServletRequest request) {
        authService.removeLoginMethod(currentUserId(request), provider);
        return ResponseEntity.noContent().build();
    }

    /** Permanently delete the authenticated identity (retype email to confirm). Clears the session. */
    @org.springframework.web.bind.annotation.DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @RequestBody DeleteAccountRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.deleteAccount(currentUserId(request), body.confirm());
        clearTokenCookies(response);
        return ResponseEntity.noContent().build();
    }

    public record DeleteAccountRequest(String confirm) {}

    /** Resolves the authenticated identity id (sub) from the access-token cookie/bearer. */
    private Long currentUserId(HttpServletRequest request) {
        String token = extractCookie(request, "access_token");
        if (token == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7).trim();
            }
        }
        if (token == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return jwtService.extractUserId(token);
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
