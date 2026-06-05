package com.gpm.auth.repository;

import com.gpm.common.entity.UserRoleAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {
    List<UserRoleAssignment> findByUserId(Long userId);
    Optional<UserRoleAssignment> findByUserIdAndUserRoleId(Long userId, Long userRoleId);
}
