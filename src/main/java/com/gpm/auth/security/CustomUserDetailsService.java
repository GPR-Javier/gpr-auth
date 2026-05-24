package com.gpm.auth.security;

import com.gpm.auth.repository.UserRepository;
import com.gpm.auth.service.UserRoleAccessResolver;
import com.gpm.common.entity.Functionality;
import com.gpm.common.entity.User;
import com.gpm.common.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleAccessResolver userRoleAccessResolver;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                true, true, true,
                buildAuthorities(user)
        );
    }

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        List<UserRole> activeRoles = userRoleAccessResolver.resolveActiveUserRoles(user);

        // Grant ROLE_ADMIN if any active user role is an admin-type role.
        // This replaces the old User.role == ADMIN check so all security decisions
        // flow through UserRole.isAdmin rather than the deprecated User.role column.
        boolean isAdmin = activeRoles.stream().anyMatch(UserRole::isAdmin);
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        activeRoles.stream()
                .flatMap(er -> er.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .filter(f -> f.getCode() != null)
                .map(f -> new SimpleGrantedAuthority(f.getCode().getCode()))
                .distinct()
                .forEach(authorities::add);

        return authorities;
    }
}
