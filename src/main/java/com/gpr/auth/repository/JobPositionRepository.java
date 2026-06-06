package com.gpr.auth.repository;

import com.gpr.common.entity.JobPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {
}
