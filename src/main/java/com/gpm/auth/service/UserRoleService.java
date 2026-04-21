package com.gpm.auth.service;

import com.gpm.auth.dto.AssignAccessRoleRequest;
import com.gpm.auth.dto.CreateUserRoleRequest;
import com.gpm.auth.dto.ToggleFunctionalityRequest;
import com.gpm.auth.dto.UpdateUserRoleRequest;
import com.gpm.common.exception.DuplicateResourceException;
import com.gpm.common.exception.ResourceNotFoundException;
import com.gpm.auth.repository.AccessRoleRepository;
import com.gpm.auth.repository.UserRoleRepository;
import com.gpm.auth.repository.FunctionalityRepository;
import com.gpm.common.dto.AccessRoleDTO;
import com.gpm.common.dto.UserRoleDTO;
import com.gpm.common.dto.FunctionalityDTO;
import com.gpm.common.entity.AccessRole;
import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.Functionality;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .active(true)
                .build();
        return toDTO(userRoleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public List<UserRoleDTO> getAllRoles() {
        return userRoleRepository.findAll().stream().map(this::toDTO).toList();
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
            role.getAccessRoles().add(accessRole);
            userRoleRepository.save(role);
        }
        return toDTO(role);
    }

    @Transactional
    public void removeAccessRole(Long roleId, Long accessRoleId) {
        UserRole role = getOrThrow(roleId);
        role.getAccessRoles().removeIf(ar -> ar.getId().equals(accessRoleId));
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
                .active(role.isActive())
                .accessRoles(role.getAccessRoles().stream().map(this::toAccessRoleDTO).toList())
                .build();
    }

    private AccessRoleDTO toAccessRoleDTO(AccessRole ar) {
        return AccessRoleDTO.builder()
                .id(ar.getId())
                .pageName(ar.getPageName())
                .pageCode(ar.getPageCode())
                .functionalities(ar.getFunctionalities().stream().map(this::toFunctionalityDTO).toList())
                .build();
    }

    private FunctionalityDTO toFunctionalityDTO(Functionality f) {
        return FunctionalityDTO.builder()
                .id(f.getId())
                .name(f.getName())
                .code(f.getCode().getCode())
                .enabled(f.isEnabled())
                .build();
    }
}
