package com.gpr.auth.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Data;

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
