package com.gpr.auth.dto;

import com.gpr.auth.entity.UserEducation;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

/** Request/response payloads for self-service education entries. */
public final class EducationDtos {

    private EducationDtos() {}

    /** Create/update body. {@code endDate} null = ongoing. */
    @Data
    public static class Request {
        @NotBlank
        private String school;

        @NotBlank
        private String degree;

        private String fieldOfStudy;
        private LocalDate startDate;
        private LocalDate endDate;
        private String honor;
        private String description;
    }

    public record Response(
            Long id,
            String school,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            String honor,
            String description) {

        public static Response from(UserEducation e) {
            return new Response(
                    e.getId(),
                    e.getSchool(),
                    e.getDegree(),
                    e.getFieldOfStudy(),
                    e.getStartDate(),
                    e.getEndDate(),
                    e.getHonor(),
                    e.getDescription());
        }
    }
}
