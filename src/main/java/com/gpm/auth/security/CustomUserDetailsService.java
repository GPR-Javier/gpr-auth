package com.gpm.auth.security;

import com.gpm.auth.repository.UserRepository;
import com.gpm.auth.service.UserRoleAccessResolver;
import com.gpm.common.entity.Functionality;
import com.gpm.common.entity.User;
import com.gpm.common.enums.Role;
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

        if (user.getRole() == Role.ADMIN) {
            // ADMINs bypass granular checks — a single role authority covers all admin endpoints
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return authorities;
        }

        userRoleAccessResolver.resolveActiveUserRoles(user).stream()
                .flatMap(er -> er.getAccessRoles().stream())
                .flatMap(ar -> ar.getFunctionalities().stream())
                .filter(Functionality::isEnabled)
                .map(f -> new SimpleGrantedAuthority(f.getCode().getCode()))
                .distinct()
                .forEach(authorities::add);

        return authorities;
    }
}
