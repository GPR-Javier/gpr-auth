package com.gpr.auth.repository;

import com.gpr.common.entity.User;
import com.gpr.common.enums.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByRole(Role role);
    Page<User> findAllByRole(Role role, Pageable pageable);
}
