package com.gpm.auth.repository;

import com.gpm.common.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
