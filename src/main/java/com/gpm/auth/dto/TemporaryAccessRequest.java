package com.gpm.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Sets or clears the temporary-access window on an existing role assignment.
 * roleId identifies which role assignment to update.
 * Send all four fields as null to clear the restriction (role becomes permanently active).
 */
@Data
public class TemporaryAccessRequest {
    @NotNull
    private Long roleId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
}

