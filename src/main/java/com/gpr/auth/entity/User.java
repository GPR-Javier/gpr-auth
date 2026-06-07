package com.gpr.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Central LOGIN CREDENTIALS. Owned by gpr-auth ({@code gpr_identity} DB) — the shared sign-in
 * identity across every app. Holds ONLY what authenticates a person: the identifiers they can log
 * in with (email / username / phone) and the password. Personal details live in {@link UserInfo}
 * (1:1); app-specific data (employeeId, roles, profile overrides) lives per-app keyed by {@code id}.
 *
 * <p>Editing these fields changes how the user signs in to ALL apps — the UI warns accordingly.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

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

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
