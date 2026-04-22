package com.gpm.auth.controller;

import com.gpm.auth.dto.AssignUserRoleRequest;
import com.gpm.auth.dto.CreateUserRequest;
import com.gpm.auth.dto.TemporaryAccessRequest;
import com.gpm.auth.dto.TemporaryUserRoleAssignmentDTO;
import com.gpm.auth.service.UserManagementService;
import com.gpm.common.dto.UserDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserManagementService userManagementService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:CREATE_USER')")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:DELETE_USER')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userManagementService.softDeleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:VIEW_USERS') or hasAuthority('USER_MANAGEMENT:FILTER_USERS_BY_ROLE')")
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String role
    ) {
        return ResponseEntity.ok(userManagementService.getAllUsers(page, size, role));
    }

    /** Replace all permanent role assignments (no time window). */
    @PutMapping("/{id}/employee-roles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:ASSIGN_ROLES') or hasAuthority('ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE')")
    public ResponseEntity<UserDTO> assignUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignUserRoleRequest request
    ) {
        return ResponseEntity.ok(userManagementService.assignUserRoles(id, request));
    }

    /**
     * List all role assignments for a user (permanent + time-windowed).
     * GET /admin/users/{id}/employee-roles/assignments
     */
    @GetMapping("/{id}/employee-roles/assignments")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:VIEW_USERS') or hasAuthority('ROLES_AND_PERMISSIONS:VIEW_ROLES')")
    public ResponseEntity<List<TemporaryUserRoleAssignmentDTO>> getRoleAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getRoleAssignments(id));
    }

    /**
     * Set or clear a temporary-access window on an existing role assignment.
     * Send all four date/time fields as null to clear the restriction (role becomes permanent).
     * PUT /admin/users/{userId}/employee-roles/{roleId}/temporary-access
     */
    @PutMapping("/{userId}/employee-roles/{roleId}/temporary-access")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT:ASSIGN_ROLES') or hasAuthority('ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE')")
    public ResponseEntity<TemporaryUserRoleAssignmentDTO> setTemporaryAccess(
            @PathVariable Long userId,
            @PathVariable Long roleId,
            @Valid @RequestBody TemporaryAccessRequest request
    ) {
        if (!roleId.equals(request.getRoleId())) {
            throw new IllegalArgumentException("Path roleId must match payload roleId");
        }
        return ResponseEntity.ok(userManagementService.setTemporaryAccess(userId, roleId, request));
    }
}
