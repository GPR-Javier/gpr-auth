package com.gpr.auth.entity;

import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Central LOGIN CREDENTIALS. Owned by gpr-auth ({@code gpr_identity} DB) — the shared sign-in
 * identity across every app. Holds ONLY what authenticates a person: the identifiers they can log
 * in with (email / username / phone) and the password. Personal details live in {@link UserInfo}
 * (1:1); app-specific data (employeeId, roles, profile overrides) lives per-app keyed by {@code id}.
 *
 * <p>Editing these fields changes how the user signs in to ALL apps — the UI warns accordingly.
 *
 * <p>Lifecycle columns (created/updated/deleted) come from {@link Auditable}. {@code deletedAt} drives
 * soft-delete + recovery on re-login.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Primary email — a login identifier. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Global handle — a login identifier and the IdP's stable human-readable id. */
    @Column(nullable = false, unique = true)
    private String username;

    /** Phone — an optional login identifier (recovery / future 2FA). */
    @Column(unique = true)
    private String phone;

    /** Always BCrypt hashed — never stored or returned as plain text. */
    @Column(nullable = false)
    private String password;

    /** Platform super admin — transcends tenants, manages companies/apps, exempt from app authz. */
    @Column(name = "is_super_admin", nullable = false)
    @Builder.Default
    private boolean superAdmin = false;
}
