package com.gpr.auth.repository;

import com.gpr.auth.entity.UserInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {
    Optional<UserInfo> findByUserId(Long userId);

    /** Profile rows for the given identity ids — indexed lookup for batch summary projections. */
    List<UserInfo> findByUser_IdIn(Collection<Long> userIds);
}
