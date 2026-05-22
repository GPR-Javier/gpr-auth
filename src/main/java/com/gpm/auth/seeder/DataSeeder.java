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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String SUPER_HR_ROLE_NAME = "Super HR";
    private static final String ADMIN_ACCESS_ROLE_NAME = "Admin Access";
    private static final Set<String> ADMIN_EXCLUDED_PAGE_CODES = Set.of(
            "DTR",
            "LEAVE",
            "OFFICIAL_BUSINESS",
            "CERTIFICATE_OF_EMPLOYMENT",
            "OVERTIME",
            "SCHEDULE_CHANGE_REQUEST"
    );

    private final AccessRoleRepository accessRoleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /** pageCode → {pageName, [FunctionalityCodes...]} */
    private static final Map<String, Object[]> ACCESS_ROLE_DEFINITIONS = Map.ofEntries(
            entry("DTR", "DTR", FunctionalityCode.DTR_CLOCK_IN, FunctionalityCode.DTR_CLOCK_OUT, FunctionalityCode.DTR_BREAK_START, FunctionalityCode.DTR_BREAK_END,
                    FunctionalityCode.DTR_VIEW_ATTENDANCE, FunctionalityCode.DTR_VIEW_ATTENDANCE_DETAIL, FunctionalityCode.DTR_FILE_APPEAL, FunctionalityCode.DTR_REQUEST_DTR_CORRECTION,
                    FunctionalityCode.DTR_REQUIRE_CAMERA_VALIDATION),
            entry("LEAVE", "Leave", FunctionalityCode.LEAVE_FILE_LEAVE, FunctionalityCode.LEAVE_VIEW_OWN_LEAVE, FunctionalityCode.LEAVE_VIEW_LEAVE_BALANCE, FunctionalityCode.LEAVE_CANCEL_LEAVE),
            entry("OFFICIAL_BUSINESS", "Official Business", FunctionalityCode.OFFICIAL_BUSINESS_FILE_OB, FunctionalityCode.OFFICIAL_BUSINESS_VIEW_OWN_OB),
            entry("CERTIFICATE_OF_EMPLOYMENT", "Certificate of Employment", FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_REQUEST_COE, FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_VIEW_OWN_COE),
            entry("OVERTIME", "Overtime", FunctionalityCode.OVERTIME_FILE_OVERTIME, FunctionalityCode.OVERTIME_VIEW_OWN_OVERTIME),
            entry("PAYROLL", "Payroll", FunctionalityCode.PAYROLL_VIEW_PAYSLIP, FunctionalityCode.PAYROLL_DOWNLOAD_PAYSLIP, FunctionalityCode.PAYROLL_EXPORT_PAYROLL_HISTORY, FunctionalityCode.PAYROLL_VIEW_YTD_SUMMARY),
            entry("LEAVE_MANAGEMENT", "Leave Management", FunctionalityCode.LEAVE_MANAGEMENT_VIEW_ALL_LEAVE_REQUESTS, FunctionalityCode.LEAVE_MANAGEMENT_APPROVE_LEAVE, FunctionalityCode.LEAVE_MANAGEMENT_REJECT_LEAVE),
            entry("EMPLOYEE_MANAGEMENT", "Employee Management", FunctionalityCode.EMPLOYEE_MANAGEMENT_VIEW_EMPLOYEES, FunctionalityCode.EMPLOYEE_MANAGEMENT_ADD_EMPLOYEE,
                    FunctionalityCode.EMPLOYEE_MANAGEMENT_VIEW_EMPLOYEE_PROFILE, FunctionalityCode.EMPLOYEE_MANAGEMENT_SEARCH_EMPLOYEES),
            entry("RECRUITMENT", "Recruitment", FunctionalityCode.RECRUITMENT_VIEW_JOB_POSTINGS, FunctionalityCode.RECRUITMENT_VIEW_APPLICANTS, FunctionalityCode.RECRUITMENT_VIEW_JOB_DETAIL),
            entry("ATTENDANCE_MANAGEMENT", "Attendance Management", FunctionalityCode.ATTENDANCE_MANAGEMENT_VIEW_ALL_ATTENDANCE,
                    FunctionalityCode.ATTENDANCE_MANAGEMENT_APPROVE_DTR_CORRECTION, FunctionalityCode.ATTENDANCE_MANAGEMENT_APPROVE_OT_REQUEST),
            entry("USER_MANAGEMENT", "User Management", FunctionalityCode.USER_MANAGEMENT_VIEW_USERS, FunctionalityCode.USER_MANAGEMENT_CREATE_USER,
                    FunctionalityCode.USER_MANAGEMENT_ASSIGN_ROLES, FunctionalityCode.USER_MANAGEMENT_DELETE_USER, FunctionalityCode.USER_MANAGEMENT_FILTER_USERS_BY_ROLE),
            entry("ROLES_AND_PERMISSIONS", "Roles and Permissions", FunctionalityCode.ROLES_AND_PERMISSIONS_VIEW_ROLES, FunctionalityCode.ROLES_AND_PERMISSIONS_CREATE_ROLE,
                    FunctionalityCode.ROLES_AND_PERMISSIONS_EDIT_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_DELETE_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_ASSIGN_ACCESS_ROLE,
                    FunctionalityCode.ROLES_AND_PERMISSIONS_REMOVE_ACCESS_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_TOGGLE_FUNCTIONALITY),
            entry("AUDIT_LOG", "Audit Log", FunctionalityCode.AUDIT_LOG_VIEW_AUDIT_LOGS, FunctionalityCode.AUDIT_LOG_SEARCH_AUDIT_LOGS, FunctionalityCode.AUDIT_LOG_EXPORT_AUDIT_LOGS),
            entry("CONFIGURATION", "Configuration", FunctionalityCode.CONFIGURATION_VIEW_CONFIG, FunctionalityCode.CONFIGURATION_EDIT_PAYROLL_SETTINGS,
                    FunctionalityCode.CONFIGURATION_EDIT_ATTENDANCE_SETTINGS, FunctionalityCode.CONFIGURATION_EDIT_LEAVE_SETTINGS),
            entry("TEACHER_MANAGEMENT", "Teacher Management",
                    FunctionalityCode.TEACHER_MANAGEMENT_VIEW_TEACHERS,
                    FunctionalityCode.TEACHER_MANAGEMENT_ADD_TEACHER,
                    FunctionalityCode.TEACHER_MANAGEMENT_EDIT_TEACHER,
                    FunctionalityCode.TEACHER_MANAGEMENT_DELETE_TEACHER),
            entry("SCHEDULE_MANAGEMENT", "Schedule Management",
                    FunctionalityCode.SCHEDULE_MANAGEMENT_VIEW_SCHEDULES,
                    FunctionalityCode.SCHEDULE_MANAGEMENT_UPLOAD_SCHEDULES,
                    FunctionalityCode.SCHEDULE_MANAGEMENT_MANAGE_SCHEDULES),
            entry("PAYROLL_MANAGEMENT", "Payroll Management",
                    FunctionalityCode.PAYROLL_MANAGEMENT_MANAGE_PAYROLL,
                    FunctionalityCode.PAYROLL_MANAGEMENT_CREATE_RUN,
                    FunctionalityCode.PAYROLL_MANAGEMENT_RELEASE_PAYROLL,
                    FunctionalityCode.PAYROLL_MANAGEMENT_VIEW_ALL_PAYSLIPS),
            entry("REWARDS", "Rewards & Ratings",
                    FunctionalityCode.REWARDS_VIEW_REWARDS,
                    FunctionalityCode.REWARDS_MANAGE_REWARDS,
                    FunctionalityCode.REWARDS_UPLOAD_RATINGS,
                    FunctionalityCode.REWARDS_MANAGE_RULES),
            entry("SCHEDULE_POLICY", "Schedule Policy",
                    FunctionalityCode.SCHEDULE_POLICY_VIEW,
                    FunctionalityCode.SCHEDULE_POLICY_EDIT_ORG,
                    FunctionalityCode.SCHEDULE_POLICY_EDIT_ROLE,
                    FunctionalityCode.SCHEDULE_POLICY_EDIT_USER,
                    FunctionalityCode.SCHEDULE_POLICY_VIEW_HISTORY),
            entry("SCHEDULE_CHANGE_REQUEST", "Schedule Change Requests",
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_FILE,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_VIEW_OWN,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_CANCEL_OWN),
            entry("SCHEDULE_CHANGE_APPROVAL", "Schedule Change Approval",
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_VIEW_ALL,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_APPROVE,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_REJECT)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyRoleAssignments();
        syncFunctionalityConstraint();
        List<AccessRole> allAccessRoles = seedAccessRoles();
        seedSuperHrRole(allAccessRoles);
        UserRole adminAccessRole = seedAdminAccessRole(allAccessRoles);
        seedAdminUser(adminAccessRole);
    }

    /**
     * One-time migration: copies rows from the old user_role_assignments join table
     * (used by the previous @ManyToMany mapping) into the new employee_role_assignments table.
     * Safe to run on every startup — skips rows that are already present.
     * Checks for table existence via information_schema first to avoid aborting the transaction.
     */
    private void migrateLegacyRoleAssignments() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_role_assignments')",
                Boolean.class
        );
        if (!Boolean.TRUE.equals(exists)) {
            log.debug("Seeder: legacy user_role_assignments table not found, skipping migration");
            return;
        }
        int migrated = jdbcTemplate.update(
                "INSERT INTO employee_role_assignments (user_id, user_role_id, created_at, updated_at) " +
                "SELECT ura.user_id, ura.user_role_id, NOW(), NOW() " +
                "FROM user_role_assignments ura " +
                "WHERE NOT EXISTS (" +
                "  SELECT 1 FROM employee_role_assignments era " +
                "  WHERE era.user_id = ura.user_id AND era.user_role_id = ura.user_role_id" +
                ")"
        );
        if (migrated > 0) {
            log.info("Seeder: migrated {} legacy role assignments from user_role_assignments → employee_role_assignments", migrated);
        }
    }

    private void syncFunctionalityConstraint() {
        String allowedValues = Arrays.stream(FunctionalityCode.values())
                .map(FunctionalityCode::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE functionalities DROP CONSTRAINT IF EXISTS functionalities_code_check");
        int removed = jdbcTemplate.update("DELETE FROM functionalities WHERE code NOT IN (" + allowedValues + ")");
        jdbcTemplate.execute("ALTER TABLE functionalities ADD CONSTRAINT functionalities_code_check CHECK (code IN (" + allowedValues + "))");
        log.info("Seeder: synchronized functionalities_code_check with {} enum values (removed {} legacy rows)", FunctionalityCode.values().length, removed);
    }

    // ── Step 1: Seed AccessRoles + Functionalities ────────────────────

    private List<AccessRole> seedAccessRoles() {
        List<AccessRole> result = new ArrayList<>();

        ACCESS_ROLE_DEFINITIONS.forEach((pageCode, def) -> {
            String pageName = (String) def[0];
            FunctionalityCode[] codes = (FunctionalityCode[]) def[1];

            List<Functionality> functionalities = new ArrayList<>();
            for (FunctionalityCode code : codes) {
                Functionality f = seedFunctionality(code);
                functionalities.add(f);
            }

            AccessRole accessRole = accessRoleRepository.findByPageCode(pageCode)
                    .orElseGet(AccessRole::new);
            accessRole.setPageName(pageName);
            accessRole.setPageCode(pageCode);
            accessRole.getFunctionalities().clear();
            accessRole.getFunctionalities().addAll(functionalities);

            AccessRole saved = accessRoleRepository.save(accessRole);
            result.add(saved);
            log.info("Seeder: upserted AccessRole '{}' with {} functionalities", pageCode, functionalities.size());
        });

        return result;
    }

    private Functionality seedFunctionality(FunctionalityCode code) {
        String name = humanizeAction(code);

        boolean[] isNew = { false };
        Functionality functionality = functionalityRepository.findByCode(code)
                .orElseGet(() -> {
                    isNew[0] = true;
                    Functionality f = new Functionality();
                    f.setEnabled(true); // new functionalities default to enabled
                    return f;
                });
        functionality.setCode(code);
        functionality.setName(name);
        // Preserve admin toggles on existing rows — only set enabled for brand-new ones above.

        Functionality saved = functionalityRepository.save(functionality);
        log.info("Seeder: {} Functionality '{}'", isNew[0] ? "created" : "updated", code.getCode());
        return saved;
    }

    private String humanizeAction(FunctionalityCode code) {
        String[] sections = code.getCode().split(":", 2);
        String action = sections.length == 2 ? sections[1] : code.name();
        return Arrays.stream(action.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1) + part.substring(1).toLowerCase())
                .reduce((left, right) -> left + " " + right)
                .orElse(action);
    }

    private static Map.Entry<String, Object[]> entry(String pageCode, String pageName, FunctionalityCode... functionalities) {
        return new AbstractMap.SimpleEntry<>(pageCode, new Object[]{pageName, functionalities});
    }

    // ── Step 2: Seed "Super HR" UserRole ─────────────────────────

    private void seedSuperHrRole(List<AccessRole> allAccessRoles) {
        UserRole superHr = userRoleRepository.findByName(SUPER_HR_ROLE_NAME).orElse(null);
        if (superHr != null) {
            allAccessRoles.forEach(superHr::addAccessRole);
            userRoleRepository.save(superHr);
            log.info("Seeder: updated UserRole '{}' with all access roles", SUPER_HR_ROLE_NAME);
            return;
        }

        superHr = UserRole.builder()
                .name(SUPER_HR_ROLE_NAME)
                .description("Full access to all modules")
                .accessRoles(new ArrayList<>(allAccessRoles))
                .active(true)
                .build();

        userRoleRepository.save(superHr);
        log.info("Seeder: created UserRole '{}'", SUPER_HR_ROLE_NAME);
    }

    private UserRole seedAdminAccessRole(List<AccessRole> allAccessRoles) {
        List<AccessRole> allowedAccessRoles = allAccessRoles.stream()
                .filter(role -> !ADMIN_EXCLUDED_PAGE_CODES.contains(role.getPageCode()))
                .toList();

        UserRole adminAccess = userRoleRepository.findByName(ADMIN_ACCESS_ROLE_NAME).orElse(null);
        if (adminAccess == null) {
            adminAccess = UserRole.builder()
                    .name(ADMIN_ACCESS_ROLE_NAME)
                    .description("Admin access excluding employee self-service modules")
                    .active(true)
                    .build();
        }

        adminAccess.setActive(true);
        adminAccess.getAccessRoles().clear();
        allowedAccessRoles.forEach(adminAccess::addAccessRole);

        UserRole saved = userRoleRepository.save(adminAccess);
        log.info("Seeder: upserted UserRole '{}' with {} access roles", ADMIN_ACCESS_ROLE_NAME, allowedAccessRoles.size());
        return saved;
    }

    // ── Step 3: Seed default ADMIN user ──────────────────────────────

    private void seedAdminUser(UserRole adminAccessRole) {
        List<User> admins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .toList();

        if (admins.isEmpty()) {
            User admin = User.builder()
                    .firstName("Admin")
                    .lastName("System")
                    .email("admin@company.com")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .employeeId("ADMIN-001")
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            admin.addRoleAssignment(adminAccessRole);

            userRepository.save(admin);
            log.info("Seeder: created ADMIN user 'admin@company.com' with '{}' role", ADMIN_ACCESS_ROLE_NAME);
            return;
        }

        int updated = 0;
        for (User admin : admins) {
            Set<Long> currentRoleIds = admin.getUserRoles().stream()
                    .map(UserRole::getId)
                    .collect(Collectors.toCollection(HashSet::new));
            if (currentRoleIds.contains(adminAccessRole.getId())) {
                continue;
            }

            admin.addRoleAssignment(adminAccessRole);
            userRepository.save(admin);
            updated++;
        }

        log.info("Seeder: ensured '{}' role for {} ADMIN user(s)", ADMIN_ACCESS_ROLE_NAME, updated);
    }
}
