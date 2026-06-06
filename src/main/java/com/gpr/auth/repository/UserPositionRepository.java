package com.gpr.auth.repository;

import com.gpr.common.entity.UserPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPositionRepository extends JpaRepository<UserPosition, Long> {
}
