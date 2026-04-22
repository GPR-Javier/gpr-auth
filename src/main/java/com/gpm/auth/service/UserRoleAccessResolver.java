package com.gpm.auth.service;

import com.gpm.common.entity.User;
import com.gpm.common.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleAccessResolver {

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<UserRole> resolveActiveUserRoles(User user) {
        return user.getActiveUserRoles(LocalDateTime.now());
    }
}
