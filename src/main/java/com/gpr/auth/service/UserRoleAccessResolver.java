package com.gpr.auth.service;

import com.gpr.common.entity.User;
import com.gpr.common.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRoleAccessResolver {

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<UserRole> resolveActiveUserRoles(User user) {
        return user.getActiveUserRoles(LocalDateTime.now());
    }
}
