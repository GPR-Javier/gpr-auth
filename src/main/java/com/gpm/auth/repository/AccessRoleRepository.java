package com.gpm.auth.repository;

import com.gpm.common.entity.AccessRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {
    Optional<AccessRole> findByPageCode(String pageCode);
    boolean existsByPageCode(String pageCode);
}
