package com.gpm.auth.repository;

import com.gpm.common.entity.User;
import com.gpm.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);
    Page<User> findAllByRole(Role role, Pageable pageable);

    @Query("SELECT MAX(CAST(SUBSTRING(u.employeeId, 5) AS int)) FROM User u WHERE u.employeeId LIKE 'EMP-%'")
    Optional<Integer> findMaxEmployeeIdSequence();
}
