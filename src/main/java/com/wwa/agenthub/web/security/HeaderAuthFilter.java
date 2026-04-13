package com.wwa.agenthub.web.security;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Extracts WWA user identity from HTTP headers and populates the Spring SecurityContext.
 *
 * <p>Header contract:
 * <ul>
 *   <li>{@code X-User-Id}   – operator identifier (e.g. employee ID)</li>
 *   <li>{@code X-User-Role} – RBAC role: DEVELOPER | TL | DEVOPS_ADMIN | AUDIT | MANAGEMENT</li>
 * </ul>
 *
 * <p>Skips if:
 * <ul>
 *   <li>SecurityContext is already authenticated (e.g. from SessionAuthFilter)</li>
 *   <li>Header fallback is disabled via {@code app.auth.header-fallback-enabled=false}</li>
 * </ul>
 */
@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Value("${app.auth.header-fallback-enabled:true}")
    private boolean headerFallbackEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if already authenticated (session auth took precedence)
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if header fallback is disabled
        if (!headerFallbackEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (userId != null && !userId.isBlank() && role != null && !role.isBlank()) {
            UserContext ctx = new UserContext(
                    userId.trim(),
                    role.trim(),
                    List.of(role.trim()),
                    Set.of(),
                    userId.trim(),
                    List.of(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD))
            );
            UserContextAuthentication auth = new UserContextAuthentication(ctx);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
