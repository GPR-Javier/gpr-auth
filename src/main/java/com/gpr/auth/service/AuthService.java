package com.gpr.auth.service;

import com.gpr.auth.client.WosHrOAuthClient;
import com.gpr.auth.dto.CompanyInfo;
import com.gpr.auth.dto.IdentityCreateRequest;
import com.gpr.auth.dto.LoginResult;
import com.gpr.auth.dto.RegisterRequest;
import com.gpr.auth.dto.UpdateCredentialsRequest;
import com.gpr.auth.dto.UpdateInfoRequest;
import com.gpr.auth.entity.App;
import com.gpr.auth.entity.Company;
import com.gpr.auth.entity.LoginMethod;
import com.gpr.auth.entity.RefreshToken;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.entity.UserCompany;
import com.gpr.auth.entity.UserInfo;
import com.gpr.auth.enums.LoginMethodType;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.repository.RefreshTokenRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
import com.gpr.auth.repository.UserCompanyRepository;
import com.gpr.auth.repository.UserInfoRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.security.JwtService;
import com.gpr.kernel.dto.AuthRequest;
import com.gpr.kernel.dto.AuthResponse;
import com.gpr.kernel.dto.UserSummaryDto;
import com.gpr.kernel.exception.InvalidTokenException;
import com.gpr.kernel.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final com.gpr.auth.repository.LoginMethodRepository loginMethodRepository;

    /** Tokens + the tenant-selection state the client needs after authenticating. */
    public record LoginOutcome(LoginResult result, List<CompanyInfo> companies,
                               boolean requiresCompanySelection, Long companyId,
                               boolean requiresReactivation) {}

    @Transactional
    public LoginOutcome login(AuthRequest request, String clientId) {
        // The submitted identifier (carried in the email field) may be email, username, or phone.
        String identifier = request.getEmail();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
            );
        } catch (org.springframework.security.authentication.DisabledException ex) {
            // A soft-deleted account is "disabled". If the credentials are right, don't fail — signal
            // the client to offer recovery (or a fresh start) instead of a dead-end login error.
            User deleted = userRepository.findByEmailOrUsernameOrPhone(identifier, identifier, identifier)
                    .orElse(null);
            if (deleted != null && deleted.getDeletedAt() != null
                    && passwordEncoder.matches(request.getPassword(), deleted.getPassword())) {
                return new LoginOutcome(null, List.of(), false, null, true);
            }
            throw ex; // genuinely disabled, or wrong password for a deleted account
        }

        User user = userRepository.findByEmailOrUsernameOrPhone(identifier, identifier, identifier)
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        enforceAppAccess(user, resolveApp(clientId));

        List<CompanyInfo> companies = companiesForUser(user);
        if (companies.size() == 1) {
            // Single company → select it now and mint a tenant-scoped token.
            Long companyId = companies.get(0).id();
            return new LoginOutcome(issueTokens(user, companyId, clientId), companies, false, companyId, false);
        }
        // 0 (no company yet) or many (must choose) → identity token without a company.
        return new LoginOutcome(issueTokens(user, null, clientId), companies, companies.size() > 1, null, false);
    }

    /** Selects (or switches to) a company: validates access, re-mints the token with companyId. */
    @Transactional
    public LoginOutcome selectCompany(Long userId, Long companyId, String clientId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        if (!canAccess(user, companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to company " + companyId);
        }
        return new LoginOutcome(issueTokens(user, companyId, clientId), companiesForUser(user), false, companyId, false);
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
        ensurePasswordMethod(user);

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
            ensurePasswordMethod(user);
            log.info("createIdentity: provisioned identity {} ({})", email, user.getId());
        } else {
            info = userInfoRepository.findByUserId(user.getId()).orElse(null);
        }
        grantAccessIfMissing(user, app);
        linkCompanyIfPresent(user, req.getCompanyId());
        return userDirectoryService.toSummary(user, info);
    }

    /** Links an existing identity (by id) to a company — idempotent. For app-driven promotions
     * (e.g. an applicant being hired) so the user gains the company at the identity level and can
     * select it / resolve the employee session without re-registering. */
    @Transactional
    public void linkCompany(Long userId, Long companyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        linkCompanyIfPresent(user, companyId);
    }

    /** Idempotently links the identity to the provisioning company so it appears in the user's
     * company list at login (and the client can resolve the company slug instead of "guest"). */
    private void linkCompanyIfPresent(User user, Long companyId) {
        if (companyId == null) return;
        if (userCompanyRepository.existsByUserIdAndCompanyId(user.getId(), companyId)) return;
        Company company = companyRepository.findById(companyId)
                .filter(Company::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown or inactive company " + companyId));
        userCompanyRepository.save(UserCompany.builder()
                .user(user)
                .company(company)
                .active(true)
                .build());
        log.info("createIdentity: linked identity {} to company {}", user.getId(), companyId);
    }

    // ── OAuth sign-in / linking ──────────────────────────────────────

    /**
     * Resolves an OAuth callback to a signed-in session, or signals that explicit linking is needed.
     *   - (provider, sub) already linked → sign in.
     *   - email matches an account: trusted provider + verified email → auto-link; else empty (confirm).
     *   - no match → provision a new identity (no password) + link.
     * Empty result ⇒ the caller must run the confirm-link flow.
     */
    @Transactional
    public Optional<LoginOutcome> findOrLinkByOAuth(
            String provider, WosHrOAuthClient.OAuthProfile profile, String clientId) {
        Optional<LoginMethod> linked =
                loginMethodRepository.findByProviderAndExternalSubject(provider, profile.sub());
        if (linked.isPresent()) {
            return Optional.of(outcomeForUser(linked.get().getUser(), clientId));
        }

        String email = profile.email() == null ? null : profile.email().toLowerCase().trim();
        User byEmail = email == null ? null : userRepository.findByEmail(email).orElse(null);
        if (byEmail != null) {
            if (isTrusted(provider) && profile.emailVerified()) {
                linkOAuth(byEmail, provider, profile.sub());
                grantAccessIfMissing(byEmail, resolveApp(clientId));
                return Optional.of(outcomeForUser(byEmail, clientId));
            }
            return Optional.empty(); // unverified or untrusted → require explicit linking
        }

        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider did not return an email.");
        }
        User user = provisionFromOAuth(provider, profile, email);
        grantAccessIfMissing(user, resolveApp(clientId));
        return Optional.of(outcomeForUser(user, clientId));
    }

    /** Confirm-link: prove ownership of the existing account (password), then attach the OAuth method. */
    @Transactional
    public LoginOutcome confirmLinkByOAuth(
            String identifier, String password,
            com.gpr.auth.security.OAuthStateService.PendingLink pending, String clientId) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, password));
        User user = userRepository.findByEmailOrUsernameOrPhone(identifier, identifier, identifier)
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));
        // Only link to the account that owns the provider's email.
        if (pending.email() != null && !pending.email().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Confirm from the account that owns " + pending.email() + ".");
        }
        linkOAuth(user, pending.provider(), pending.sub());
        grantAccessIfMissing(user, resolveApp(clientId));
        return outcomeForUser(user, clientId);
    }

    private LoginOutcome outcomeForUser(User user, String clientId) {
        List<CompanyInfo> companies = companiesForUser(user);
        if (companies.size() == 1) {
            Long companyId = companies.get(0).id();
            return new LoginOutcome(issueTokens(user, companyId, clientId), companies, false, companyId, false);
        }
        return new LoginOutcome(issueTokens(user, null, clientId), companies, companies.size() > 1, null, false);
    }

    private void linkOAuth(User user, String provider, String sub) {
        if (loginMethodRepository.existsByUserAndProvider(user, provider)) return;
        loginMethodRepository.save(LoginMethod.builder()
                .user(user)
                .type(mapLoginType(provider))
                .provider(provider)
                .externalSubject(sub)
                .active(true)
                .build());
    }

    private User provisionFromOAuth(String provider, WosHrOAuthClient.OAuthProfile profile, String email) {
        String[] names = splitName(profile.name());
        User user = User.builder()
                .email(email)
                .username(generateUniqueUsername(names[0], names[1], null))
                // No usable password — sign-in is via the provider. Placeholder satisfies NOT NULL.
                .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .active(true)
                .build();
        userRepository.save(user);
        userInfoRepository.save(UserInfo.builder()
                .user(user)
                .firstName(names[0])
                .lastName(names[1])
                .profilePhoto(blankToNull(profile.picture()))
                .build());
        loginMethodRepository.save(LoginMethod.builder()
                .user(user)
                .type(mapLoginType(provider))
                .provider(provider)
                .externalSubject(profile.sub())
                .active(true)
                .build());
        log.info("OAuth provisioned identity {} via {}", email, provider);
        return user;
    }

    private static String[] splitName(String name) {
        if (name == null || name.isBlank()) return new String[] {"New", "User"};
        String[] parts = name.trim().split("\\s+", 2);
        return new String[] {parts[0], parts.length > 1 ? parts[1] : ""};
    }

    /** Only these providers' verified-email claim is trusted for silent auto-link. */
    private static boolean isTrusted(String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return p.equals("google") || p.equals("microsoft");
    }

    private static LoginMethodType mapLoginType(String provider) {
        String p = provider == null ? "" : provider.toLowerCase();
        return switch (p) {
            case "google" -> LoginMethodType.GOOGLE;
            case "microsoft" -> LoginMethodType.MICROSOFT;
            default -> LoginMethodType.OAUTH;
        };
    }

    // ── account deletion (self-service) ──────────────────────────────

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** Everything an identity owns — purged before the {@code users} row on a hard delete / fresh start. */
    private static final String[] HARD_PURGE = {
        "RefreshToken", "LoginMethod", "UserInfo", "UserCompany", "UserAppAccess",
        "PasswordHistory", "UserEducation", "UserWorkExperience", "UserCertificate"
    };

    /**
     * SOFT-deletes the authenticated identity: marks it deleted + deactivated and kills all sessions,
     * but RETAINS every owned record so the user can recover it by signing in again (see
     * {@link #reactivate}). The caller must retype their email/username to confirm.
     */
    @Transactional
    public void deleteAccount(Long userId, String confirm) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        boolean ok = confirm != null
                && (confirm.trim().equalsIgnoreCase(user.getEmail())
                        || confirm.trim().equalsIgnoreCase(user.getUsername()));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Type your email to confirm account deletion.");
        }
        user.setActive(false);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        // Invalidate existing sessions so the deactivated account can't keep acting on stale tokens.
        entityManager.createQuery("delete from RefreshToken x where x.user = :u")
                .setParameter("u", user)
                .executeUpdate();
        log.info("Soft-deleted identity {} ({}) — data retained for recovery", user.getEmail(), userId);
    }

    /**
     * Recovers a soft-deleted account on re-login (credentials re-verified). {@code fresh=false}
     * restores everything as-is. {@code fresh=true} is a TRUE hard delete: the account and everything
     * it owns are erased, then a blank identity is re-provisioned under the same login (new userId) —
     * any WorkOS data keyed by the old userId is harmlessly orphaned. Returns a login outcome.
     */
    @Transactional
    public LoginOutcome reactivate(AuthRequest request, boolean fresh, String clientId) {
        String identifier = request.getEmail();
        User user = userRepository.findByEmailOrUsernameOrPhone(identifier, identifier, identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));
        if (user.getDeletedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account isn't pending reactivation.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }
        App app = resolveApp(clientId);

        if (fresh) {
            // Reuse the login identifiers + the just-verified password for the clean account.
            String email = user.getEmail();
            String username = user.getUsername();
            String phone = user.getPhone();
            String passwordHash = passwordEncoder.encode(request.getPassword());

            for (String entity : HARD_PURGE) {
                entityManager.createQuery("delete from " + entity + " x where x.user = :u")
                        .setParameter("u", user)
                        .executeUpdate();
            }
            userRepository.delete(user);
            entityManager.flush(); // release the unique email/username before re-inserting

            User recreated = userRepository.save(User.builder()
                    .email(email).username(username).phone(phone)
                    .password(passwordHash).active(true).build());
            userInfoRepository.save(UserInfo.builder().user(recreated).build());
            ensurePasswordMethod(recreated);
            grantAccessIfMissing(recreated, app);
            log.info("Fresh start: hard-deleted identity {} and re-provisioned it as new id {}",
                    email, recreated.getId());
            // Brand-new account → no companies → identity token (careers/applicant portal).
            return new LoginOutcome(issueTokens(recreated, null, clientId), List.of(), false, null, false);
        }

        // Recover: restore the account exactly as it was.
        user.setActive(true);
        user.setDeletedAt(null);
        userRepository.save(user);
        grantAccessIfMissing(user, app);
        log.info("Reactivated identity {} ({}) — data restored", user.getEmail(), user.getId());

        List<CompanyInfo> companies = companiesForUser(user);
        if (companies.size() == 1) {
            Long companyId = companies.get(0).id();
            return new LoginOutcome(issueTokens(user, companyId, clientId), companies, false, companyId, false);
        }
        return new LoginOutcome(issueTokens(user, null, clientId), companies, companies.size() > 1, null, false);
    }

    // ── login methods (self-service security screen) ─────────────────

    public record LoginMethodsView(String email, boolean hasPassword, List<String> providers) {}

    @Transactional(readOnly = true)
    public LoginMethodsView loginMethods(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        List<LoginMethod> active = loginMethodRepository.findByUserAndActiveTrue(user);
        List<String> providers = active.stream()
                .map(LoginMethod::getProvider)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
        boolean passwordMethod = active.stream()
                .anyMatch(m -> m.getType() == LoginMethodType.PASSWORD);
        // Legacy fallback: a user with no OAuth methods can only sign in by password.
        boolean hasPassword = passwordMethod || providers.isEmpty();
        return new LoginMethodsView(user.getEmail(), hasPassword, providers);
    }

    /**
     * Disconnects a sign-in method (an OAuth provider key, or "password"), refusing to remove the
     * user's only remaining method. Disconnecting "password" also invalidates the stored credential.
     */
    @Transactional
    public void removeLoginMethod(Long userId, String provider) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        List<LoginMethod> active = loginMethodRepository.findByUserAndActiveTrue(user);

        if ("password".equalsIgnoreCase(provider)) {
            long oauthCount = active.stream().filter(m -> m.getProvider() != null).count();
            if (oauthCount == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Can't disconnect your only sign-in method.");
            }
            active.stream()
                    .filter(m -> m.getType() == LoginMethodType.PASSWORD)
                    .forEach(loginMethodRepository::delete);
            // Make the credential unusable so password sign-in stops working.
            user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            userRepository.save(user);
            return;
        }

        LoginMethod target = active.stream()
                .filter(m -> provider != null && provider.equalsIgnoreCase(m.getProvider()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, provider + " is not connected."));
        boolean passwordMethod = active.stream()
                .anyMatch(m -> m.getType() == LoginMethodType.PASSWORD);
        long otherProviders = active.stream()
                .filter(m -> m.getProvider() != null && !m.getProvider().equalsIgnoreCase(provider))
                .count();
        if (!passwordMethod && otherProviders == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Can't disconnect your only sign-in method.");
        }
        loginMethodRepository.delete(target);
    }

    /**
     * Sets or changes the account password. When the user already has a password, the current one
     * must be supplied and match; when they have none (e.g. OAuth-only), this *adds* password sign-in.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        List<LoginMethod> active = loginMethodRepository.findByUserAndActiveTrue(user);
        boolean passwordMethod = active.stream()
                .anyMatch(m -> m.getType() == LoginMethodType.PASSWORD);
        boolean hasPassword = passwordMethod
                || active.stream().noneMatch(m -> m.getProvider() != null);
        if (hasPassword
                && (currentPassword == null
                        || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        LoginMethod pm = loginMethodRepository.findByUserAndType(user, LoginMethodType.PASSWORD)
                .orElseGet(() -> LoginMethod.builder()
                        .user(user).type(LoginMethodType.PASSWORD).active(true).build());
        pm.setSecretHash(user.getPassword());
        loginMethodRepository.save(pm);
    }

    /** Records that the user has a password login (idempotent). secretHash mirrors the bcrypt on User. */
    private void ensurePasswordMethod(User user) {
        if (loginMethodRepository.findByUserAndType(user, LoginMethodType.PASSWORD).isPresent()) return;
        loginMethodRepository.save(LoginMethod.builder()
                .user(user)
                .type(LoginMethodType.PASSWORD)
                .secretHash(user.getPassword())
                .active(true)
                .build());
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
        if (req.getDisplayName() != null) info.setDisplayName(blankToNull(req.getDisplayName()));
        if (req.getBio() != null) info.setBio(blankToNull(req.getBio()));
        if (req.getProfilePhoto() != null) info.setProfilePhoto(blankToNull(req.getProfilePhoto()));
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
