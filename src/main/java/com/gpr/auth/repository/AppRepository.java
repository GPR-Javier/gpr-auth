package com.gpr.auth.repository;

import com.gpr.auth.entity.App;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByClientId(String clientId);
}
