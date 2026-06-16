package com.gpr.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** Internal cross-service identity provisioning payload (WorkOS "add employee" → gpr-auth). */
@Data
public class IdentityCreateRequest {
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Optional login identifiers — generated when blank. */
    private String email;
    private String username;
    private String phone;

    /** Optional canonical personal info. */
    private LocalDate birthday;
    private String address;

    /** Company the identity is being provisioned into — links a UserCompany membership so the
     * employee resolves their tenant (and slug) at login. Null for company-less identities. */
    private Long companyId;
}
