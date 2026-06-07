package com.gpr.auth.controller;

import com.gpr.auth.dto.CreateCompanyRequest;
import com.gpr.auth.entity.Company;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.service.CompanyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform (super-admin) company management. Standing up a company also provisions its first
 * Company Admin identity; WorkOS then seeds that company's role catalog (lazily on first session,
 * or via its provision endpoint).
 */
@RestController
@RequestMapping("/admin/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<CompanyService.CompanyAdminResult> create(@Valid @RequestBody CreateCompanyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.createCompany(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Company>> list() {
        return ResponseEntity.ok(companyRepository.findAll());
    }
}
