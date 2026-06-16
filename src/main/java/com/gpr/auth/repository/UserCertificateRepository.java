package com.gpr.auth.repository;

import com.gpr.auth.entity.UserCertificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCertificateRepository
        extends JpaRepository<UserCertificate, Long> {

    List<UserCertificate> findByUserIdOrderByIssuedDateDescIdDesc(Long userId);

    Optional<UserCertificate> findByIdAndUserId(Long id, Long userId);
}
