package com.gpr.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * A user's work-experience entry — identity-level data (shared across every app the identity belongs
 * to), real FK to {@link User} in the same {@code gpr_identity} DB. {@code endDate} null = current role.
 */
@Entity
@Table(
        name = "user_work_experience",
        indexes = @Index(name = "idx_user_work_experience_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWorkExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    /** e.g. FULL_TIME / PART_TIME / CONTRACT / INTERNSHIP — free-form, optional. */
    @Column(name = "employment_type")
    private String employmentType;

    private String location;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Null = current role. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
