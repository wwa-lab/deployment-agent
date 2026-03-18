package com.wwa.deploymentagent.web.security;

import com.wwa.deploymentagent.contracts.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts WWA user identity from HTTP headers and populates the Spring SecurityContext.
 *
 * <p>Header contract (mirrors the TypeScript extractUserContext middleware):
 * <ul>
 *   <li>{@code X-User-Id}   – operator identifier (e.g. employee ID)</li>
 *   <li>{@code X-User-Role} – RBAC role: DEVELOPER | TL | DEVOPS_ADMIN | AUDIT | MANAGEMENT</li>
 * </ul>
 *
 * <p>If either header is absent the SecurityContext is not set, and Spring Security
 * will return 401 for any endpoint requiring authentication.
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
            UserContext ctx = new UserContext(userId.trim(), role.trim());
            UserContextAuthentication auth = new UserContextAuthentication(ctx);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
