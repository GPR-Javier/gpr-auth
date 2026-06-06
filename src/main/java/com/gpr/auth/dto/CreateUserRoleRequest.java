package com.gpr.auth.dto;

import com.gpr.common.enums.RoleType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRoleRequest {
    @NotBlank private String name;
    private String description;
    private String color;
    private RoleType roleType;
}
