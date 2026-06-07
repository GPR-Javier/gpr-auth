package com.gpr.auth.repository;

import com.gpr.auth.entity.UserCompany;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCompanyRepository extends JpaRepository<UserCompany, Long> {
    List<UserCompany> findByUserId(Long userId);
    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);
}
