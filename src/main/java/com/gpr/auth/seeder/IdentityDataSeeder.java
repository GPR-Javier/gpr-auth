package com.gpr.auth.seeder;

import com.gpr.auth.entity.App;
import com.gpr.auth.entity.Company;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.entity.UserCompany;
import com.gpr.auth.entity.UserInfo;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.CompanyRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
import com.gpr.auth.repository.UserCompanyRepository;
import com.gpr.auth.repository.UserInfoRepository;
import com.gpr.auth.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the {@code gpr_identity} database: the bootstrap admin identity, the app registry, and
 * app-access backfill. Idempotent — safe on every startup.
 *
 * <p>The admin is seeded FIRST on a fresh database so it lands on the reserved id (1). WorkOS's
 * {@code WorkOsDataSeeder} writes the matching employee profile + Super Admin assignment against the
 * SAME reserved id with no cross-service coordination — the convention that links the two DBs.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class IdentityDataSeeder implements ApplicationRunner {

    private static final String WORKOS_CLIENT_ID = "workos";
    private static final String ADMIN_USER_EMAIL = "admin@gpr.com";
    private static final String SUPER_ADMIN_EMAIL = "super@gpr.com";
    private static final String DEFAULT_COMPANY_SLUG = "gpr";

    private final AppRepository appRepository;
    private final UserAppAccessRepository userAppAccessRepository;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final CompanyRepository companyRepository;
    private final UserCompanyRepository userCompanyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = seedAdminIdentity();        // admin@company.com → id 1 on a fresh DB
        Company company = seedDefaultCompany();  // GPR tenant → id 1 on a fresh DB
        User superAdmin = seedSuperAdmin();      // platform super admin
        App workos = seedWorkosApp();
        backfillUserAppAccess(workos);
        addMembership(admin, company);           // admin is a Company Admin of GPR (role assigned in WorkOS)
        addMembership(superAdmin, company);
    }

    private User seedAdminIdentity() {
        // Idempotent across an email change too — match either identifier so we never collide on username.
        User existing = userRepository.findByEmail(ADMIN_USER_EMAIL)
                .or(() -> userRepository.findByUsername("admin"))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        User admin = User.builder()
                .email(ADMIN_USER_EMAIL)
                .username("admin")
                .password(passwordEncoder.encode("Password1!"))
                .active(true)
                .build();
        userRepository.save(admin);
        userInfoRepository.save(UserInfo.builder()
                .user(admin)
                .firstName("Admin")
                .lastName("System")
                .build());
        log.info("IdentitySeeder: created admin identity '{}' (id={})", ADMIN_USER_EMAIL, admin.getId());
        return admin;
    }

    private User seedSuperAdmin() {
        User existing = userRepository.findByEmail(SUPER_ADMIN_EMAIL)
                .or(() -> userRepository.findByUsername("superadmin"))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        User su = User.builder()
                .email(SUPER_ADMIN_EMAIL)
                .username("superadmin")
                .password(passwordEncoder.encode("Password1!"))
                .active(true)
                .superAdmin(true)
                .build();
        userRepository.save(su);
        userInfoRepository.save(UserInfo.builder()
                .user(su)
                .firstName("Super")
                .lastName("Admin")
                .build());
        log.info("IdentitySeeder: created platform super admin '{}' (id={})", SUPER_ADMIN_EMAIL, su.getId());
        return su;
    }

    private Company seedDefaultCompany() {
        Company company = companyRepository.findBySlug(DEFAULT_COMPANY_SLUG).orElseGet(Company::new);
        company.setName("GPR");
        company.setSlug(DEFAULT_COMPANY_SLUG);
        company.setActive(true);
        Company saved = companyRepository.save(company);
        log.info("IdentitySeeder: upserted default company '{}' (id={})", DEFAULT_COMPANY_SLUG, saved.getId());
        return saved;
    }

    private void addMembership(User user, Company company) {
        if (!userCompanyRepository.existsByUserIdAndCompanyId(user.getId(), company.getId())) {
            userCompanyRepository.save(UserCompany.builder()
                    .user(user)
                    .company(company)
                    .active(true)
                    .build());
        }
    }

    private App seedWorkosApp() {
        App app = appRepository.findByClientId(WORKOS_CLIENT_ID).orElseGet(App::new);
        app.setClientId(WORKOS_CLIENT_ID);
        app.setName("WorkOS");
        // Interim: SELF_SIGNUP so login auto-provisions access for any authenticated user.
        app.setRegistrationMode(RegistrationMode.SELF_SIGNUP);
        app.setActive(true);
        App saved = appRepository.save(app);
        log.info("IdentitySeeder: upserted app '{}'", WORKOS_CLIENT_ID);
        return saved;
    }

    private void backfillUserAppAccess(App app) {
        List<User> users = userRepository.findAll();
        int granted = 0;
        for (User user : users) {
            if (!userAppAccessRepository.existsByUserAndApp(user, app)) {
                userAppAccessRepository.save(UserAppAccess.builder()
                        .user(user)
                        .app(app)
                        .active(true)
                        .build());
                granted++;
            }
        }
        log.info("IdentitySeeder: granted '{}' access to {} new user(s) ({} total)",
                app.getClientId(), granted, users.size());
    }
}
