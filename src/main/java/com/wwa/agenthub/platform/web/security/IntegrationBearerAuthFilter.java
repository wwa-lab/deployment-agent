package com.wwa.agenthub.platform.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.dto.integration.IntegrationEnvelope;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientDescriptor;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationClientRegistry;
import com.wwa.agenthub.platform.domain.integration.auth.IntegrationRequestRateLimiter;
import com.wwa.agenthub.platform.domain.integration.auth.PresentedCredentialLeakGuard;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IntegrationBearerAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final IntegrationClientRegistry registry;
    private final ObjectMapper objectMapper;
    private final PresentedCredentialLeakGuard credentialLeakGuard;
    private final IntegrationRequestRateLimiter rateLimiter;

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
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            writeRejectedAuthentication(request, response);
            return;
        }

        String token = authorization.substring(PREFIX.length()).trim();
        Optional<IntegrationClientDescriptor> descriptor = registry.authenticate(token);
        if (descriptor.isEmpty()) {
            SecurityContextHolder.clearContext();
            writeRejectedAuthentication(request, response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new IntegrationClientAuthentication(descriptor.orElseThrow()));
        try (var ignored = credentialLeakGuard.bind(token)) {
            filterChain.doFilter(request, response);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), IntegrationEnvelope.ErrorResponse.of(
                "AUTHENTICATION_REQUIRED",
                "A valid Atlas Integration credential is required.",
                false,
                CorrelationIdFilter.current(),
                List.of()));
    }

    private void writeRejectedAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        if (rateLimiter.tryAcquireAuthenticationAttempt(request.getRemoteAddr())) {
            writeUnauthorized(response);
        } else {
            writeRateLimited(response);
        }
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), IntegrationEnvelope.ErrorResponse.of(
                "RATE_LIMITED",
                "The Atlas Integration authentication attempt rate limit has been reached.",
                true,
                CorrelationIdFilter.current(),
                List.of()));
    }
}
