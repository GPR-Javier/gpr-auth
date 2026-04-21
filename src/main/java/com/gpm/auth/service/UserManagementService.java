package com.gpm.auth.service;

import com.gpm.auth.dto.AssignUserRoleRequest;
import com.gpm.auth.dto.CreateUserRequest;
import com.gpm.common.exception.DuplicateResourceException;
import com.gpm.common.exception.ResourceNotFoundException;
import com.gpm.auth.repository.UserRoleRepository;
import com.gpm.auth.repository.UserRepository;
import com.gpm.common.dto.UserDTO;
import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.User;
import com.gpm.common.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }

        List<UserRole> userRoles = request.getUserRoleIds().stream()
                .map(id -> userRoleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Employee role not found: " + id)))
                .toList();

        int next = userRepository.findMaxEmployeeIdSequence().orElse(0) + 1;
        String employeeId = String.format("EMP-%03d", next);

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .employeeId(employeeId)
                .role(Role.EMPLOYEE)
                .userRoles(userRoles)
                .active(true)
                .build();

        return toDTO(userRepository.save(user));
    }

    @Transactional
    public void softDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(int page, int size, String roleFilter) {
        Pageable pageable = PageRequest.of(page, size);

        if (roleFilter != null && !roleFilter.isBlank()) {
            Role role = Role.valueOf(roleFilter.toUpperCase());
            return userRepository.findAllByRole(role, pageable).map(this::toDTO);
        }
        return userRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public UserDTO assignUserRoles(Long userId, AssignUserRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<UserRole> roles = request.getUserRoleIds().stream()
                .map(id -> userRoleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Employee role not found: " + id)))
                .toList();

        user.getUserRoles().clear();
        user.getUserRoles().addAll(roles);
        return toDTO(userRepository.save(user));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .employeeId(user.getEmployeeId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .userRoleNames(user.getUserRoles().stream().map(UserRole::getName).toList())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
