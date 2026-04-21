package com.gpm.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignAccessRoleRequest {
    @NotNull private Long accessRoleId;
}
