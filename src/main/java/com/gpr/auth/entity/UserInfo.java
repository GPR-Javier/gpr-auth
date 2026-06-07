package com.gpr.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Canonical personal details for an identity (1:1 with {@link User}, real FK — same DB). This is the
 * source-of-truth profile captured at account creation and the default each app snapshots from. Apps
 * keep their own overridable copy; editing this canonical record affects apps the user hasn't yet
 * customized / joins later, so the UI warns when it changes.
 */
@Entity
@Table(name = "user_info", indexes = @Index(name = "idx_user_info_user", columnList = "user_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    private LocalDate birthday;

    private String address;

    private String gender;

    @Column(name = "profile_photo")
    private String profilePhoto;

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
