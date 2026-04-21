package com.gpm.auth.repository;

import com.gpm.common.entity.AccessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {
    Optional<AccessRole> findByPageCode(String pageCode);
    boolean existsByPageCode(String pageCode);
}
