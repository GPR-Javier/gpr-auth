package com.gpm.auth.controller;

import com.gpm.auth.dto.LoginResult;
import com.gpm.auth.dto.RoleSelectionRequest;
import com.gpm.auth.dto.SwitchRoleRequest;
import com.gpm.auth.service.AuthService;
import com.gpm.common.dto.AuthRequest;
import com.gpm.common.dto.AuthResponse;
import com.gpm.common.dto.UserDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private static final int ACCESS_TOKEN_MAX_AGE  = 3600;       // 1 hour
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 3600; // 7 days

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request);
        if (!result.response().isRequiresRoleSelection()) {
            setTokenCookies(response, result.accessToken(), result.refreshToken());
        }
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/login/select-role")
    public ResponseEntity<AuthResponse> loginWithRole(
            @Valid @RequestBody RoleSelectionRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.loginWithRole(request);
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
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.me(userDetails.getUsername()));
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
