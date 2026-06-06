package com.gpr.auth.controller;

import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.dto.RoleSelectionRequest;
import com.gpr.auth.dto.SwitchRoleRequest;
import com.gpr.auth.security.JwtService;
import com.gpr.auth.service.AuthService;
import com.gpr.common.dto.AuthRequest;
import com.gpr.common.dto.AuthResponse;
import com.gpr.common.dto.UserDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request, resolveClientId(httpRequest));
        if (!result.response().isRequiresRoleSelection()) {
            setTokenCookies(response, result.accessToken(), result.refreshToken());
        }
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/login/select-role")
    public ResponseEntity<AuthResponse> loginWithRole(
            @Valid @RequestBody RoleSelectionRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        LoginResult result = authService.loginWithRole(request, resolveClientId(httpRequest));
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

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request
    ) {
        Long activeRoleId = extractActiveRoleId(request);
        return ResponseEntity.ok(authService.me(userDetails.getUsername(), activeRoleId));
    }

    /**
     * Marks a single onboarding screen as completed/skipped for the user's active role.
     * The active role is resolved from the JWT — no payload needed.
     */
    @PostMapping("/onboarding/screen/{screenKey}/complete")
    public ResponseEntity<AuthResponse> completeScreenOnboarding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String screenKey,
            HttpServletRequest request
    ) {
        Long activeRoleId = extractActiveRoleId(request);
        return ResponseEntity.ok(
                authService.completeScreenOnboarding(userDetails.getUsername(), activeRoleId, screenKey));
    }

    /** Skips all remaining onboarding for the user's active role (the global "skip all"). */
    @PostMapping("/onboarding/skip-all")
    public ResponseEntity<AuthResponse> skipOnboarding(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request
    ) {
        Long activeRoleId = extractActiveRoleId(request);
        return ResponseEntity.ok(authService.skipOnboarding(userDetails.getUsername(), activeRoleId));
    }

    /**
     * Switches the active role for the currently authenticated user — no password required.
     * Issues a new access-token cookie scoped to the requested role.
     * Payload: { "userRoleId": 3 }
     */
    @PostMapping("/switch-role")
    public ResponseEntity<AuthResponse> switchRole(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SwitchRoleRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.switchRole(userDetails.getUsername(), request);
        setAccessTokenCookie(response, result.accessToken());
        return ResponseEntity.ok(result.response());
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

    /** Resolves the active role id from the access-token cookie, or null if absent/unparseable. */
    private Long extractActiveRoleId(HttpServletRequest request) {
        String accessToken = extractCookie(request, "access_token");
        if (accessToken == null) {
            return null;
        }
        try {
            return jwtService.extractUserRoleId(accessToken);
        } catch (Exception e) {
            return null;
        }
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
