package com.gpm.auth.repository;

import com.gpm.common.entity.UserPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPositionRepository extends JpaRepository<UserPosition, Long> {
}