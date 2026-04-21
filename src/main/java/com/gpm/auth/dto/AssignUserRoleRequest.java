package com.gpm.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AssignUserRoleRequest {
    @NotNull private List<Long> userRoleIds;
}
