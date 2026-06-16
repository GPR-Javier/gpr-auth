package com.gpr.auth.controller;

import com.gpr.auth.dto.CertificateDtos;
import com.gpr.auth.dto.EducationDtos;
import com.gpr.auth.dto.WorkExperienceDtos;
import com.gpr.auth.security.JwtService;
import com.gpr.auth.service.CertificateService;
import com.gpr.auth.service.EducationService;
import com.gpr.auth.service.WorkExperienceService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Self-service identity profile sub-resources — education (and, later, work experience and
 * certificates). Scoped to the authenticated identity (the JWT subject); this is user-level data
 * shared across every app the identity belongs to. Mirrors {@code AuthController}'s {@code /me/*}.
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeProfileController {

    private final EducationService educationService;
    private final WorkExperienceService workExperienceService;
    private final CertificateService certificateService;
    private final JwtService jwtService;

    // ── Education ──────────────────────────────────────────────────────────

    @GetMapping("/education")
    public List<EducationDtos.Response> listEducation(HttpServletRequest request) {
        return educationService.list(currentUserId(request));
    }

    @PostMapping("/education")
    public ResponseEntity<EducationDtos.Response> createEducation(
            @Valid @RequestBody EducationDtos.Request body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(educationService.create(currentUserId(request), body));
    }

    @PutMapping("/education/{id}")
    public EducationDtos.Response updateEducation(
            @PathVariable Long id,
            @Valid @RequestBody EducationDtos.Request body,
            HttpServletRequest request) {
        return educationService.update(currentUserId(request), id, body);
    }

    @DeleteMapping("/education/{id}")
    public ResponseEntity<Void> deleteEducation(
            @PathVariable Long id, HttpServletRequest request) {
        educationService.delete(currentUserId(request), id);
        return ResponseEntity.noContent().build();
    }

    // ── Work experience ──────────────────────────────────────────────────────

    @GetMapping("/work-experience")
    public List<WorkExperienceDtos.Response> listWork(HttpServletRequest request) {
        return workExperienceService.list(currentUserId(request));
    }

    @PostMapping("/work-experience")
    public ResponseEntity<WorkExperienceDtos.Response> createWork(
            @Valid @RequestBody WorkExperienceDtos.Request body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workExperienceService.create(currentUserId(request), body));
    }

    @PutMapping("/work-experience/{id}")
    public WorkExperienceDtos.Response updateWork(
            @PathVariable Long id,
            @Valid @RequestBody WorkExperienceDtos.Request body,
            HttpServletRequest request) {
        return workExperienceService.update(currentUserId(request), id, body);
    }

    @DeleteMapping("/work-experience/{id}")
    public ResponseEntity<Void> deleteWork(
            @PathVariable Long id, HttpServletRequest request) {
        workExperienceService.delete(currentUserId(request), id);
        return ResponseEntity.noContent().build();
    }

    // ── Certificates ─────────────────────────────────────────────────────────

    @GetMapping("/certificates")
    public List<CertificateDtos.Response> listCertificates(HttpServletRequest request) {
        return certificateService.list(currentUserId(request));
    }

    @PostMapping("/certificates")
    public ResponseEntity<CertificateDtos.Response> createCertificate(
            @Valid @RequestBody CertificateDtos.Request body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(certificateService.create(currentUserId(request), body));
    }

    @PutMapping("/certificates/{id}")
    public CertificateDtos.Response updateCertificate(
            @PathVariable Long id,
            @Valid @RequestBody CertificateDtos.Request body,
            HttpServletRequest request) {
        return certificateService.update(currentUserId(request), id, body);
    }

    @DeleteMapping("/certificates/{id}")
    public ResponseEntity<Void> deleteCertificate(
            @PathVariable Long id, HttpServletRequest request) {
        certificateService.delete(currentUserId(request), id);
        return ResponseEntity.noContent().build();
    }

    // ── helpers (mirror AuthController) ──────────────────────────────────────

    /** Resolves the authenticated identity id (sub) from the access-token cookie/bearer. */
    private Long currentUserId(HttpServletRequest request) {
        String token = extractCookie(request, "access_token");
        if (token == null) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7).trim();
            }
        }
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return jwtService.extractUserId(token);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
