package com.gpm.auth.controller;

import com.gpm.auth.dto.AssignUserRoleRequest;
import com.gpm.auth.dto.CreateUserRequest;
import com.gpm.auth.service.UserManagementService;
import com.gpm.common.dto.UserDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserManagementService userManagementService;

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userManagementService.softDeleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String role
    ) {
        return ResponseEntity.ok(userManagementService.getAllUsers(page, size, role));
    }

    @PutMapping("/{id}/employee-roles")
    public ResponseEntity<UserDTO> assignUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignUserRoleRequest request
    ) {
        return ResponseEntity.ok(userManagementService.assignUserRoles(id, request));
    }
}
