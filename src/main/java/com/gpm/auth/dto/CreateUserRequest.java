package com.gpm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateUserRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank private String password;
    private List<Long> userRoleIds = new ArrayList<>();
    /** Optional: job position IDs to assign on creation (many-to-many). First in list is primary. */
    private List<Long> jobPositionIds = new ArrayList<>();
}
