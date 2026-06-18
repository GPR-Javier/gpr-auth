package com.gpr.auth.entity;
import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * A user's education history entry — identity-level data (shared across every app the identity
 * belongs to), with a real FK to {@link User} in the same {@code gpr_identity} DB. Apps consume this
 * read-only; editing happens here via the owner's self-service profile.
 */
@Entity
@Table(
        name = "user_education",
        indexes = @Index(name = "idx_user_education_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEducation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String school;

    @Column(nullable = false)
    private String degree;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Null = ongoing / present. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** e.g. "Cum Laude". */
    private String honor;

    @Column(columnDefinition = "text")
    private String description;
}
