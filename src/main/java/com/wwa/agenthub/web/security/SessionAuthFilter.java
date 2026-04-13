package com.wwa.agenthub.web.security;

import com.wwa.agenthub.contracts.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads UserContext from the HTTP session and populates the SecurityContext.
 * Runs before HeaderAuthFilter so that session-based auth takes priority.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final String USER_CONTEXT_ATTR = "USER_CONTEXT";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            UserContext ctx = (UserContext) session.getAttribute(USER_CONTEXT_ATTR);
            if (ctx != null) {
                UserContextAuthentication auth = new UserContextAuthentication(ctx);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
