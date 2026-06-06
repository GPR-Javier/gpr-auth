package com.gpr.auth.repository;

import com.gpr.common.entity.AccessRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {
    Optional<AccessRole> findByPageCode(String pageCode);
    boolean existsByPageCode(String pageCode);
}
