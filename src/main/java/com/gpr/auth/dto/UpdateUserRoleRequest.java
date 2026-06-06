package com.gpr.auth.dto;

import com.gpr.common.enums.RoleType;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    private String name;
    private String description;
    private String color;
    private RoleType roleType; // null means no change
}
