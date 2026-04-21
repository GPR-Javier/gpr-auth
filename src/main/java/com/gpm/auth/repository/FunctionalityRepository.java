package com.gpm.auth.repository;

import com.gpm.common.entity.Functionality;
import com.gpm.common.enums.FunctionalityCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FunctionalityRepository extends JpaRepository<Functionality, Long> {
    Optional<Functionality> findByCode(FunctionalityCode code);
    boolean existsByCode(FunctionalityCode code);
}
