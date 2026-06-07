package com.gpr.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Self-service edit of login credentials. All fields optional — only non-blank ones are applied. */
@Data
public class UpdateCredentialsRequest {
    private String email;
    private String username;
    private String phone;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
