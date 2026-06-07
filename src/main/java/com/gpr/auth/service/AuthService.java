package com.gpr.auth.service;

import com.gpr.auth.dto.CompanyInfo;
import com.gpr.auth.dto.IdentityCreateRequest;
import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.dto.UpdateCredentialsRequest;
import com.gpr.auth.dto.UpdateInfoRequest;
import com.gpr.auth.entity.App;
import com.gpr.auth.entity.Company;
import com.gpr.auth.entity.RefreshToken;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.entity.UserCompany;
import com.gpr.auth.entity.UserInfo;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.repository.RefreshTokenRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
import com.gpr.auth.repository.UserCompanyRepository;
import com.gpr.auth.repository.UserInfoRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.security.JwtService;
import com.gpr.common.dto.AuthRequest;
import com.gpr.common.dto.AuthResponse;
import com.gpr.common.dto.UserSummaryDto;
import com.gpr.common.exception.InvalidTokenException;
import com.gpr.common.exception.ResourceNotFoundException;
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
 * Identity-only authentication over split credentials ({@link User}) + canonical personal info
 * ({@link UserInfo}). Proves who the caller is, gates app access, and mints a bare IDENTITY token;
 * per-app roles + the role-bearing token are resolved by WorkOS (wos-hr {@code /auth/session}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String COMPANY_EMAIL_DOMAIN = "@gpr.com";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppRepository appRepository;
    private final UserAppAccessRepository userAppAccessRepository;
    private final UserDirectoryService userDirectoryService;
    private final CompanyRepository companyRepository;
    private final UserCompanyRepository userCompanyRepository;

    /** Tokens + the tenant-selection state the client needs after authenticating. */
    public record LoginOutcome(LoginResult result, List<CompanyInfo> companies,
                               boolean requiresCompanySelection, Long companyId) {}

    @Transactional
    public LoginOutcome login(AuthRequest request, String clientId) {
        // The submitted identifier (carried in the email field) may be email, username, or phone.
        String identifier = request.getEmail();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
        );

        User user = userRepository.findByEmailOrUsernameOrPhone(identifier, identifier, identifier)
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        enforceAppAccess(user, resolveApp(clientId));

        List<CompanyInfo> companies = companiesForUser(user);
        if (companies.size() == 1) {
            // Single company → select it now and mint a tenant-scoped token.
            Long companyId = companies.get(0).id();
            return new LoginOutcome(issueTokens(user, companyId, clientId), companies, false, companyId);
        }
        // 0 (no company yet) or many (must choose) → identity token without a company.
        return new LoginOutcome(issueTokens(user, null, clientId), companies, companies.size() > 1, null);
    }

    /** Selects (or switches to) a company: validates access, re-mints the token with companyId. */
    @Transactional
    public LoginOutcome selectCompany(Long userId, Long companyId, String clientId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        if (!canAccess(user, companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to company " + companyId);
        }
        return new LoginOutcome(issueTokens(user, companyId, clientId), companiesForUser(user), false, companyId);
    }

    @Transactional(readOnly = true)
    public List<CompanyInfo> companiesForUser(Long userId) {
        return userRepository.findById(userId).map(this::companiesForUser).orElse(List.of());
    }

    private List<CompanyInfo> companiesForUser(User user) {
        if (user.isSuperAdmin()) {
            return companyRepository.findAll().stream()
                    .filter(Company::isActive)
                    .map(c -> new CompanyInfo(c.getId(), c.getName(), c.getSlug()))
                    .toList();
        }
        return userCompanyRepository.findByUserId(user.getId()).stream()
                .filter(UserCompany::isActive)
                .map(UserCompany::getCompany)
                .filter(Company::isActive)
                .map(c -> new CompanyInfo(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    private boolean canAccess(User user, Long companyId) {
        if (user.isSuperAdmin()) {
            return companyRepository.findById(companyId).filter(Company::isActive).isPresent();
        }
        return userCompanyRepository.existsByUserIdAndCompanyId(user.getId(), companyId);
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
            return issueTokens(existing, null, clientId);
        }

        User user = User.builder()
                .email(email)
                .username(generateUniqueUsername(req.getFirstName(), req.getLastName(), req.getUsername()))
                .phone(blankToNull(req.getPhone()))
                .password(passwordEncoder.encode(req.getPassword()))
                .active(true)
                .build();
        userRepository.save(user);
        userInfoRepository.save(UserInfo.builder()
                .user(user)
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .birthday(req.getBirthday())
                .address(blankToNull(req.getAddress()))
                .build());
        grantAccessIfMissing(user, app);

        log.info("Registered new identity {} and granted '{}' access", user.getEmail(), clientId);
        return issueTokens(user, null, clientId);
    }

    /**
     * Internal cross-service identity provisioning (create-or-find). WorkOS "add employee" calls
     * this to obtain a userId + canonical info before writing its local employee profile. Generates
     * the username/email when the caller doesn't supply them. Idempotent on email.
     */
    @Transactional
    public UserSummaryDto createIdentity(IdentityCreateRequest req, String clientId) {
        App app = resolveApp(clientId);
        String username = generateUniqueUsername(req.getFirstName(), req.getLastName(), req.getUsername());
        String email = req.getEmail() != null && !req.getEmail().isBlank()
                ? req.getEmail().toLowerCase().trim()
                : username + COMPANY_EMAIL_DOMAIN;

        User user = userRepository.findByEmail(email).orElse(null);
        UserInfo info;
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .username(username)
                    .phone(blankToNull(req.getPhone()))
                    .password(passwordEncoder.encode(req.getPassword()))
                    .active(true)
                    .build();
            userRepository.save(user);
            info = userInfoRepository.save(UserInfo.builder()
                    .user(user)
                    .firstName(req.getFirstName().trim())
                    .lastName(req.getLastName().trim())
                    .birthday(req.getBirthday())
                    .address(blankToNull(req.getAddress()))
                    .build());
            log.info("createIdentity: provisioned identity {} ({})", email, user.getId());
        } else {
            info = userInfoRepository.findByUserId(user.getId()).orElse(null);
        }
        grantAccessIfMissing(user, app);
        return userDirectoryService.toSummary(user, info);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (stored.isRevoked()) throw new InvalidTokenException("Refresh token has been revoked");
        if (stored.isExpired()) throw new InvalidTokenException("Refresh token has expired");
        if (!jwtService.validateToken(rawRefreshToken)) throw new InvalidTokenException("Refresh token is invalid");

        String newAccessToken = jwtService.generateAccessToken(stored.getUser());
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

    // ── account self-service (shared across all apps — UI warns) ──────

    @Transactional(readOnly = true)
    public UserSummaryDto getAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return userDirectoryService.toSummary(user, userInfoRepository.findByUserId(userId).orElse(null));
    }

    /** Updates login credentials (email / username / phone / password). Affects sign-in to all apps. */
    @Transactional
    public UserSummaryDto updateCredentials(Long userId, UpdateCredentialsRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (notBlank(req.getEmail())) {
            String email = req.getEmail().toLowerCase().trim();
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(email);
        }
        if (notBlank(req.getUsername())) {
            String username = req.getUsername().trim();
            if (!username.equalsIgnoreCase(user.getUsername()) && userRepository.existsByUsername(username)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
            }
            user.setUsername(username);
        }
        if (req.getPhone() != null) {
            String phone = blankToNull(req.getPhone());
            if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone already in use");
            }
            user.setPhone(phone);
        }
        if (notBlank(req.getNewPassword())) {
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }
        userRepository.save(user);
        return userDirectoryService.toSummary(user, userInfoRepository.findByUserId(userId).orElse(null));
    }

    /** Updates canonical personal info. Affects apps the user hasn't customized / joins later. */
    @Transactional
    public UserSummaryDto updateInfo(Long userId, UpdateInfoRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        UserInfo info = userInfoRepository.findByUserId(userId)
                .orElseGet(() -> UserInfo.builder().user(user).build());

        if (req.getFirstName() != null) info.setFirstName(blankToNull(req.getFirstName()));
        if (req.getLastName() != null) info.setLastName(blankToNull(req.getLastName()));
        if (req.getMiddleName() != null) info.setMiddleName(blankToNull(req.getMiddleName()));
        if (req.getBirthday() != null) info.setBirthday(req.getBirthday());
        if (req.getAddress() != null) info.setAddress(blankToNull(req.getAddress()));
        if (req.getGender() != null) info.setGender(blankToNull(req.getGender()));
        userInfoRepository.save(info);
        return userDirectoryService.toSummary(user, info);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private LoginResult issueTokens(User user, Long companyId, String clientId) {
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateAccessToken(user, companyId, clientId);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(604800))
                .build());

        return new LoginResult(identityResponse(), accessToken, refreshToken);
    }

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

    // ── username generation ──────────────────────────────────────────

    /** Uses the requested username if free, else "gpmjavier"-style handle suffixed until unique. */
    private String generateUniqueUsername(String firstName, String lastName, String requested) {
        if (notBlank(requested) && !userRepository.existsByUsername(requested.trim())) {
            return requested.trim();
        }
        String base = buildBaseHandle(firstName, lastName);
        String candidate = base;
        int counter = 1;
        while (userRepository.existsByUsername(candidate)
                || userRepository.existsByEmail(candidate + COMPANY_EMAIL_DOMAIN)) {
            candidate = base + counter;
            counter++;
        }
        return candidate;
    }

    private String buildBaseHandle(String firstName, String lastName) {
        String initials = java.util.Arrays.stream(firstName.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .map(word -> String.valueOf(word.charAt(0)))
                .collect(java.util.stream.Collectors.joining())
                .toLowerCase();
        String normalizedLastName = lastName.trim().toLowerCase().replaceAll("\\s+", "");
        return initials + normalizedLastName;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
