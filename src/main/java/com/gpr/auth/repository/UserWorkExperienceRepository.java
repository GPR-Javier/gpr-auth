package com.gpr.auth.repository;

import com.gpr.auth.entity.UserWorkExperience;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkExperienceRepository
        extends JpaRepository<UserWorkExperience, Long> {

    List<UserWorkExperience> findByUserIdOrderByStartDateDescIdDesc(Long userId);

    Optional<UserWorkExperience> findByIdAndUserId(Long id, Long userId);
}
