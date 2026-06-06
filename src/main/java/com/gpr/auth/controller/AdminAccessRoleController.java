package com.gpr.auth.controller;

import com.gpr.auth.service.UserRoleService;
import com.gpr.common.dto.AccessRoleDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/access-roles")
@RequiredArgsConstructor
public class AdminAccessRoleController {

    private final UserRoleService userRoleService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLES_AND_PERMISSIONS:VIEW_ROLES') or hasAuthority('ROLES_AND_PERMISSIONS:ASSIGN_ACCESS_ROLE')")
    public ResponseEntity<List<AccessRoleDTO>> getAllAccessRoles() {
        return ResponseEntity.ok(userRoleService.getAllAccessRoles());
    }
}
