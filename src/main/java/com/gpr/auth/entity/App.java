package com.gpr.auth.entity;

import com.gpr.auth.enums.RegistrationMode;
import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A product that authenticates against this identity provider (e.g. WorkOS, and later pet-vet,
 * rental). Tokens are scoped to an app via the {@code aud} claim; access is gated by
 * {@link UserAppAccess}.
 */
@Entity
@Table(name = "apps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class App extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier sent by the app at login and stamped into the JWT {@code aud}, e.g. "workos". */
    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_mode", nullable = false)
    private RegistrationMode registrationMode;

}
