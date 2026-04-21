package com.gpm.auth.controller;

import com.gpm.auth.dto.AssignAccessRoleRequest;
import com.gpm.auth.dto.CreateUserRoleRequest;
import com.gpm.auth.dto.ToggleFunctionalityRequest;
import com.gpm.auth.dto.UpdateUserRoleRequest;
import com.gpm.auth.service.UserRoleService;
import com.gpm.common.dto.UserRoleDTO;
import com.gpm.common.dto.FunctionalityDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/user-roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserRoleController {

    private final UserRoleService userRoleService;

    // ── User role CRUD ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<UserRoleDTO> createRole(@Valid @RequestBody CreateUserRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userRoleService.createRole(request));
    }

    @GetMapping
    public ResponseEntity<List<UserRoleDTO>> getAllRoles() {
        return ResponseEntity.ok(userRoleService.getAllRoles());
    }

    /** Used by FE dropdowns — returns only active user roles (id, name, description) */
    @GetMapping("/active")
    public ResponseEntity<List<UserRoleDTO>> getActiveRoles() {
        return ResponseEntity.ok(userRoleService.getActiveRoles());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserRoleDTO> updateRole(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(userRoleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        userRoleService.softDeleteRole(id);
        return ResponseEntity.ok().build();
    }

    // ── Access role assignment ────────────────────────────────────────

    @PostMapping("/{id}/access-roles")
    public ResponseEntity<UserRoleDTO> addAccessRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignAccessRoleRequest request
    ) {
        return ResponseEntity.ok(userRoleService.addAccessRole(id, request));
    }

    @DeleteMapping("/{id}/access-roles/{accessRoleId}")
    public ResponseEntity<Void> removeAccessRole(@PathVariable Long id, @PathVariable Long accessRoleId) {
        userRoleService.removeAccessRole(id, accessRoleId);
        return ResponseEntity.ok().build();
    }

    // ── Functionality toggle ──────────────────────────────────────────

    @PutMapping("/{id}/access-roles/{accessRoleId}/functionalities/{functionalityId}")
    public ResponseEntity<FunctionalityDTO> toggleFunctionality(
            @PathVariable Long id,
            @PathVariable Long accessRoleId,
            @PathVariable Long functionalityId,
            @RequestBody ToggleFunctionalityRequest request
    ) {
        return ResponseEntity.ok(userRoleService.toggleFunctionality(accessRoleId, functionalityId, request));
    }
}
