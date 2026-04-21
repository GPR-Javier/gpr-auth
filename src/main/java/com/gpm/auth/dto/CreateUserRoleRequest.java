package com.gpm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRoleRequest {
    @NotBlank private String name;
    private String description;
}
