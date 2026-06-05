package com.gpm.auth.service;

import com.gpm.auth.dto.LoginResult;
import com.gpm.auth.dto.RegisterRequest;
import com.gpm.auth.dto.RoleSelectionRequest;
import com.gpm.auth.dto.SwitchRoleRequest;
import com.gpm.auth.repository.RefreshTokenRepository;
import com.gpm.auth.repository.UserRepository;
import com.gpm.auth.repository.UserRoleRepository;
import com.gpm.auth.security.JwtService;
import com.gpm.common.dto.AuthRequest;
import com.gpm.common.dto.AuthResponse;
import com.gpm.common.dto.RoleInfo;
import com.gpm.common.dto.UserDTO;
import com.gpm.common.dto.UserRoleSummaryDTO;
import com.gpm.common.entity.Functionality;
import com.gpm.common.entity.RefreshToken;
import com.gpm.common.entity.User;
import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.UserRoleAssignment;
import com.gpm.common.enums.Role;
import com.gpm.common.enums.RoleType;
import com.gpm.common.exception.DuplicateResourceException;
import com.gpm.common.exception.InvalidTokenException;
import com.gpm.common.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserRoleAccessResolver userRoleAccessResolver;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResult login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        log.debug("Authentication succeeded for {}; loading user entity", request.getEmail());
        User user;
        List<UserRole> activeRoles;
        try {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));
            log.debug("User {} loaded, resolving active roles (assignments={})", request.getEmail(), user.getRoleAssignments().size());
            activeRoles = userRoleAccessResolver.resolveActiveUserRoles(user);
            log.debug("Active roles for {}: {}", request.getEmail(), activeRoles.stream().map(UserRole::getName).toList());
        } catch (Exception e) {
            log.error("Post-authentication failure for {}: [{}] {}", request.getEmail(), e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }

        if (activeRoles.size() > 1) {
            LocalDateTime now = LocalDateTime.now();
            List<RoleInfo> availableRoles = activeRoles.stream()
                    .map(er -> RoleInfo.builder()
                            .id(er.getId())
                            .name(er.getName())
                            .description(er.getDescription())
                            .temporary(isTemporaryRole(user, er.getId(), now))
                            .onboarded(isRoleOnboarded(user, er.getId()))
                            .build())
                    .toList();
            AuthResponse response = AuthResponse.builder()
                    .requiresRoleSelection(true)
                    .availableRoles(availableRoles)
                    .build();
            return new LoginResult(response, null, null);
        }

        return issueTokens(user, activeRoles.isEmpty() ? null : activeRoles.get(0), activeRoles);
    }

    @Transactional
    public LoginResult loginWithRole(RoleSelectionRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        List<UserRole> activeRoles = userRoleAccessResolver.resolveActiveUserRoles(user);

        UserRole selectedRole = activeRoles.stream()
                .filter(er -> er.getId().equals(request.getUserRoleId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User role not active for this user"));

        return issueTokens(user, selectedRole, activeRoles);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (stored.isRevoked()) throw new InvalidTokenException("Refresh token has been revoked");
        if (stored.isExpired()) throw new InvalidTokenException("Refresh token has expired");
        if (!jwtService.validateToken(rawRefreshToken)) throw new InvalidTokenException("Refresh token is invalid");

        User user = stored.getUser();
        List<UserRole> activeRoles = userRoleAccessResolver.resolveActiveUserRoles(user);
        String newAccessToken = jwtService.generateAccessToken(user, activeRoles);
        AuthResponse response = buildAuthResponse(user, null, activeRoles);

        return new LoginResult(response, newAccessToken, rawRefreshToken);
    }

    /**
     * Switches the active role for an already-authenticated user.
     * No password required â€” identity is verified via the existing JWT.
     * Returns a new access token scoped to the selected role; refresh token is unchanged.
     */
    @Transactional
    public LoginResult switchRole(String email, SwitchRoleRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));

        List<UserRole> activeRoles = userRoleAccessResolver.resolveActiveUserRoles(user);

        UserRole selectedRole = activeRoles.stream()
                .filter(er -> er.getId().equals(request.getUserRoleId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User role not active for this user"));

        String newAccessToken = jwtService.generateAccessToken(user, selectedRole);
        AuthResponse authResponse = buildAuthResponse(user, selectedRole, activeRoles);

        // Refresh token is not rotated â€” caller should keep their existing refresh_token cookie
        return new LoginResult(authResponse, newAccessToken, null);
    }

    @Transactional
    public LoginResult register(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail().toLowerCase().trim()).isPresent()) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        UserRole applicantRole = userRoleRepository.findByName("Applicant")
                .orElseThrow(() -> new IllegalStateException("Applicant role not seeded — restart the auth service"));

        User user = User.builder()
                .firstName(req.getFirstName().trim())
                .lastName(req.getLastName().trim())
                .email(req.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(req.getPassword()))
                .employeeId("APP-" + System.currentTimeMillis())
                .role(Role.APPLICANT)
                .active(true)
                .build();
        user.addRoleAssignment(applicantRole);
        userRepository.save(user);

        log.info("Registered new applicant: {}", user.getEmail());
        return issueTokens(user, applicantRole, List.of(applicantRole));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    public UserDTO me(String email, Long activeRoleId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
        UserDTO dto = toDTO(user);
        dto.setAuthorities(computeEffectiveAuthorities(user));

        UserRoleAssignment active = findAssignment(user, activeRoleId);
        // Unknown/ambiguous active role → treat as onboarded so we never force an unintended tour.
        dto.setOnboarded(active == null || active.isOnboarded());
        dto.setOnboardingDone(active != null ? new ArrayList<>(active.getOnboardingDone()) : List.of());
        return dto;
    }

    /**
     * Appends a screen key to the active role's per-screen onboarding progress (idempotent).
     * Returns the refreshed onboarding state so the caller can update its store without a re-login.
     */
    @Transactional
    public AuthResponse completeScreenOnboarding(String email, Long activeRoleId, String screenKey) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
        UserRoleAssignment active = requireAssignment(user, activeRoleId);

        List<String> done = new ArrayList<>(active.getOnboardingDone());
        if (!done.contains(screenKey)) {
            done.add(screenKey);
            active.setOnboardingDone(done); // new list instance so Hibernate detects the JSON change
            userRepository.save(user);
        }
        return buildAuthResponse(user, active.getUserRole(), userRoleAccessResolver.resolveActiveUserRoles(user));
    }

    /** Marks the active role as fully onboarded (the global "skip all"). */
    @Transactional
    public AuthResponse skipOnboarding(String email, Long activeRoleId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
        UserRoleAssignment active = requireAssignment(user, activeRoleId);
        active.setOnboarded(true);
        userRepository.save(user);
        return buildAuthResponse(user, active.getUserRole(), userRoleAccessResolver.resolveActiveUserRoles(user));
    }

    /**
     * Effective authorities for the user *right now* — flattened from currently-active UserRoles
     * to AccessRoles to enabled Functionalities. Matches the logic in CustomUserDetailsService so
     * what /auth/me returns is consistent with what the JWT filter enforces on protected endpoints.
     */
    private List<String> computeEffectiveAuthorities(User user) {
        return userRoleAccessResolver.resolveActiveUserRoles(user).stream()
                .flatMap(ur -> ur.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .filter(f -> f.getCode() != null)
                .map(f -> f.getCode().getCode())
                .distinct()
                .toList();
    }

    // â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private LoginResult issueTokens(User user, UserRole selectedRole, List<UserRole> activeRoles) {
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = selectedRole != null
                ? jwtService.generateAccessToken(user, selectedRole)
                : jwtService.generateAccessToken(user, activeRoles);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(604800))
                .build());

        return new LoginResult(buildAuthResponse(user, selectedRole, activeRoles), accessToken, refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, UserRole selectedRole, List<UserRole> activeRoles) {
        List<UserRole> roles = selectedRole != null ? List.of(selectedRole) : activeRoles;

        List<String> authorities = roles.stream()
                .flatMap(er -> er.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .filter(f -> f.getCode() != null)
                .map(f -> f.getCode().getCode())
                .distinct()
                .toList();

        List<String> userRoleNames = roles.stream().map(UserRole::getName).toList();

        // Derive role label from whether any active role is an admin-type role.
        // This replaces the static User.role column as the driver for the sidebar badge.
        boolean isAdmin = roles.stream().anyMatch(UserRole::isAdmin);
        boolean isApplicant = roles.stream().anyMatch(r -> r.getRoleType() == RoleType.APPLICANT);
        String roleLabel = isAdmin ? "ADMIN" : (isApplicant ? "APPLICANT" : "EMPLOYEE");

        // Onboarding state of the *active* role: the selected role if one was chosen, otherwise
        // the sole active role. When it can't be resolved unambiguously, default to onboarded so
        // we never force an unintended tour.
        Long activeRoleId = selectedRole != null ? selectedRole.getId()
                : (activeRoles.size() == 1 ? activeRoles.get(0).getId() : null);
        UserRoleAssignment activeAssignment = findAssignment(user, activeRoleId);
        boolean onboarded = activeAssignment == null || activeAssignment.isOnboarded();
        List<String> onboardingDone = activeAssignment != null
                ? new ArrayList<>(activeAssignment.getOnboardingDone())
                : List.of();

        return AuthResponse.builder()
                .role(roleLabel)
                .userRoleNames(userRoleNames)
                .authorities(authorities)
                .onboarded(onboarded)
                .onboardingDone(onboardingDone)
                .requiresRoleSelection(false)
                .build();
    }

    /** Finds the permanent role assignment backing the given role id, or null. */
    private UserRoleAssignment findAssignment(User user, Long roleId) {
        if (roleId == null) {
            return null;
        }
        return user.getRoleAssignments().stream()
                .filter(a -> a.getUserRole() != null && roleId.equals(a.getUserRole().getId()))
                .findFirst()
                .orElse(null);
    }

    private UserRoleAssignment requireAssignment(User user, Long roleId) {
        UserRoleAssignment assignment = findAssignment(user, roleId);
        if (assignment == null) {
            throw new ResourceNotFoundException("No active role assignment to onboard for this user");
        }
        return assignment;
    }

    private boolean isRoleOnboarded(User user, Long roleId) {
        UserRoleAssignment assignment = findAssignment(user, roleId);
        return assignment == null || assignment.isOnboarded();
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .userRoles(user.getRoleAssignments().stream()
                        .map(a -> UserRoleSummaryDTO.builder()
                                .roleId(a.getUserRole().getId())
                                .roleName(a.getUserRole().getName())
                                .roleColor(a.getUserRole().getColor())
                                .temporary(!a.isPermanent())
                                .startAt(a.getStartAt())
                                .endAt(a.getEndAt())
                                .build())
                        .toList())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private boolean isTemporaryRole(User user, Long roleId, LocalDateTime now) {
        if (roleId == null) {
            return false;
        }
        return user.getRoleAssignments().stream()
                .filter(assignment -> assignment.getUserRole() != null
                        && roleId.equals(assignment.getUserRole().getId())
                        && assignment.isActiveAt(now))
                .findFirst()
                .map(assignment -> !assignment.isPermanent())
                .orElse(false);
    }
}
