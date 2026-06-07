package com.gpr.auth.repository;

import com.gpr.auth.entity.App;
import com.gpr.auth.entity.User;
import com.gpr.auth.entity.UserAppAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAppAccessRepository extends JpaRepository<UserAppAccess, Long> {
    boolean existsByUserAndApp(User user, App app);

    Optional<UserAppAccess> findByUserAndApp(User user, App app);

    List<UserAppAccess> findByUser(User user);
}
