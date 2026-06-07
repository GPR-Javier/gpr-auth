package com.gpr.auth.service;

import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.entity.App;
import com.gpr.auth.entity.RefreshToken;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.RefreshTokenRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.security.JwtService;
import com.gpr.common.dto.AuthRequest;
import com.gpr.common.dto.AuthResponse;
import com.gpr.common.dto.UserSummaryDto;
import com.gpr.common.exception.InvalidTokenException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Identity-only authentication. gpr-auth proves who the caller is, gates app access, and mints a
 * bare IDENTITY token (sub + email + aud — no roles). Per-app roles and the role-bearing access
 * token are resolved by WorkOS (wos-hr {@code /auth/session}) from this identity token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String COMPANY_EMAIL_DOMAIN = "@company.com";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppRepository appRepository;
    private final UserAppAccessRepository userAppAccessRepository;

    @Transactional
    public LoginResult login(AuthRequest request, String clientId) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        // IdP gate: the user must be allowed to use this app (auto-provisioned for self-signup apps).
        enforceAppAccess(user, resolveApp(clientId));

        // Identity established here (sets the cookie). The frontend then calls WorkOS /auth/session
        // to resolve roles and mint the role-bearing token.
        return issueTokens(user, clientId);
    }

    @Transactional
    public LoginResult register(RegisterRequest req, String clientId) {
        String email = req.getEmail().toLowerCase().trim();
        App app = resolveApp(clientId);

        // Register-or-link: an existing identity is linked to this app, never duplicated.
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            grantAccessIfMissing(existing, app);
            log.info("Register-or-link: linked existing identity {} to app '{}'", email, clientId);
            return issueTokens(existing, clientId);
        }

        User user = User.builder()
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .employeeId(generateUniqueIdentityHandle(req.getFirstName(), req.getLastName()))
                .active(true)
                .build();
        userRepository.save(user);
        grantAccessIfMissing(user, app);

        log.info("Registered new identity {} and granted '{}' access", user.getEmail(), clientId);
        return issueTokens(user, clientId);
    }

    /**
     * Internal cross-service identity provisioning (create-or-find). WorkOS "add employee" calls
     * this to obtain a userId before writing its local employee profile. Idempotent on email.
     */
    @Transactional
    public UserSummaryDto createIdentity(String firstName, String lastName, String rawPassword, String clientId) {
        App app = resolveApp(clientId);
        String handle = generateUniqueIdentityHandle(firstName, lastName);
        String email = handle + COMPANY_EMAIL_DOMAIN;

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .firstName(firstName.trim())
                    .lastName(lastName.trim())
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .employeeId(handle)
                    .active(true)
                    .build();
            userRepository.save(user);
            log.info("createIdentity: provisioned identity {} ({})", email, user.getId());
        }
        grantAccessIfMissing(user, app);
        return toSummary(user);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (stored.isRevoked()) throw new InvalidTokenException("Refresh token has been revoked");
        if (stored.isExpired()) throw new InvalidTokenException("Refresh token has expired");
        if (!jwtService.validateToken(rawRefreshToken)) throw new InvalidTokenException("Refresh token is invalid");

        User user = stored.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        return new LoginResult(identityResponse(), newAccessToken, rawRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    // ── helpers ──────────────────────────────────────────────────────

    private LoginResult issueTokens(User user, String clientId) {
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateAccessToken(user, clientId);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(604800))
                .build());

        return new LoginResult(identityResponse(), accessToken, refreshToken);
    }

    /**
     * Minimal identity response — login succeeded and the cookie is set. Role label, authorities,
     * and onboarding state are filled in by WorkOS /auth/session, which the frontend calls next.
     */
    private AuthResponse identityResponse() {
        return AuthResponse.builder()
                .role("")
                .userRoleNames(List.of())
                .authorities(List.of())
                .onboarded(true)
                .onboardingDone(List.of())
                .requiresRoleSelection(false)
                .build();
    }

    // ── app access (IdP) ─────────────────────────────────────────────

    private App resolveApp(String clientId) {
        return appRepository.findByClientId(clientId)
                .filter(App::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Unknown or inactive app: " + clientId));
    }

    private void enforceAppAccess(User user, App app) {
        if (userAppAccessRepository.existsByUserAndApp(user, app)) {
            return;
        }
        if (app.getRegistrationMode() == RegistrationMode.SELF_SIGNUP) {
            grantAccess(user, app);
            log.info("Auto-granted '{}' access to {} on login", app.getClientId(), user.getEmail());
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No access to app '" + app.getClientId() + "'");
        }
    }

    private void grantAccessIfMissing(User user, App app) {
        if (!userAppAccessRepository.existsByUserAndApp(user, app)) {
            grantAccess(user, app);
        }
    }

    private void grantAccess(User user, App app) {
        userAppAccessRepository.save(UserAppAccess.builder()
                .user(user)
                .app(app)
                .active(true)
                .build());
    }

    // ── identity handle generation ───────────────────────────────────

    /** "Gene Paul Mar" + "Javier" => "gpmjavier", suffixed with a counter until unique. */
    private String generateUniqueIdentityHandle(String firstName, String lastName) {
        String base = buildBaseIdentityHandle(firstName, lastName);
        String candidate = base;
        int counter = 1;
        while (identityHandleExists(candidate)) {
            candidate = base + counter;
            counter++;
        }
        return candidate;
    }

    private String buildBaseIdentityHandle(String firstName, String lastName) {
        String initials = java.util.Arrays.stream(firstName.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .map(word -> String.valueOf(word.charAt(0)))
                .collect(java.util.stream.Collectors.joining())
                .toLowerCase();
        String normalizedLastName = lastName.trim().toLowerCase().replaceAll("\\s+", "");
        return initials + normalizedLastName;
    }

    private boolean identityHandleExists(String handle) {
        return userRepository.existsByEmployeeId(handle)
                || userRepository.existsByEmail(handle + COMPANY_EMAIL_DOMAIN);
    }

    private UserSummaryDto toSummary(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }
}
