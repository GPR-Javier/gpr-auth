package com.gpm.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class TemporaryUserRoleAssignmentDTO {
    private Long assignmentId;
    private Long userRoleId;
    private String userRoleName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean active;
    private boolean currentlyActive;
}

