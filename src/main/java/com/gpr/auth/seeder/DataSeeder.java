package com.gpr.auth.seeder;

import com.gpr.auth.repository.AccessRoleRepository;
import com.gpr.auth.repository.FunctionalityRepository;
import com.gpr.auth.repository.UserRepository;
import com.gpr.auth.repository.UserRoleRepository;
import com.gpr.common.entity.AccessRole;
import com.gpr.common.entity.Functionality;
import com.gpr.common.entity.User;
import com.gpr.common.entity.UserRole;
import com.gpr.common.enums.ControlType;
import com.gpr.common.enums.FunctionalityCode;
import com.gpr.common.enums.Role;
import com.gpr.common.enums.RoleType;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE_NAME = "Super Admin";
    private static final String APPLICANT_ROLE_NAME  = "Applicant";
    private static final String ADMIN_USER_EMAIL = "admin@company.com";

    /** Page codes whose functionalities are employee self-service (controlType = EMPLOYEE). */
    private static final Set<String> EMPLOYEE_PAGE_CODES = Set.of(
            "DTR",
            "LEAVE",
            "OFFICIAL_BUSINESS",
            "CERTIFICATE_OF_EMPLOYMENT",
            "OVERTIME",
            "PAYROLL",
            "SCHEDULE_CHANGE_REQUEST",
            "CHANGE_TIME_REQUEST",
            "SALARY_DISPUTE"
    );

    /** Page codes whose functionalities are applicant-facing (controlType = APPLICANT). All non-employee, non-applicant pages are ADMIN. */
    private static final Set<String> APPLICANT_PAGE_CODES = Set.of(
            "CAREERS",
            "MY_APPLICATIONS",
            "APPLICANT_INTERVIEWS",
            "APPLICANT_OFFERS",
            "MY_ASSESSMENT"
    );

    private final AccessRoleRepository accessRoleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    /** Override display names for specific functionality codes to match UI tab/action names. */
    private static final Map<FunctionalityCode, String> CUSTOM_FUNCTIONALITY_NAMES = Map.of(
            // Configuration tab names
            FunctionalityCode.SCHEDULE_POLICY_VIEW,                    "Schedule Policy",
            FunctionalityCode.CONFIGURATION_EDIT_ATTENDANCE_SETTINGS,  "Attendance",
            FunctionalityCode.CONFIGURATION_EDIT_PAYROLL_SETTINGS,     "Payroll",
            FunctionalityCode.CONFIGURATION_EDIT_LEAVE_SETTINGS,       "Leave",
            // Employee request action names — humanizer produces "File", "Request coe", "File ob" etc.
            FunctionalityCode.CHANGE_TIME_REQUEST_FILE,                 "File Request",
            FunctionalityCode.SCHEDULE_CHANGE_REQUEST_FILE,             "File Request",
            FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_REQUEST_COE,    "Request COE",
            FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_VIEW_OWN_COE,   "View Own COE",
            FunctionalityCode.OFFICIAL_BUSINESS_FILE_OB,                "File OB",
            FunctionalityCode.OFFICIAL_BUSINESS_VIEW_OWN_OB,            "View Own OB"
    );

    /** pageCode → {pageName, navGroup, FunctionalityCodes[]} */
    private static final Map<String, Object[]> ACCESS_ROLE_DEFINITIONS = Map.ofEntries(
            // ── Employee self-service ──────────────────────────────────────────────────
            entry("DTR", "My Attendance", "Self Service",
                    FunctionalityCode.DTR_CLOCK_IN, FunctionalityCode.DTR_CLOCK_OUT, FunctionalityCode.DTR_BREAK_START, FunctionalityCode.DTR_BREAK_END,
                    FunctionalityCode.DTR_VIEW_ATTENDANCE, FunctionalityCode.DTR_VIEW_ATTENDANCE_DETAIL, FunctionalityCode.DTR_FILE_APPEAL,
                    FunctionalityCode.DTR_REQUEST_DTR_CORRECTION, FunctionalityCode.DTR_REQUIRE_CAMERA_VALIDATION),
            entry("PAYROLL", "My Payslip", "Self Service",
                    FunctionalityCode.PAYROLL_VIEW_PAYSLIP, FunctionalityCode.PAYROLL_DOWNLOAD_PAYSLIP, FunctionalityCode.PAYROLL_EXPORT_PAYROLL_HISTORY, FunctionalityCode.PAYROLL_VIEW_YTD_SUMMARY),
            // ── Employee requests (Request) ────────────────────────────────────────────
            entry("LEAVE", "File Leave", "Request",
                    FunctionalityCode.LEAVE_FILE_LEAVE, FunctionalityCode.LEAVE_VIEW_OWN_LEAVE, FunctionalityCode.LEAVE_VIEW_LEAVE_BALANCE),
            entry("OVERTIME", "Overtime", "Request",
                    FunctionalityCode.OVERTIME_FILE_OVERTIME, FunctionalityCode.OVERTIME_VIEW_OWN_OVERTIME),
            entry("CERTIFICATE_OF_EMPLOYMENT", "COE", "Request",
                    FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_REQUEST_COE, FunctionalityCode.CERTIFICATE_OF_EMPLOYMENT_VIEW_OWN_COE),
            entry("OFFICIAL_BUSINESS", "Official Business", "Request",
                    FunctionalityCode.OFFICIAL_BUSINESS_FILE_OB, FunctionalityCode.OFFICIAL_BUSINESS_VIEW_OWN_OB),
            entry("CHANGE_TIME_REQUEST", "Change Time In/Time Out", "Request",
                    FunctionalityCode.CHANGE_TIME_REQUEST_FILE, FunctionalityCode.CHANGE_TIME_REQUEST_VIEW_OWN),
            entry("SCHEDULE_CHANGE_REQUEST", "Change Schedule", "Request",
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_FILE, FunctionalityCode.SCHEDULE_CHANGE_REQUEST_VIEW_OWN),
            entry("SALARY_DISPUTE", "Salary Dispute", "Request",
                    FunctionalityCode.SALARY_DISPUTE_FILE, FunctionalityCode.SALARY_DISPUTE_VIEW_OWN),
            // ── Team (admin) ───────────────────────────────────────────────────────────
            entry("ATTENDANCE_MANAGEMENT", "Attendance", "Team",
                    FunctionalityCode.ATTENDANCE_MANAGEMENT_VIEW_ALL_ATTENDANCE),
            entry("SCHEDULE_MANAGEMENT", "Schedule", "Team",
                    FunctionalityCode.SCHEDULE_MANAGEMENT_VIEW_SCHEDULES,
                    FunctionalityCode.SCHEDULE_MANAGEMENT_UPLOAD_SCHEDULES,
                    FunctionalityCode.SCHEDULE_MANAGEMENT_MANAGE_SCHEDULES),
            entry("LEAVE_MANAGEMENT", "Leave Management", "Requests",
                    FunctionalityCode.LEAVE_MANAGEMENT_VIEW_ALL_LEAVE_REQUESTS, FunctionalityCode.LEAVE_MANAGEMENT_APPROVE_LEAVE, FunctionalityCode.LEAVE_MANAGEMENT_REJECT_LEAVE),
            entry("EMPLOYEE_MANAGEMENT", "Employees", "Team",
                    FunctionalityCode.EMPLOYEE_MANAGEMENT_VIEW_EMPLOYEES, FunctionalityCode.EMPLOYEE_MANAGEMENT_ADD_EMPLOYEE,
                    FunctionalityCode.EMPLOYEE_MANAGEMENT_VIEW_EMPLOYEE_PROFILE, FunctionalityCode.EMPLOYEE_MANAGEMENT_SEARCH_EMPLOYEES),
            entry("SCHEDULE_CHANGE_APPROVAL", "Schedule Changes", "Requests",
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_VIEW_ALL,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_APPROVE,
                    FunctionalityCode.SCHEDULE_CHANGE_REQUEST_REJECT),
            entry("COE_MANAGEMENT", "COE Management", "Requests",
                    FunctionalityCode.COE_MANAGEMENT_VIEW_ALL),
            entry("OR_MANAGEMENT", "OR", "Deprecated",
                    FunctionalityCode.OR_MANAGEMENT_VIEW_ALL,
                    FunctionalityCode.OR_MANAGEMENT_APPROVE,
                    FunctionalityCode.OR_MANAGEMENT_REJECT),
            entry("CHANGE_TIME_MANAGEMENT", "Change Time Management", "Requests",
                    FunctionalityCode.CHANGE_TIME_MANAGEMENT_VIEW_ALL),
            entry("OVERTIME_MANAGEMENT", "Overtime Management", "Requests",
                    FunctionalityCode.OVERTIME_MANAGEMENT_VIEW_ALL),
            // ── Finance (admin) ────────────────────────────────────────────────────────
            entry("PAYROLL_MANAGEMENT", "Payroll", "Finance",
                    FunctionalityCode.PAYROLL_MANAGEMENT_MANAGE_PAYROLL,
                    FunctionalityCode.PAYROLL_MANAGEMENT_VIEW_ALL_PAYSLIPS),
            entry("REWARDS", "Rewards & Ratings", "Finance",
                    FunctionalityCode.REWARDS_VIEW_REWARDS,
                    FunctionalityCode.REWARDS_MANAGE_REWARDS,
                    FunctionalityCode.REWARDS_UPLOAD_RATINGS,
                    FunctionalityCode.REWARDS_MANAGE_RULES),
            entry("SALARY_DISPUTE_MANAGEMENT", "Salary Disputes", "Requests",
                    FunctionalityCode.SALARY_DISPUTE_MANAGEMENT_VIEW_ALL),
            // ── Business (admin) ───────────────────────────────────────────────────────
            entry("OFFICIAL_RECEIPTS", "Official Receipts", "Business",
                    FunctionalityCode.OFFICIAL_RECEIPTS_VIEW_ALL),
            entry("BUSINESS_TRIP", "Business Trip", "Finance",
                    FunctionalityCode.BUSINESS_TRIP_VIEW_ALL),
            entry("EXPENSE_REPORTS", "Expense Reports", "Finance",
                    FunctionalityCode.EXPENSE_REPORTS_VIEW_ALL),
            entry("CONTRACTS", "Contracts", "Business",
                    FunctionalityCode.CONTRACTS_VIEW_ALL),
            // ── System (admin) ─────────────────────────────────────────────────────────
            entry("USER_MANAGEMENT", "User Management", "System",
                    FunctionalityCode.USER_MANAGEMENT_VIEW_USERS, FunctionalityCode.USER_MANAGEMENT_CREATE_USER,
                    FunctionalityCode.USER_MANAGEMENT_ASSIGN_ROLES, FunctionalityCode.USER_MANAGEMENT_DELETE_USER, FunctionalityCode.USER_MANAGEMENT_FILTER_USERS_BY_ROLE),
            entry("ROLES_AND_PERMISSIONS", "Roles & Permissions", "System",
                    FunctionalityCode.ROLES_AND_PERMISSIONS_VIEW_ROLES, FunctionalityCode.ROLES_AND_PERMISSIONS_CREATE_ROLE,
                    FunctionalityCode.ROLES_AND_PERMISSIONS_EDIT_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_DELETE_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_ASSIGN_ACCESS_ROLE,
                    FunctionalityCode.ROLES_AND_PERMISSIONS_REMOVE_ACCESS_ROLE, FunctionalityCode.ROLES_AND_PERMISSIONS_TOGGLE_FUNCTIONALITY),
            entry("AUDIT_LOG", "Audit Log", "System",
                    FunctionalityCode.AUDIT_LOG_VIEW_AUDIT_LOGS, FunctionalityCode.AUDIT_LOG_SEARCH_AUDIT_LOGS, FunctionalityCode.AUDIT_LOG_EXPORT_AUDIT_LOGS),
            entry("CONFIGURATION", "Configuration", "System",
                    FunctionalityCode.SCHEDULE_POLICY_VIEW,
                    FunctionalityCode.CONFIGURATION_EDIT_ATTENDANCE_SETTINGS,
                    FunctionalityCode.CONFIGURATION_EDIT_PAYROLL_SETTINGS,
                    FunctionalityCode.CONFIGURATION_EDIT_LEAVE_SETTINGS),
            entry("SCHEDULE_POLICY", "Schedule Policy", "Deprecated",
                    FunctionalityCode.SCHEDULE_POLICY_VIEW),
            entry("ATTENDANCE_CONFIG", "Attendance", "Deprecated",
                    FunctionalityCode.CONFIGURATION_EDIT_ATTENDANCE_SETTINGS),
            entry("PAYROLL_CONFIG", "Payroll", "Deprecated",
                    FunctionalityCode.CONFIGURATION_EDIT_PAYROLL_SETTINGS),
            entry("LEAVE_CONFIG", "Leave", "Deprecated",
                    FunctionalityCode.CONFIGURATION_EDIT_LEAVE_SETTINGS),
            // ── Recruitment (admin) ────────────────────────────────────────────────────
            entry("RECRUITMENT", "Recruitment", "Recruitment",
                    FunctionalityCode.RECRUITMENT_VIEW_JOB_POSTINGS, FunctionalityCode.RECRUITMENT_VIEW_APPLICANTS, FunctionalityCode.RECRUITMENT_VIEW_JOB_DETAIL),
            // ── Applicant portal (applicant) ─────────────────────────────────────────────
            entry("CAREERS", "Careers", "Careers",
                    FunctionalityCode.CAREERS_VIEW_JOBS, FunctionalityCode.CAREERS_VIEW_JOB_DETAIL,
                    FunctionalityCode.CAREERS_SEARCH_JOBS, FunctionalityCode.CAREERS_APPLY_JOB),
            entry("MY_APPLICATIONS", "My Applications", "Careers",
                    FunctionalityCode.MY_APPLICATIONS_VIEW_OWN_APPLICATIONS, FunctionalityCode.MY_APPLICATIONS_VIEW_APPLICATION_STATUS,
                    FunctionalityCode.MY_APPLICATIONS_WITHDRAW_APPLICATION),
            entry("APPLICANT_INTERVIEWS", "Interviews", "Careers",
                    FunctionalityCode.APPLICANT_INTERVIEWS_VIEW_INTERVIEWS, FunctionalityCode.APPLICANT_INTERVIEWS_CONFIRM_INTERVIEW),
            entry("APPLICANT_OFFERS", "Offers", "Careers",
                    FunctionalityCode.APPLICANT_OFFERS_VIEW_OFFERS, FunctionalityCode.APPLICANT_OFFERS_RESPOND_OFFER),
            // ── Assessment / AI interview (admin builder) ───────────────────────────────
            entry("INTERVIEW_MANAGEMENT", "Assessments", "Recruitment",
                    FunctionalityCode.INTERVIEW_MANAGEMENT_VIEW,
                    FunctionalityCode.INTERVIEW_MANAGEMENT_MANAGE_QUESTIONS,
                    FunctionalityCode.INTERVIEW_MANAGEMENT_MANAGE_QUESTION_SETS,
                    FunctionalityCode.INTERVIEW_MANAGEMENT_MANAGE_TEMPLATES,
                    FunctionalityCode.INTERVIEW_MANAGEMENT_ASSIGN_ASSESSMENT),
            // ── Assessment (applicant) ──────────────────────────────────────────────────
            entry("MY_ASSESSMENT", "Assessment", "Careers",
                    FunctionalityCode.MY_ASSESSMENT_TAKE_ASSESSMENT, FunctionalityCode.MY_ASSESSMENT_VIEW_RESULTS)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyRoleAssignments();
        migrateRoleTypeColumn();
        syncRoleTypeConstraint();
        syncUserRoleConstraint();
        syncControlTypeConstraint();
        syncFunctionalityConstraint();
        List<AccessRole> allAccessRoles = seedAccessRoles();
        UserRole superAdminRole = seedSuperAdminRole(allAccessRoles);
        seedAdminUser(superAdminRole);
        seedApplicantRole(allAccessRoles);
    }

    /**
     * One-time migration: backfills the new role_type column from the legacy is_admin boolean.
     * Runs on every startup; skips rows that already have role_type set.
     * Defensive: checks if is_admin column exists before using it (fresh DBs won't have it).
     */
    private void migrateRoleTypeColumn() {
        Boolean isAdminExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_roles' AND column_name = 'is_admin')",
                Boolean.class
        );
        if (Boolean.TRUE.equals(isAdminExists)) {
            int adminMigrated = jdbcTemplate.update(
                    "UPDATE user_roles SET role_type = 'ADMIN' WHERE is_admin = true AND role_type IS NULL"
            );
            if (adminMigrated > 0) {
                log.info("Seeder: backfilled {} ADMIN rows from is_admin column", adminMigrated);
            }
        }
        int employeeMigrated = jdbcTemplate.update(
                "UPDATE user_roles SET role_type = 'EMPLOYEE' WHERE role_type IS NULL"
        );
        if (employeeMigrated > 0) {
            log.info("Seeder: backfilled {} EMPLOYEE rows (NULL role_type)", employeeMigrated);
        }
        // Fix any stale role_type values that don't match the current RoleType enum.
        // These would cause IllegalArgumentException when Hibernate loads the entity.
        int invalidFixed = jdbcTemplate.update(
                "UPDATE user_roles SET role_type = 'EMPLOYEE' WHERE role_type NOT IN ('ADMIN', 'EMPLOYEE', 'APPLICANT')"
        );
        if (invalidFixed > 0) {
            log.warn("Seeder: fixed {} user_roles rows with invalid role_type values (reset to EMPLOYEE)", invalidFixed);
        }
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

    /**
     * Rebuilds the user_roles.role_type CHECK constraint from the current RoleType enum.
     * Hibernate generates this constraint once and never updates it on ddl-auto=update, so adding
     * a new enum value (e.g. APPLICANT) would otherwise be rejected at insert time.
     */
    private void syncRoleTypeConstraint() {
        String allowedValues = Arrays.stream(RoleType.values())
                .map(RoleType::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_role_type_check");
        jdbcTemplate.execute("ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_type_check CHECK (role_type IN (" + allowedValues + "))");
        log.info("Seeder: synchronized user_roles_role_type_check with {} enum values", RoleType.values().length);
    }

    /**
     * Rebuilds the users.role CHECK constraint from the current Role enum.
     * Hibernate generates this constraint once and never updates it on ddl-auto=update, so adding
     * a new enum value (e.g. APPLICANT) would otherwise be rejected at insert time during registration.
     */
    private void syncUserRoleConstraint() {
        String allowedValues = Arrays.stream(Role.values())
                .map(Role::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN (" + allowedValues + "))");
        log.info("Seeder: synchronized users_role_check with {} enum values", Role.values().length);
    }

    /**
     * Rebuilds the functionalities.control_type CHECK constraint from the current ControlType enum.
     * Hibernate generates this constraint once and never updates it on ddl-auto=update, so adding
     * a new enum value (e.g. APPLICANT) would otherwise be rejected when seeding applicant functionalities.
     */
    private void syncControlTypeConstraint() {
        String allowedValues = Arrays.stream(ControlType.values())
                .map(ControlType::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE functionalities DROP CONSTRAINT IF EXISTS functionalities_control_type_check");
        jdbcTemplate.execute("ALTER TABLE functionalities ADD CONSTRAINT functionalities_control_type_check CHECK (control_type IN (" + allowedValues + "))");
        log.info("Seeder: synchronized functionalities_control_type_check with {} enum values", ControlType.values().length);
    }

    // ── Step 1: Seed AccessRoles + Functionalities ────────────────────

    private List<AccessRole> seedAccessRoles() {
        List<AccessRole> result = new ArrayList<>();

        ACCESS_ROLE_DEFINITIONS.forEach((pageCode, def) -> {
            String pageName = (String) def[0];
            String navGroup = (String) def[1];
            FunctionalityCode[] codes = (FunctionalityCode[]) def[2];
            ControlType controlType = EMPLOYEE_PAGE_CODES.contains(pageCode)
                    ? ControlType.EMPLOYEE
                    : APPLICANT_PAGE_CODES.contains(pageCode)
                        ? ControlType.APPLICANT
                        : ControlType.ADMIN;

            List<Functionality> functionalities = new ArrayList<>();
            for (FunctionalityCode code : codes) {
                Functionality f = seedFunctionality(code, controlType);
                functionalities.add(f);
            }

            AccessRole accessRole = accessRoleRepository.findByPageCode(pageCode)
                    .orElseGet(AccessRole::new);
            accessRole.setPageName(pageName);
            accessRole.setPageCode(pageCode);
            accessRole.setNavGroup(navGroup);
            accessRole.getFunctionalities().clear();
            accessRole.getFunctionalities().addAll(functionalities);

            AccessRole saved = accessRoleRepository.save(accessRole);
            result.add(saved);
            log.info("Seeder: upserted AccessRole '{}' ({}) with {} functionalities", pageCode, controlType, functionalities.size());
        });

        return result;
    }

    private Functionality seedFunctionality(FunctionalityCode code, ControlType controlType) {
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
        functionality.setControlType(controlType);
        // Preserve admin toggles on existing rows — only set enabled for brand-new ones above.

        Functionality saved = functionalityRepository.save(functionality);
        log.info("Seeder: {} Functionality '{}' ({})", isNew[0] ? "created" : "updated", code.getCode(), controlType);
        return saved;
    }

    private String humanizeAction(FunctionalityCode code) {
        if (CUSTOM_FUNCTIONALITY_NAMES.containsKey(code)) return CUSTOM_FUNCTIONALITY_NAMES.get(code);
        String[] sections = code.getCode().split(":", 2);
        String action = sections.length == 2 ? sections[1] : code.name();
        return Arrays.stream(action.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1) + part.substring(1).toLowerCase())
                .reduce((left, right) -> left + " " + right)
                .orElse(action);
    }

    private static Map.Entry<String, Object[]> entry(String pageCode, String pageName, String navGroup, FunctionalityCode... functionalities) {
        return new AbstractMap.SimpleEntry<>(pageCode, new Object[]{pageName, navGroup, functionalities});
    }

    // ── Step 2: Seed "Super Admin" UserRole ──────────────────────────

    private UserRole seedSuperAdminRole(List<AccessRole> allAccessRoles) {
        // Super Admin gets only admin-control access roles
        // (excludes employee self-service pages and applicant portal pages)
        List<AccessRole> adminAccessRoles = allAccessRoles.stream()
                .filter(ar -> !EMPLOYEE_PAGE_CODES.contains(ar.getPageCode()))
                .filter(ar -> !APPLICANT_PAGE_CODES.contains(ar.getPageCode()))
                .toList();

        UserRole superAdmin = userRoleRepository.findByName(SUPER_ADMIN_ROLE_NAME).orElse(null);
        if (superAdmin == null) {
            superAdmin = UserRole.builder()
                    .name(SUPER_ADMIN_ROLE_NAME)
                    .description("Full access to all admin control modules")
                    .roleType(RoleType.ADMIN)
                    .active(true)
                    .build();
        }
        superAdmin.setRoleType(RoleType.ADMIN);
        superAdmin.setActive(true);
        superAdmin.getAccessRoles().clear();
        adminAccessRoles.forEach(superAdmin::addAccessRole);

        UserRole saved = userRoleRepository.save(superAdmin);
        log.info("Seeder: upserted UserRole '{}' with {} access roles", SUPER_ADMIN_ROLE_NAME, adminAccessRoles.size());
        return saved;
    }

    // ── Step 3b: Seed "Applicant" UserRole ───────────────────────────

    private void seedApplicantRole(List<AccessRole> allAccessRoles) {
        // Applicant gets only applicant-control access roles (the careers/candidate portal pages)
        List<AccessRole> applicantAccessRoles = allAccessRoles.stream()
                .filter(ar -> APPLICANT_PAGE_CODES.contains(ar.getPageCode()))
                .toList();

        UserRole applicant = userRoleRepository.findByName(APPLICANT_ROLE_NAME).orElse(null);
        if (applicant == null) {
            applicant = UserRole.builder()
                    .name(APPLICANT_ROLE_NAME)
                    .description("Job applicant — can apply to open positions and track application status")
                    .roleType(RoleType.APPLICANT)
                    .active(true)
                    .build();
        }
        applicant.setRoleType(RoleType.APPLICANT);
        applicant.setActive(true);
        applicant.getAccessRoles().clear();
        applicantAccessRoles.forEach(applicant::addAccessRole);

        userRoleRepository.save(applicant);
        log.info("Seeder: upserted '{}' UserRole with {} access roles", APPLICANT_ROLE_NAME, applicantAccessRoles.size());
    }

    // ── Step 3: Seed default admin user ──────────────────────────────

    private void seedAdminUser(UserRole superAdminRole) {
        User admin = userRepository.findByEmail(ADMIN_USER_EMAIL).orElse(null);

        if (admin == null) {
            admin = User.builder()
                    .firstName("Admin")
                    .lastName("System")
                    .email(ADMIN_USER_EMAIL)
                    .password(passwordEncoder.encode("Admin@1234"))
                    .employeeId("ADMIN-001")
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            admin.addRoleAssignment(superAdminRole);
            userRepository.save(admin);
            log.info("Seeder: created admin user '{}' with '{}' role", ADMIN_USER_EMAIL, SUPER_ADMIN_ROLE_NAME);
            return;
        }

        // Ensure the existing admin user has the Super Admin role assigned
        boolean hasSuperAdmin = admin.getUserRoles().stream()
                .anyMatch(r -> r.getId() != null && r.getId().equals(superAdminRole.getId()));
        if (!hasSuperAdmin) {
            admin.addRoleAssignment(superAdminRole);
            userRepository.save(admin);
            log.info("Seeder: assigned '{}' role to existing admin user '{}'", SUPER_ADMIN_ROLE_NAME, ADMIN_USER_EMAIL);
        }
    }
}
