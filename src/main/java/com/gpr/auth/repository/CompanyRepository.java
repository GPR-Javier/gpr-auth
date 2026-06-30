package com.gpr.auth.repository;

import com.gpr.auth.entity.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findBySlug(String slug);
    boolean existsBySlug(String slug);

    /** Active companies only — the super-admin company picker (filter done in the DB, not in memory). */
    List<Company> findByActiveTrue();
}
