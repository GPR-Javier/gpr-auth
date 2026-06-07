package com.gpr.auth.repository;

import com.gpr.auth.entity.LoginMethod;
import com.gpr.auth.entity.User;
import com.gpr.auth.enums.LoginMethodType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginMethodRepository extends JpaRepository<LoginMethod, Long> {
    List<LoginMethod> findByUserAndActiveTrue(User user);

    Optional<LoginMethod> findByUserAndType(User user, LoginMethodType type);
}
