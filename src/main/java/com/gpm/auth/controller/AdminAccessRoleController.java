package com.gpm.auth.controller;

import com.gpm.auth.service.UserRoleService;
import com.gpm.common.dto.AccessRoleDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/access-roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccessRoleController {

    private final UserRoleService userRoleService;

    @GetMapping
    public ResponseEntity<List<AccessRoleDTO>> getAllAccessRoles() {
        return ResponseEntity.ok(userRoleService.getAllAccessRoles());
    }
}
