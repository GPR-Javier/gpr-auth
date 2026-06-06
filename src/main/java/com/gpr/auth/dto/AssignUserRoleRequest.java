package com.gpr.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AssignUserRoleRequest {
    @NotNull private List<Long> userRoleIds;
}
