package com.gpr.auth.entity;

import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Membership: which companies an identity belongs to (many-to-many). A user with several
 * memberships chooses one at login (and can switch). Super admins bypass this and may enter any
 * company. Real FKs — same DB as users/companies.
 */
@Entity
@Table(
        name = "user_company",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_company", columnNames = {"user_id", "company_id"}),
        indexes = @Index(name = "idx_uc_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserCompany extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}
