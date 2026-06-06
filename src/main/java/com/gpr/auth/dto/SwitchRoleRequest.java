package com.gpr.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SwitchRoleRequest {
    @NotNull
    private Long userRoleId;
}
