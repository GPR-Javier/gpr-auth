package com.gpr.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * A tenant. Companies are owned by gpr-auth and SHARED across every app — each app scopes its own
 * resources (WorkOS roles/permissions, etc.) by this company's {@code id} with no cross-DB FK.
 * Created and managed by platform super admins.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    // ── Descriptive profile (the "My Company" screen) — editable by a company admin via WorkOS ──
    private String tagline;

    @Column(columnDefinition = "text")
    private String about;

    private String industry;

    private String founded;

    @Column(name = "company_size")
    private String companySize;

    private String headquarters;

    private String email;

    private String phone;

    private String website;

    @Column(columnDefinition = "text")
    private String address;

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
