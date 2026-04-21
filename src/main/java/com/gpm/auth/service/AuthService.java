package com.gpm.auth.service;

import com.gpm.auth.dto.LoginResult;
import com.gpm.auth.dto.RoleSelectionRequest;
import com.gpm.auth.entity.RefreshToken;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public LoginResult login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        if (user.getUserRoles().size() > 1) {
            List<RoleInfo> availableRoles = user.getUserRoles().stream()
                    .map(er -> RoleInfo.builder().id(er.getId()).name(er.getName()).description(er.getDescription()).build())
                    .toList();
            AuthResponse response = AuthResponse.builder()
                    .requiresRoleSelection(true)
                    .availableRoles(availableRoles)
                    .build();
            return new LoginResult(response, null, null);
        }

        return issueTokens(user, user.getUserRoles().isEmpty() ? null : user.getUserRoles().get(0));
    }

    @Transactional
    public LoginResult loginWithRole(RoleSelectionRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User disappeared after authentication"));

        UserRole selectedRole = user.getUserRoles().stream()
                .filter(er -> er.getId().equals(request.getUserRoleId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User role not assigned to this user"));

        return issueTokens(user, selectedRole);
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
        AuthResponse response = buildAuthResponse(user, null);

        return new LoginResult(response, newAccessToken, rawRefreshToken);
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
        return toDTO(user);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private LoginResult issueTokens(User user, UserRole selectedRole) {
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = selectedRole != null
                ? jwtService.generateAccessToken(user, selectedRole)
                : jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(604800))
                .build());

        return new LoginResult(buildAuthResponse(user, selectedRole), accessToken, refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, UserRole selectedRole) {
        List<UserRole> roles = selectedRole != null ? List.of(selectedRole) : user.getUserRoles();

        List<String> authorities = roles.stream()
                .flatMap(er -> er.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .map(f -> f.getCode().getCode())
                .distinct()
                .toList();

        List<String> userRoleNames = roles.stream().map(UserRole::getName).toList();

        return AuthResponse.builder()
                .role(user.getRole().name())
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
                .userRoles(user.getUserRoles().stream()
                        .map(role -> UserRoleSummaryDTO.builder()
                                .roleId(role.getId())
                                .roleName(role.getName())
                                .roleColor(role.getColor())
                                .build())
                        .toList())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
