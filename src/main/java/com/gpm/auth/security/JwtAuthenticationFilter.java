package com.gpm.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractAccessToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.extractAllClaims(token);
            String email = claims.get("email", String.class);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            List<GrantedAuthority> effectiveAuthorities = new ArrayList<>(userDetails.getAuthorities());
            // Merge token authorities as fallback so a valid, recently-issued token keeps working
            // even if DB role-assignment rows were migrated/updated after login.
            List<String> tokenAuthorities = jwtService.extractAuthorities(token);
            if (tokenAuthorities != null) {
                tokenAuthorities.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authority -> {
                            if (!effectiveAuthorities.contains(authority)) {
                                effectiveAuthorities.add(authority);
                            }
                        });
            }
            String tokenRole = claims.get("role", String.class);
            if ("ADMIN".equalsIgnoreCase(tokenRole)) {
                SimpleGrantedAuthority adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");
                if (!effectiveAuthorities.contains(adminAuthority)) {
                    effectiveAuthorities.add(adminAuthority);
                }
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, effectiveAuthorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

    private String extractAccessToken(HttpServletRequest request) {
        String cookieToken = extractAccessTokenCookie(request);
        if (cookieToken != null && jwtService.validateToken(cookieToken)) {
            return cookieToken;
        }

        String bearerToken = extractBearerToken(request);
        if (bearerToken != null && jwtService.validateToken(bearerToken)) {
            return bearerToken;
        }

        return null;
    }

    private String extractAccessTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "access_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
