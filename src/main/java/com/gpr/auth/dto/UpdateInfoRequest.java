package com.gpr.auth.dto;

import java.time.LocalDate;
import lombok.Data;

/** Self-service edit of canonical personal info. All fields optional — only non-null ones are applied. */
@Data
public class UpdateInfoRequest {
    private String firstName;
    private String lastName;
    private String middleName;
    private LocalDate birthday;
    private String address;
    private String gender;
}
