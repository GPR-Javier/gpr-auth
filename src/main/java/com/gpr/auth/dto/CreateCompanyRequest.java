package com.gpr.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Super-admin payload to stand up a new company plus its first Company Admin identity. */
@Data
public class CreateCompanyRequest {
    @NotBlank private String name;
    @NotBlank private String slug;

    @NotBlank private String adminFirstName;
    @NotBlank private String adminLastName;
    private String adminEmail;
    private String adminUsername;

    @NotBlank
    @Size(min = 8, message = "Admin password must be at least 8 characters")
    private String adminPassword;
}
