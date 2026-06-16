package com.gpr.auth.dto;

import com.gpr.auth.entity.UserCertificate;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

/** Request/response payloads for self-service certificate entries. */
public final class CertificateDtos {

    private CertificateDtos() {}

    /** Create/update body. {@code expiryDate} null = no expiry. */
    @Data
    public static class Request {
        @NotBlank
        private String name;

        @NotBlank
        private String issuer;

        private LocalDate issuedDate;
        private LocalDate expiryDate;
        private String credentialId;
        private String credentialUrl;
    }

    public record Response(
            Long id,
            String name,
            String issuer,
            LocalDate issuedDate,
            LocalDate expiryDate,
            String credentialId,
            String credentialUrl) {

        public static Response from(UserCertificate c) {
            return new Response(
                    c.getId(),
                    c.getName(),
                    c.getIssuer(),
                    c.getIssuedDate(),
                    c.getExpiryDate(),
                    c.getCredentialId(),
                    c.getCredentialUrl());
        }
    }
}
