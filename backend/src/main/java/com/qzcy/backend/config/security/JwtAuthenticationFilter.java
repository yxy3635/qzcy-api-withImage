package com.qzcy.backend.config.security;

import com.qzcy.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String TOKEN_PREFIX = "Bearer ";
    private final JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/api/images/") || path.equals("/api/payment/notify");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerValue = request.getHeader("Authorization");
        if (headerValue != null && headerValue.startsWith(TOKEN_PREFIX)) {
            String jwtValue = extractBearerValue(headerValue);
            if (jwtUtil.validateToken(jwtValue)) {
                Long userId = jwtUtil.getUserId(jwtValue);
                String username = jwtUtil.getUsername(jwtValue);
                String role = jwtUtil.getRole(jwtValue);
                JwtUserPrincipal principal = new JwtUserPrincipal(userId, username, role);
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(principal));
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerValue(String headerValue) {
        char[] chars = headerValue.toCharArray();
        return new String(chars, TOKEN_PREFIX.length(), chars.length - TOKEN_PREFIX.length());
    }

    private static final class JwtAuthentication implements Authentication {
        private final JwtUserPrincipal principal;
        private boolean authenticated = true;

        private JwtAuthentication(JwtUserPrincipal principal) {
            this.principal = principal;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public void setAuthenticated(boolean authenticated) {
            this.authenticated = authenticated;
        }

        @Override
        public String getName() {
            return principal.username();
        }
    }
}
