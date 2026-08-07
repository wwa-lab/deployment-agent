package com.wwa.agenthub.platform.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.integration.IntegrationEnvelope;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationRequestRateLimiter;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import com.wwa.agenthub.web.security.UserContextAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Applies a token bucket after Integration bearer/session authentication. */
@Component
@RequiredArgsConstructor
public class IntegrationRequestRateLimitFilter extends OncePerRequestFilter {

    private final IntegrationRequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/integration/")
                && !request.getRequestURI().equals("/api/v1/integration");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (rateLimiter.tryAcquire(identity(authentication, request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), IntegrationEnvelope.ErrorResponse.of(
                "RATE_LIMITED",
                "The Atlas Integration request rate limit has been reached.",
                true,
                CorrelationIdFilter.current(),
                List.of()));
    }

    private static String identity(Authentication authentication, HttpServletRequest request) {
        if (authentication instanceof IntegrationClientAuthentication integration) {
            return "client:" + integration.clientDescriptor().applicationId()
                    + ":" + integration.clientDescriptor().user().userId();
        }
        if (authentication instanceof UserContextAuthentication contextAuthentication
                && contextAuthentication.getPrincipal() instanceof UserContext user) {
            return "web:" + user.userId() + ":" + request.getRemoteAddr();
        }
        return "auth:" + authentication.getName() + ":" + request.getRemoteAddr();
    }
}
