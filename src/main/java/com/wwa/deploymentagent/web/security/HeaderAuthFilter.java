package com.wwa.deploymentagent.web.security;

import com.wwa.deploymentagent.contracts.AccessScope;
import com.wwa.deploymentagent.contracts.UserContext;
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
                    parseScopes(request.getHeader("X-User-Scopes"))
            );
            UserContextAuthentication auth = new UserContextAuthentication(ctx);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private List<AccessScope> parseScopes(String rawScopes) {
        if (rawScopes == null || rawScopes.isBlank()) {
            return List.of(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD));
        }
        return java.util.Arrays.stream(rawScopes.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(scope -> {
                    String[] parts = scope.split("\\|", 2);
                    String application = parts.length > 0 ? parts[0].trim() : null;
                    String snowGroup = parts.length > 1 ? parts[1].trim() : null;
                    return new AccessScope(application, snowGroup);
                })
                .toList();
    }
}
