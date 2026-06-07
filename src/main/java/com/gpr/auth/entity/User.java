package com.gpr.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * The central identity record. Owned by gpr-auth and stored in the {@code gpr_identity} database —
 * the single source of truth for who a person is across every app. Holds IDENTITY ONLY: credentials
 * and name. App-specific data (WorkOS employeeId/role/positions, pet-vet profiles, etc.) lives in
 * each app's own database, keyed by this {@code id} with NO cross-DB foreign key.
 *
 * <p>{@code employeeId} is retained here as a stable human-readable handle exposed by the user
 * directory ({@code /users/summaries}); it is generated at identity-creation time.
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

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

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
