package com.gpr.auth.controller;

import com.gpr.auth.dto.AssignAccessRoleRequest;
import com.gpr.auth.dto.CreateUserRoleRequest;
import com.gpr.auth.dto.ToggleFunctionalityRequest;
import com.gpr.auth.dto.UpdateUserRoleRequest;
import com.gpr.auth.service.UserRoleService;
import com.gpr.common.dto.FunctionalityDTO;
import com.gpr.common.dto.UserRoleDTO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-roles")
@RequiredArgsConstructor
public class AdminUserRoleController {

    private final UserRoleService userRoleService;

    // ── User role CRUD ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:CREATE_ROLE')")
    public ResponseEntity<UserRoleDTO> createRole(@Valid @RequestBody CreateUserRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRoleService.createRole(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:VIEW_ROLES')")
    public ResponseEntity<List<UserRoleDTO>> getAllRoles() {
        return ResponseEntity.ok(userRoleService.getAllRoles());
    }

    /** Used by FE dropdowns — returns only active user roles (id, name, description) */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:VIEW_ROLES') or hasAuthority('USER_MANAGEMENT:ASSIGN_ROLES')")
    public ResponseEntity<List<UserRoleDTO>> getActiveRoles() {
        return ResponseEntity.ok(userRoleService.getActiveRoles());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:EDIT_ROLE')")
    public ResponseEntity<UserRoleDTO> updateRole(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(userRoleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:DELETE_ROLE')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        userRoleService.softDeleteRole(id);
        return ResponseEntity.ok().build();
    }

    // ── Access role assignment ────────────────────────────────────────

    @PostMapping("/{id}/access-roles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE')")
    public ResponseEntity<UserRoleDTO> addAccessRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignAccessRoleRequest request
    ) {
        return ResponseEntity.ok(userRoleService.addAccessRole(id, request));
    }

    @DeleteMapping("/{id}/access-roles/{accessRoleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:REMOVE_ACCESS_ROLE')")
    public ResponseEntity<Void> removeAccessRole(@PathVariable Long id, @PathVariable Long accessRoleId) {
        userRoleService.removeAccessRole(id, accessRoleId);
        return ResponseEntity.ok().build();
    }

    // ── Functionality toggle ──────────────────────────────────────────

    @PutMapping("/{id}/access-roles/{accessRoleId}/functionalities/{functionalityId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:TOGGLE_FUNCTIONALITY')")
    public ResponseEntity<FunctionalityDTO> toggleFunctionality(
            @PathVariable Long id,
            @PathVariable Long accessRoleId,
            @PathVariable Long functionalityId,
            @RequestBody ToggleFunctionalityRequest request
    ) {
        return ResponseEntity.ok(userRoleService.toggleFunctionality(accessRoleId, functionalityId, request));
    }
}
