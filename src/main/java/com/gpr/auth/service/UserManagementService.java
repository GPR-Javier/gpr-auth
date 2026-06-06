package com.gpr.auth.service;

import com.gpr.auth.dto.AssignUserRoleRequest;
import com.gpr.auth.dto.CreateUserRequest;
import com.gpr.auth.dto.TemporaryAccessRequest;
import com.gpr.auth.dto.TemporaryUserRoleAssignmentDTO;
import com.gpr.auth.repository.JobPositionRepository;
import com.gpr.auth.repository.UserPositionRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.repository.UserRoleAssignmentRepository;
import com.gpr.auth.repository.UserRoleRepository;
import com.gpr.common.dto.UserDTO;
import com.gpr.common.dto.UserRoleSummaryDTO;
import com.gpr.common.entity.JobPosition;
import com.gpr.common.entity.User;
import com.gpr.common.entity.UserPosition;
import com.gpr.common.entity.UserRole;
import com.gpr.common.entity.UserRoleAssignment;
import com.gpr.common.enums.Role;
import com.gpr.common.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String COMPANY_EMAIL_DOMAIN = "@company.com";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final JobPositionRepository jobPositionRepository;
    private final UserPositionRepository userPositionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        List<UserRole> userRoles = request.getUserRoleIds().stream()
                .map(id -> userRoleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Employee role not found: " + id)))
                .toList();

        String handle = generateUniqueIdentityHandle(request.getFirstName(), request.getLastName());
        String employeeId = handle;
        String email = handle + COMPANY_EMAIL_DOMAIN;

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .employeeId(employeeId)
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        userRoles.forEach(user::addRoleAssignment);
        User saved = userRepository.save(user);

        // Assign job positions (first in list is primary)
        List<Long> positionIds = request.getJobPositionIds();
        for (int i = 0; i < positionIds.size(); i++) {
            Long posId = positionIds.get(i);
            JobPosition position = jobPositionRepository.findById(posId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job position not found: " + posId));
            UserPosition up = UserPosition.builder()
                    .user(saved)
                    .jobPosition(position)
                    .primary(i == 0)
                    .build();
            userPositionRepository.save(up);
        }

        return toDTO(saved);
    }

    /**
     * Builds a unique user handle from first-name initials + last name.
     * Example: "Gene Paul Mar" + "Javier" => "gpmjavier".
     */
    private String generateUniqueIdentityHandle(String firstName, String lastName) {
        String baseHandle = buildBaseIdentityHandle(firstName, lastName);
        String candidateHandle = baseHandle;
        int counter = 1;
        while (identityHandleExists(candidateHandle)) {
            candidateHandle = baseHandle + counter;
            counter++;
        }
        return candidateHandle;
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

    /** Replaces all role assignments with the given list as permanent (no time window). */
    @Transactional
    public UserDTO assignUserRoles(Long userId, AssignUserRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<UserRole> roles = request.getUserRoleIds().stream()
                .map(id -> userRoleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Employee role not found: " + id)))
                .toList();

        Set<Long> targetRoleIds = new HashSet<Long>(request.getUserRoleIds());

        // Remove roles no longer selected.
        user.getRoleAssignments().removeIf(assignment ->
                assignment.getUserRole() == null
                        || assignment.getUserRole().getId() == null
                        || !targetRoleIds.contains(assignment.getUserRole().getId())
        );

        // Keep existing selected roles and make them permanent.
        user.getRoleAssignments().forEach(assignment -> {
            assignment.setStartAt(null);
            assignment.setEndAt(null);
        });

        // Add only missing roles to avoid insert-before-delete unique conflicts.
        roles.forEach(user::addRoleAssignment);
        return toDTO(userRepository.save(user));
    }

    /**
     * Sets or clears the temporary-access window on an existing role assignment.
     * Sending all four date/time fields as null clears the restriction (role becomes permanent).
     */
    @Transactional
    public TemporaryUserRoleAssignmentDTO setTemporaryAccess(Long userId, Long roleId, TemporaryAccessRequest request) {
        if (request.getRoleId() != null && !request.getRoleId().equals(roleId)) {
            throw new IllegalArgumentException("Path roleId must match payload roleId");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        UserRole userRole = userRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee role not found: " + roleId));

        LocalDateTime startAt = resolveStartAt(request.getStartDate(), request.getStartTime(),
                request.getEndDate(), request.getEndTime());
        LocalDateTime endAt = resolveEndAt(request.getStartDate(), request.getStartTime(),
                request.getEndDate(), request.getEndTime());

        UserRoleAssignment assignment = roleAssignmentRepository
                .findByUserIdAndUserRoleId(userId, roleId)
                .orElseGet(() -> UserRoleAssignment.builder().user(user).userRole(userRole).build());

        assignment.setStartAt(startAt);
        assignment.setEndAt(endAt);
        return toAssignmentDTO(roleAssignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<TemporaryUserRoleAssignmentDTO> getRoleAssignments(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return roleAssignmentRepository.findByUserId(userId).stream()
                .map(this::toAssignmentDTO)
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private LocalDateTime resolveStartAt(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
        validateWindowInputs(startDate, startTime, endDate, endTime);
        if (startDate == null) return null;
        return LocalDateTime.of(startDate, startTime != null ? startTime : LocalTime.MIN);
    }

    private LocalDateTime resolveEndAt(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
        validateWindowInputs(startDate, startTime, endDate, endTime);
        if (endDate == null) return null;
        LocalDateTime startAt = LocalDateTime.of(startDate, startTime != null ? startTime : LocalTime.MIN);
        LocalDateTime resolvedEnd = LocalDateTime.of(endDate, endTime != null ? endTime : LocalTime.of(23, 59, 59));
        if (resolvedEnd.isBefore(startAt)) {
            throw new IllegalArgumentException("Temporary access end date/time must be after start date/time");
        }
        return resolvedEnd;
    }

    private void validateWindowInputs(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {
        boolean hasStartDate = startDate != null;
        boolean hasEndDate = endDate != null;
        if (hasStartDate != hasEndDate) {
            throw new IllegalArgumentException("Both startDate and endDate are required when using a temporary access window");
        }
        if (!hasStartDate && (startTime != null || endTime != null)) {
            throw new IllegalArgumentException("startTime/endTime cannot be provided without startDate/endDate");
        }
    }

    private TemporaryUserRoleAssignmentDTO toAssignmentDTO(UserRoleAssignment assignment) {
        LocalDateTime startAt = assignment.getStartAt();
        LocalDateTime endAt = assignment.getEndAt();
        return TemporaryUserRoleAssignmentDTO.builder()
                .assignmentId(assignment.getId())
                .userRoleId(assignment.getUserRole().getId())
                .userRoleName(assignment.getUserRole().getName())
                .startDate(startAt != null ? startAt.toLocalDate() : null)
                .endDate(endAt != null ? endAt.toLocalDate() : null)
                .startTime(startAt != null ? startAt.toLocalTime() : null)
                .endTime(endAt != null ? endAt.toLocalTime() : null)
                .active(!assignment.isPermanent())
                .currentlyActive(assignment.isActiveAt(LocalDateTime.now()))
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
}
