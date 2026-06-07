package com.gpr.auth.seeder;

import com.gpr.auth.entity.App;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
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
    private static final String ADMIN_USER_EMAIL = "admin@company.com";

    private final AppRepository appRepository;
    private final UserAppAccessRepository userAppAccessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdminIdentity();
        App workos = seedWorkosApp();
        backfillUserAppAccess(workos);
    }

    private void seedAdminIdentity() {
        if (userRepository.findByEmail(ADMIN_USER_EMAIL).isPresent()) {
            return;
        }
        User admin = User.builder()
                .firstName("Admin")
                .lastName("System")
                .email(ADMIN_USER_EMAIL)
                .password(passwordEncoder.encode("Admin@1234"))
                .employeeId("ADMIN-001")
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("IdentitySeeder: created admin identity '{}' (id={})", ADMIN_USER_EMAIL, admin.getId());
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
