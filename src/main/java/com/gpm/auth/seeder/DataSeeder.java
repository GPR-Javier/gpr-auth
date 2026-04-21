package com.gpm.auth.seeder;

import com.gpm.auth.repository.AccessRoleRepository;
import com.gpm.auth.repository.UserRoleRepository;
import com.gpm.auth.repository.FunctionalityRepository;
import com.gpm.auth.repository.UserRepository;
import com.gpm.common.entity.AccessRole;
import com.gpm.common.entity.UserRole;
import com.gpm.common.entity.Functionality;
import com.gpm.common.entity.User;
import com.gpm.common.enums.FunctionalityCode;
import com.gpm.common.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final AccessRoleRepository accessRoleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** pageCode → {pageName, [FunctionalityCodes...]} */
    private static final Map<String, Object[]> ACCESS_ROLE_DEFINITIONS = Map.of(
        "DTR",      new Object[]{"DTR Management",     new FunctionalityCode[]{FunctionalityCode.DTR_VIEW,      FunctionalityCode.DTR_EDIT,      FunctionalityCode.DTR_DELETE,      FunctionalityCode.DTR_EXPORT}},
        "PAYROLL",  new Object[]{"Payroll Management",  new FunctionalityCode[]{FunctionalityCode.PAYROLL_VIEW,  FunctionalityCode.PAYROLL_EDIT,  FunctionalityCode.PAYROLL_DELETE,  FunctionalityCode.PAYROLL_EXPORT, FunctionalityCode.PAYROLL_RUN, FunctionalityCode.PAYROLL_CONFIGURE}},
        "LEAVE",    new Object[]{"Leave Management",    new FunctionalityCode[]{FunctionalityCode.LEAVE_VIEW,    FunctionalityCode.LEAVE_EDIT,    FunctionalityCode.LEAVE_DELETE,    FunctionalityCode.LEAVE_APPROVE,  FunctionalityCode.LEAVE_EXPORT}},
        "ANALYTICS",new Object[]{"Analytics",           new FunctionalityCode[]{FunctionalityCode.ANALYTICS_VIEW,FunctionalityCode.ANALYTICS_EXPORT}},
        "USER",     new Object[]{"User Management",     new FunctionalityCode[]{FunctionalityCode.USER_VIEW,     FunctionalityCode.USER_CREATE,   FunctionalityCode.USER_EDIT,       FunctionalityCode.USER_DELETE}}
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<AccessRole> allAccessRoles = seedAccessRoles();
        seedSuperHrRole(allAccessRoles);
        seedAdminUser();
    }

    // ── Step 1: Seed AccessRoles + Functionalities ────────────────────

    private List<AccessRole> seedAccessRoles() {
        List<AccessRole> result = new ArrayList<>();

        ACCESS_ROLE_DEFINITIONS.forEach((pageCode, def) -> {
            if (accessRoleRepository.existsByPageCode(pageCode)) {
                log.info("Seeder: AccessRole '{}' already exists, skipping", pageCode);
                accessRoleRepository.findByPageCode(pageCode).ifPresent(result::add);
                return;
            }

            String pageName = (String) def[0];
            FunctionalityCode[] codes = (FunctionalityCode[]) def[1];

            List<Functionality> functionalities = new ArrayList<>();
            for (FunctionalityCode code : codes) {
                Functionality f = seedFunctionality(code);
                functionalities.add(f);
            }

            AccessRole accessRole = AccessRole.builder()
                    .pageName(pageName)
                    .pageCode(pageCode)
                    .functionalities(functionalities)
                    .build();

            result.add(accessRoleRepository.save(accessRole));
            log.info("Seeder: created AccessRole '{}'", pageCode);
        });

        return result;
    }

    private Functionality seedFunctionality(FunctionalityCode code) {
        if (functionalityRepository.existsByCode(code)) {
            log.info("Seeder: Functionality '{}' already exists, skipping", code);
            return functionalityRepository.findByCode(code).orElseThrow();
        }

        // Derive human-readable name from enum name: DTR_VIEW → "View"
        String[] parts = code.name().split("_");
        String name = parts[parts.length - 1].charAt(0) + parts[parts.length - 1].substring(1).toLowerCase();

        Functionality f = Functionality.builder()
                .name(name)
                .code(code)
                .enabled(true)
                .build();

        Functionality saved = functionalityRepository.save(f);
        log.info("Seeder: created Functionality '{}'", code.getCode());
        return saved;
    }

    // ── Step 2: Seed "Super HR" UserRole ─────────────────────────

    private void seedSuperHrRole(List<AccessRole> allAccessRoles) {
        if (userRoleRepository.existsByName("Super HR")) {
            log.info("Seeder: UserRole 'Super HR' already exists, skipping");
            return;
        }

        UserRole superHr = UserRole.builder()
                .name("Super HR")
                .description("Full access to all modules")
                .accessRoles(new ArrayList<>(allAccessRoles))
                .active(true)
                .build();

        userRoleRepository.save(superHr);
        log.info("Seeder: created UserRole 'Super HR'");
    }

    // ── Step 3: Seed default ADMIN user ──────────────────────────────

    private void seedAdminUser() {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Seeder: ADMIN user already exists, skipping");
            return;
        }

        User admin = User.builder()
                .firstName("Admin")
                .lastName("System")
                .email("admin@company.com")
                .password(passwordEncoder.encode("Admin@1234"))
                .employeeId("ADMIN-001")
                .role(Role.ADMIN)
                .active(true)
                .build();

        userRepository.save(admin);
        log.info("Seeder: created ADMIN user 'admin@company.com'");
    }
}
