package com.gpr.auth.entity;

import com.gpr.auth.enums.LoginMethodType;
import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import lombok.*;

/**
 * One way a user can sign in. A user has many methods (PASSWORD today; OAuth/magic-link later).
 * Credentials are global — the same method authenticates the user across every app.
 */
@Entity
@Table(
        name = "login_methods",
        indexes = @Index(name = "idx_lm_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginMethod extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginMethodType type;

    /** BCrypt hash for PASSWORD; null for external providers. */
    @Column(name = "secret_hash")
    private String secretHash;

    /** Provider subject id for OAuth methods; null for PASSWORD. */
    @Column(name = "external_subject")
    private String externalSubject;

    /** Provider key for OAuth methods (e.g. "google", "microsoft"); null for PASSWORD. */
    @Column(name = "provider")
    private String provider;

    @Column(nullable = false)
    private boolean active;
}
