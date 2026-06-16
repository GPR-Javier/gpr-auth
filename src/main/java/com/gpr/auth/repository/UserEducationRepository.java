package com.gpr.auth.repository;

import com.gpr.auth.entity.UserEducation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEducationRepository extends JpaRepository<UserEducation, Long> {

    /** All education for an identity, most recent first. {@code UserId} resolves to {@code user.id}. */
    List<UserEducation> findByUserIdOrderByStartDateDescIdDesc(Long userId);

    /** Ownership-checked lookup — used by update/delete so a user only touches their own rows. */
    Optional<UserEducation> findByIdAndUserId(Long id, Long userId);
}
