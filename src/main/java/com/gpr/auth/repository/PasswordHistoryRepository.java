package com.gpr.auth.repository;

import com.gpr.auth.entity.PasswordHistory;
import com.gpr.auth.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findByUserOrderByCreatedAtDesc(User user);
}
