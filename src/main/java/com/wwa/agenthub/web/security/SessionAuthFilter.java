package com.wwa.agenthub.web.security;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.domain.auth.AccessGrantService;
import com.wwa.agenthub.errors.AccessNotGrantedAppException;
import com.wwa.agenthub.errors.AccessSuspendedAppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads UserContext from the HTTP session and populates the SecurityContext.
 * Runs before HeaderAuthFilter so that session-based auth takes priority.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private final AccessGrantService accessGrantService;
    private final boolean grantRevalidationEnabled;

    public SessionAuthFilter(
            AccessGrantService accessGrantService,
            @Value("${app.auth.session-grant-revalidation-enabled:true}")
            boolean grantRevalidationEnabled
    ) {
        this.accessGrantService = accessGrantService;
        this.grantRevalidationEnabled = grantRevalidationEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof com.wwa.agenthub.platform.web.security.IntegrationClientAuthentication) {
            filterChain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            UserContext ctx = (UserContext) session.getAttribute(SessionIdentityAttributes.USER_CONTEXT);
            if (ctx != null) {
                boolean syntheticGuest = Boolean.TRUE.equals(
                        session.getAttribute(SessionIdentityAttributes.SYNTHETIC_GUEST));
                if (syntheticGuest && !ctx.isGuestViewer()) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                if (grantRevalidationEnabled && !syntheticGuest) {
                    try {
                        ctx = accessGrantService.refreshAuthorizedContext(ctx.userId());
                        session.setAttribute(SessionIdentityAttributes.USER_CONTEXT, ctx);
                    } catch (AccessNotGrantedAppException | AccessSuspendedAppException exception) {
                        session.invalidate();
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
                UserContextAuthentication auth = new UserContextAuthentication(ctx);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
