package com.gpr.auth.entity;

import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@SuperBuilder
public class Company extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    /**
     * The identity (gpr-auth user id) of this company's founding Company Admin. Recorded at creation so
     * each app can grant that user its admin role when it provisions the company's resources — no
     * cross-DB FK, resolved by id. Null only for legacy companies created before this was tracked.
     */
    @Column(name = "admin_user_id")
    private Long adminUserId;

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
}
