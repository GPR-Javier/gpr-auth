package com.gpr.auth.controller;

import com.gpr.auth.client.WosHrOAuthClient;
import com.gpr.auth.entity.Company;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.security.OAuthStateService;
import com.gpr.auth.service.AuthService;
import com.gpr.auth.service.AuthService.LoginOutcome;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth/SSO sign-in. gpr-auth owns the redirect + session; wos-hr (via {@link WosHrOAuthClient})
 * holds the per-company secret and runs the token exchange. Auto-links on a trusted, verified email;
 * otherwise hands off to the confirm-link flow.
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private static final int ACCESS_TOKEN_MAX_AGE = 3600;
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 3600;
    private static final String APP_CLIENT_ID = "workos";

    private final WosHrOAuthClient wosHr;
    private final OAuthStateService stateService;
    private final AuthService authService;
    private final CompanyRepository companyRepository;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    /** Step 1 — redirect the browser to the provider's consent screen. */
    @GetMapping("/{provider}/authorize")
    public void authorize(
            @PathVariable String provider,
            @RequestParam("company") String slug,
            HttpServletResponse response) throws java.io.IOException {
        Company company = companyRepository.findBySlug(slug).orElse(null);
        if (company == null) {
            response.sendRedirect(loginError(slug, "unknown_company"));
            return;
        }
        WosHrOAuthClient.AuthorizeConfig cfg = wosHr.authorizeConfig(company.getId(), provider);
        if (cfg.authorizationUri() == null || cfg.authorizationUri().isBlank()) {
            response.sendRedirect(loginError(slug, "provider_misconfigured"));
            return;
        }
        String scopes = cfg.scopes() != null && !cfg.scopes().isBlank()
                ? cfg.scopes() : "openid email profile";
        String state = stateService.signState(slug, provider);
        String url = cfg.authorizationUri()
                + "?response_type=code"
                + "&client_id=" + enc(cfg.clientId())
                + "&redirect_uri=" + enc(callbackUri(provider))
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&access_type=offline&prompt=select_account";
        response.sendRedirect(url);
    }

    /** Step 2 — provider redirects back with a code; resolve/link/provision, then set session. */
    @GetMapping("/{provider}/callback")
    public void callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws java.io.IOException {

        OAuthStateService.StateData sd;
        try {
            sd = stateService.verifyState(state);
        } catch (Exception e) {
            response.sendRedirect(loginError(null, "invalid_state"));
            return;
        }
        if (error != null || code == null) {
            response.sendRedirect(loginError(sd.slug(), error != null ? error : "no_code"));
            return;
        }
        Company company = companyRepository.findBySlug(sd.slug()).orElse(null);
        if (company == null) {
            response.sendRedirect(loginError(sd.slug(), "unknown_company"));
            return;
        }

        WosHrOAuthClient.OAuthProfile profile;
        try {
            profile = wosHr.exchange(company.getId(), provider, code, callbackUri(provider));
        } catch (Exception e) {
            log.warn("OAuth exchange failed for {}/{}: {}", sd.slug(), provider, e.getMessage());
            response.sendRedirect(loginError(sd.slug(), "exchange_failed"));
            return;
        }

        Optional<LoginOutcome> outcome =
                authService.findOrLinkByOAuth(provider, profile, APP_CLIENT_ID);
        if (outcome.isPresent()) {
            // Soft-deleted account: provider login proved ownership — hand a signed token to the FE,
            // which prompts recover-or-fresh and calls /oauth/reactivate/confirm. No session yet.
            if (outcome.get().requiresReactivation()) {
                String token = stateService.signPendingReactivation(
                        sd.slug(), provider, profile.sub(), profile.email());
                response.sendRedirect(
                        appBaseUrl + "/" + sd.slug() + "/login/reactivate?token=" + enc(token));
                return;
            }
            setTokenCookies(response,
                    outcome.get().result().accessToken(),
                    outcome.get().result().refreshToken());
            // Land on a page that establishes the WorkOS session (mints the role token), like login.
            response.sendRedirect(appBaseUrl + "/" + sd.slug() + "/oauth/complete");
            return;
        }

        // Needs explicit linking (unverified / untrusted) → hand the signed profile to the FE.
        String linkToken = stateService.signPendingLink(
                sd.slug(), provider, profile.sub(), profile.email(), profile.name(), profile.picture());
        response.sendRedirect(appBaseUrl + "/" + sd.slug() + "/login/link?token=" + enc(linkToken));
    }

    /** Step 3 (only when linking) — prove ownership of the existing account, then link + sign in. */
    @PostMapping("/link/confirm")
    public ResponseEntity<LinkConfirmResponse> confirmLink(
            @RequestBody LinkConfirmRequest req, HttpServletResponse response) {
        OAuthStateService.PendingLink pending = stateService.verifyPendingLink(req.token());
        LoginOutcome outcome = authService.confirmLinkByOAuth(
                req.identifier(), req.password(), pending, APP_CLIENT_ID);
        setTokenCookies(response,
                outcome.result().accessToken(), outcome.result().refreshToken());
        return ResponseEntity.ok(new LinkConfirmResponse(
                outcome.requiresCompanySelection(), outcome.companyId()));
    }

    public record LinkConfirmRequest(String token, String identifier, String password) {}

    public record LinkConfirmResponse(boolean requiresCompanySelection, Long companyId) {}

    /**
     * Reactivate a soft-deleted account whose ownership was just proven by an OAuth sign-in. The signed
     * token (issued by the callback only after a successful exchange) IS the proof — no password needed.
     * {@code mode=fresh} wipes accumulated data for a clean start; anything else restores everything.
     */
    @PostMapping("/reactivate/confirm")
    public ResponseEntity<LinkConfirmResponse> reactivateConfirm(
            @RequestBody ReactivateConfirmRequest req, HttpServletResponse response) {
        OAuthStateService.PendingReactivation pending =
                stateService.verifyPendingReactivation(req.token());
        LoginOutcome outcome = authService.reactivateByOAuth(
                pending.provider(), pending.sub(), pending.email(),
                "fresh".equalsIgnoreCase(req.mode()), APP_CLIENT_ID);
        setTokenCookies(response,
                outcome.result().accessToken(), outcome.result().refreshToken());
        return ResponseEntity.ok(new LinkConfirmResponse(
                outcome.requiresCompanySelection(), outcome.companyId()));
    }

    public record ReactivateConfirmRequest(String token, String mode) {}

    // ── helpers ──────────────────────────────────────────────────────────

    /** Canonical callback URL derived from the provider key — must match the provider console + token call. */
    private String callbackUri(String provider) {
        return appBaseUrl + "/api/auth/oauth/" + provider + "/callback";
    }

    private String loginError(String slug, String code) {
        String base = slug != null ? appBaseUrl + "/" + slug + "/login" : appBaseUrl + "/login";
        return base + "?error=" + enc(code);
    }

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addCookie(buildCookie("access_token", accessToken, ACCESS_TOKEN_MAX_AGE));
        response.addCookie(buildCookie("refresh_token", refreshToken, REFRESH_TOKEN_MAX_AGE));
    }

    private Cookie buildCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
