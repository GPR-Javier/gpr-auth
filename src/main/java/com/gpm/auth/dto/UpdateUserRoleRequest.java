package com.gpm.auth.dto;

import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    private String name;
    private String description;
}
