package com.wwa.deploymentagent.web.security;

import com.wwa.deploymentagent.contracts.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Blocks every write operation for anonymous guest viewers.
 *
 * <p>GUEST UserContexts are created by the {@code /api/platform/auth/guest}
 * endpoint so unauthenticated visitors can browse the platform read-only.
 * This filter runs after the auth filters and rejects any HTTP method other
 * than GET/HEAD/OPTIONS for guest sessions with 403, regardless of which
 * controller would have handled the request. The logout endpoint is the
 * only POST explicitly allowed so guests can terminate their session.
 */
@Component
public class GuestReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String LOGOUT_PATH = "/api/platform/auth/logout";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isGuestWriteAttempt(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Guest mode is read-only. Sign in to perform this action.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isGuestWriteAttempt(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || SAFE_METHODS.contains(method.toUpperCase())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path != null && path.equals(LOGOUT_PATH)) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserContext userContext)) {
            return false;
        }
        return userContext.isGuestViewer();
    }
}
