package com.gpr.auth.dto;

import com.gpr.auth.entity.UserWorkExperience;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

/** Request/response payloads for self-service work-experience entries. */
public final class WorkExperienceDtos {

    private WorkExperienceDtos() {}

    /** Create/update body. {@code endDate} null = current role. */
    @Data
    public static class Request {
        @NotBlank
        private String title;

        @NotBlank
        private String company;

        private String employmentType;
        private String location;
        private LocalDate startDate;
        private LocalDate endDate;
        private String description;
    }

    public record Response(
            Long id,
            String title,
            String company,
            String employmentType,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            String description) {

        public static Response from(UserWorkExperience w) {
            return new Response(
                    w.getId(),
                    w.getTitle(),
                    w.getCompany(),
                    w.getEmploymentType(),
                    w.getLocation(),
                    w.getStartDate(),
                    w.getEndDate(),
                    w.getDescription());
        }
    }
}
