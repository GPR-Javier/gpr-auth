package com.gpr.auth.entity;
import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * A user's certificate / credential — identity-level data (shared across every app the identity
 * belongs to), real FK to {@link User} in the same {@code gpr_identity} DB. {@code expiryDate} null =
 * never expires; "active vs expired" is derived from it on display.
 */
@Entity
@Table(
        name = "user_certificate",
        indexes = @Index(name = "idx_user_certificate_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCertificate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String issuer;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    /** Null = no expiry. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "credential_id")
    private String credentialId;

    @Column(name = "credential_url")
    private String credentialUrl;
}
