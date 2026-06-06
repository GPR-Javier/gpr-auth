package com.gpr.auth.repository;

import com.gpr.common.entity.UserTemporaryRoleAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTemporaryRoleAssignmentRepository extends JpaRepository<UserTemporaryRoleAssignment, Long> {
    List<UserTemporaryRoleAssignment> findByUserIdAndActiveTrue(Long userId);
    List<UserTemporaryRoleAssignment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserTemporaryRoleAssignment> findByIdAndUserId(Long id, Long userId);
}
