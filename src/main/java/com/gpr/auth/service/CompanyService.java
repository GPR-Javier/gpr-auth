package com.gpr.auth.service;

import com.gpr.auth.dto.CompanyInfo;
import com.gpr.auth.dto.CreateCompanyRequest;
import com.gpr.auth.dto.IdentityCreateRequest;
import com.gpr.auth.entity.Company;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserCompany;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.repository.UserCompanyRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.common.dto.UserSummaryDto;
import com.gpr.common.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenancy authority: owns companies + membership. Super admins transcend membership (may act within
 * any company); everyone else is limited to the companies they belong to.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserCompanyRepository userCompanyRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    /** The created company + the new Company Admin identity's userId (for WorkOS provisioning). */
    public record CompanyAdminResult(Long companyId, Long adminUserId, String name, String slug) {}

    @Transactional
    public CompanyAdminResult createCompany(CreateCompanyRequest req) {
        if (companyRepository.existsBySlug(req.getSlug().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company slug already in use: " + req.getSlug());
        }
        Company company = companyRepository.save(Company.builder()
                .name(req.getName().trim())
                .slug(req.getSlug().trim())
                .active(true)
                .build());

        IdentityCreateRequest idReq = new IdentityCreateRequest();
        idReq.setFirstName(req.getAdminFirstName());
        idReq.setLastName(req.getAdminLastName());
        idReq.setPassword(req.getAdminPassword());
        idReq.setEmail(req.getAdminEmail());
        idReq.setUsername(req.getAdminUsername());
        UserSummaryDto admin = authService.createIdentity(idReq, "workos");

        User adminUser = userRepository.findById(admin.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin identity vanished: " + admin.getId()));
        addMembership(adminUser, company);

        return new CompanyAdminResult(company.getId(), admin.getId(), company.getName(), company.getSlug());
    }

    @Transactional(readOnly = true)
    public List<CompanyInfo> companiesForUser(Long userId, boolean superAdmin) {
        if (superAdmin) {
            return companyRepository.findAll().stream()
                    .filter(Company::isActive)
                    .map(this::toInfo)
                    .toList();
        }
        return userCompanyRepository.findByUserId(userId).stream()
                .filter(UserCompany::isActive)
                .map(UserCompany::getCompany)
                .filter(Company::isActive)
                .map(this::toInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Long userId, Long companyId, boolean superAdmin) {
        if (superAdmin) {
            return companyRepository.findById(companyId).filter(Company::isActive).isPresent();
        }
        return userCompanyRepository.existsByUserIdAndCompanyId(userId, companyId);
    }

    public void addMembership(User user, Company company) {
        if (!userCompanyRepository.existsByUserIdAndCompanyId(user.getId(), company.getId())) {
            userCompanyRepository.save(UserCompany.builder()
                    .user(user)
                    .company(company)
                    .active(true)
                    .build());
        }
    }

    private CompanyInfo toInfo(Company c) {
        return new CompanyInfo(c.getId(), c.getName(), c.getSlug());
    }
}
