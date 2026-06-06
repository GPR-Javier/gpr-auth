package com.gpr.auth.service;

import com.gpr.auth.dto.AssignAccessRoleRequest;
import com.gpr.auth.dto.CreateUserRoleRequest;
import com.gpr.auth.dto.ToggleFunctionalityRequest;
import com.gpr.auth.dto.UpdateUserRoleRequest;
import com.gpr.auth.repository.AccessRoleRepository;
import com.gpr.auth.repository.FunctionalityRepository;
import com.gpr.auth.repository.UserRoleRepository;
import com.gpr.common.dto.AccessRoleDTO;
import com.gpr.common.dto.FunctionalityDTO;
import com.gpr.common.dto.UserRoleDTO;
import com.gpr.common.entity.AccessRole;
import com.gpr.common.entity.Functionality;
import com.gpr.common.entity.UserRole;
import com.gpr.common.enums.RoleType;
import com.gpr.common.exception.DuplicateResourceException;
import com.gpr.common.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final AccessRoleRepository accessRoleRepository;
    private final FunctionalityRepository functionalityRepository;

    @Transactional
    public UserRoleDTO createRole(CreateUserRoleRequest request) {
        if (userRoleRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("User role already exists: " + request.getName());
        }
        UserRole role = UserRole.builder()
                .name(request.getName())
                .description(request.getDescription())
                .color(request.getColor())
                .roleType(request.getRoleType() != null ? request.getRoleType() : RoleType.EMPLOYEE)
                .active(true)
                .build();
        return toDTO(userRoleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<UserRoleDTO> getAllRoles() {
        return userRoleRepository.findAll().stream().map(this::toDTO).toList();
    }

    /** Lightweight list for dropdowns — only active roles, id + name only */
    @Transactional(readOnly = true)
    public List<UserRoleDTO> getActiveRoles() {
        return userRoleRepository.findAllByActiveTrue().stream()
                .map(r -> UserRoleDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .color(r.getColor())
                        .roleType(r.getRoleType())
                        .active(true)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccessRoleDTO> getAllAccessRoles() {
        return accessRoleRepository.findAll().stream().map(this::toAccessRoleDTO).toList();
    }

    @Transactional
    public UserRoleDTO updateRole(Long id, UpdateUserRoleRequest request) {
        UserRole role = getOrThrow(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            role.setName(request.getName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getColor() != null) {
            role.setColor(request.getColor());
        }
        if (request.getRoleType() != null) {
            role.setRoleType(request.getRoleType());
        }
        return toDTO(userRoleRepository.save(role));
    }

    @Transactional
    public void softDeleteRole(Long id) {
        UserRole role = getOrThrow(id);
        role.setActive(false);
        userRoleRepository.save(role);
    }

    @Transactional
    public UserRoleDTO addAccessRole(Long roleId, AssignAccessRoleRequest request) {
        UserRole role = getOrThrow(roleId);
        AccessRole accessRole = accessRoleRepository.findById(request.getAccessRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Access role not found: " + request.getAccessRoleId()));

        boolean alreadyAssigned = role.getAccessRoles().stream()
                .anyMatch(ar -> ar.getId().equals(accessRole.getId()));
        if (!alreadyAssigned) {
            role.addAccessRole(accessRole);
            userRoleRepository.save(role);
        }
        return toDTO(role);
    }

    @Transactional
    public void removeAccessRole(Long roleId, Long accessRoleId) {
        UserRole role = getOrThrow(roleId);
        role.getAccessRoles().stream()
                .filter(ar -> ar.getId().equals(accessRoleId))
                .findFirst()
                .ifPresent(role::removeAccessRole);
        userRoleRepository.save(role);
    }

    @Transactional
    public FunctionalityDTO toggleFunctionality(Long accessRoleId, Long functionalityId, ToggleFunctionalityRequest request) {
        AccessRole accessRole = accessRoleRepository.findById(accessRoleId)
                .orElseThrow(() -> new ResourceNotFoundException("Access role not found: " + accessRoleId));

        Functionality functionality = accessRole.getFunctionalities().stream()
                .filter(f -> f.getId().equals(functionalityId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Functionality not found in access role: " + functionalityId));

        functionality.setEnabled(request.isEnabled());
        functionalityRepository.save(functionality);

        return toFunctionalityDTO(functionality);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private UserRole getOrThrow(Long id) {
        return userRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User role not found: " + id));
    }

    private UserRoleDTO toDTO(UserRole role) {
        return UserRoleDTO.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .color(role.getColor())
                .roleType(role.getRoleType())
                .active(role.isActive())
                .accessRoles(role.getAccessRoles().stream().map(this::toAccessRoleDTO).toList())
                .build();
    }

    private AccessRoleDTO toAccessRoleDTO(AccessRole ar) {
        return AccessRoleDTO.builder()
                .id(ar.getId())
                .pageName(ar.getPageName())
                .pageCode(ar.getPageCode())
                .navGroup(ar.getNavGroup())
                .functionalities(ar.getFunctionalities().stream().map(this::toFunctionalityDTO).toList())
                .build();
    }

    private FunctionalityDTO toFunctionalityDTO(Functionality f) {
        return FunctionalityDTO.builder()
                .id(f.getId())
                .name(f.getName())
                .code(f.getCode() != null ? f.getCode().getCode() : null)
                .enabled(f.isEnabled())
                .controlType(f.getControlType() != null ? f.getControlType().name().toLowerCase() : null)
                .build();
    }
}
