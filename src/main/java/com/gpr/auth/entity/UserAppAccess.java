package com.gpr.auth.entity;

import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Grants a user access to a specific app. One identity, many access grants — adding an app means
 * adding a row here, never a new user. Login is rejected if the (user, app) grant is missing for
 * an invite-only app.
 */
@Entity
@Table(
        name = "user_app_access",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_app", columnNames = {"user_id", "app_id"}),
        indexes = @Index(name = "idx_uaa_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserAppAccess extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    @PrePersist
    void onCreate() {
        grantedAt = LocalDateTime.now();
    }
}
