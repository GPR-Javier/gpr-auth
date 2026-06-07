package com.gpr.auth.repository;

import com.gpr.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    /** Login resolution: a user may sign in with any of their identifiers. */
    Optional<User> findByEmailOrUsernameOrPhone(String email, String username, String phone);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
}
