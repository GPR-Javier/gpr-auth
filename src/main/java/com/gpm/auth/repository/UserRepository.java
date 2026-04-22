package com.gpm.auth.repository;

import com.gpm.common.entity.User;
import com.gpm.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByRole(Role role);
    Page<User> findAllByRole(Role role, Pageable pageable);
}
