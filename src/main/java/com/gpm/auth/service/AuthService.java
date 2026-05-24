package com.gpm.auth.service;

import com.gpm.auth.dto.LoginResult;
import com.gpm.auth.dto.RoleSelectionRequest;
import com.gpm.auth.dto.SwitchRoleRequest;
import com.gpm.common.entity.RefreshToken;
import com.gpm.common.exception.InvalidTokenException;
import com.gpm.common.exception.ResourceNotFoundException;
import com.gpm.auth.repository.RefreshTokenRepository;
import com.gpm.auth.repository.UserRepository;
import com.gpm.auth.security.JwtService;
import com.gpm.common.dto.AuthRequest;
import com.gpm.common.dto.AuthResponse;
import com.gpm.common.dto.RoleInfo;
import com.gpm.common.dto.UserDTO;
import com.gpm.common.dto.UserRoleSummaryDTO;
import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.Functionality;
import com.gpm.common.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserRoleAccessResolver userRoleAccessResolver;

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
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    public UserDTO me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
        UserDTO dto = toDTO(user);
        dto.setAuthorities(computeEffectiveAuthorities(user));
        return dto;
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
        String roleLabel = isAdmin ? "ADMIN" : "EMPLOYEE";

        return AuthResponse.builder()
                .role(roleLabel)
                .userRoleNames(userRoleNames)
                .authorities(authorities)
                .requiresRoleSelection(false)
                .build();
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
