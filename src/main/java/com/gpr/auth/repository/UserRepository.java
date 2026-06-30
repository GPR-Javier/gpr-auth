package com.gpr.auth.repository;

import com.gpr.auth.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    /** Login resolution: a user may sign in with any of their identifiers. */
    Optional<User> findByEmailOrUsernameOrPhone(String email, String username, String phone);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);

    /**
     * Identity ids whose email, username, or full name (first + last) contain {@code q}
     * (case-insensitive). Apps use this to power name/email search over their own user set: they
     * resolve matching ids here, then intersect with their company-scoped profiles. Personal name is
     * owned here (in {@code UserInfo}), so this is the only place such a search can run.
     *
     * @param q the search term (caller trims/guards blank)
     * @return matching identity ids (may be empty)
     */
    @Query("""
            SELECT u.id FROM User u LEFT JOIN UserInfo i ON i.user = u
            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(CONCAT(CONCAT(COALESCE(i.firstName, ''), ' '), COALESCE(i.lastName, '')))
                    LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    List<Long> searchIds(@Param("q") String q);
}
