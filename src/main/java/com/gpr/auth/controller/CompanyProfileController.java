package com.gpr.auth.controller;

import com.gpr.auth.dto.CompanyInfo;
import com.gpr.auth.service.CompanyService;
import com.gpr.common.dto.CompanyProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shared company's descriptive profile. Internal — called by WorkOS (wos-hr) on behalf of a
 * company admin; the edit is gated in wos-hr by {@code CONFIGURATION:EDIT_COMPANY_DETAILS}. Permitted
 * for service-to-service calls (same trust model as {@code /users/summaries} / {@code POST /users}).
 */
@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyService companyService;

    /** Resolve a company by its public slug (id/name/slug) — used pre-login for branded login pages. */
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<CompanyInfo> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(companyService.findBySlug(slug));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<CompanyProfileDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getProfile(id));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<CompanyProfileDto> update(
            @PathVariable Long id, @RequestBody CompanyProfileDto dto) {
        return ResponseEntity.ok(companyService.updateProfile(id, dto));
    }
}
