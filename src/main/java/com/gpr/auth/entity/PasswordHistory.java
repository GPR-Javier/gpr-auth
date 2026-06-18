package com.gpr.auth.entity;

import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import lombok.*;

/**
 * A previously-used password hash, for reuse prevention. On a password change, BCrypt-match the
 * candidate against recent rows (salted hashes can't be string-compared). Policy is global —
 * applies to the user across every app, since credentials are shared.
 */
@Entity
@Table(
        name = "password_history",
        indexes = @Index(name = "idx_pwh_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHistory extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
}
