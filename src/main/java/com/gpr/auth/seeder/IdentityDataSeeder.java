package com.gpr.auth.seeder;

import com.gpr.auth.entity.App;
import com.gpr.auth.entity.UserAppAccess;
import com.gpr.auth.enums.RegistrationMode;
import com.gpr.auth.repository.AppRepository;
import com.gpr.auth.repository.UserAppAccessRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.common.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the IdP app registry and backfills app access for existing users.
 *
 * <p>Runs AFTER {@link DataSeeder} (see {@code @Order}) so the admin user already exists when we
 * grant access. Idempotent — safe to run on every startup.
 *
 * <p>Interim phase: identity tables live in the shared DB alongside WorkOS data. WorkOS is the
 * first (and currently only) registered app. When the physical DB split happens, this seeder moves
 * to the standalone gpr-auth service against its own {@code gpr_identity} database.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class IdentityDataSeeder implements ApplicationRunner {

    private static final String WORKOS_CLIENT_ID = "workos";

    private final AppRepository appRepository;
    private final UserAppAccessRepository userAppAccessRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        App workos = seedWorkosApp();
        backfillUserAppAccess(workos);
    }

    private App seedWorkosApp() {
        App app = appRepository.findByClientId(WORKOS_CLIENT_ID).orElseGet(App::new);
        app.setClientId(WORKOS_CLIENT_ID);
        app.setName("WorkOS");
        // Interim: SELF_SIGNUP so login auto-provisions access for any authenticated user (applicants
        // self-register; employees are admin-created without an access row until they first log in).
        // The invite-only/employee-vs-applicant policy split is a later phase.
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
