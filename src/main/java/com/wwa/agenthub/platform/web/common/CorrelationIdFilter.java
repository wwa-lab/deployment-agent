package com.wwa.agenthub.platform.web.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * CorrelationIdFilter — propagates a request-scoped correlation ID into SLF4J
 * MDC and echoes it back in the response header.
 *
 * <p>Day-1 infrastructure debt fix (not an MVP Foundation Seam). Every inbound
 * request must carry an ID that can be used to stitch together:
 * <ul>
 *   <li>Server logs (via {@code %X{correlationId}} in the logback pattern)</li>
 *   <li>Audit log entries (via the {@code correlation_id} column on
 *       {@code DA_AUDIT_LOG_ENTRY})</li>
 *   <li>Downstream Jenkins / Ansible submissions (forwarded as the same
 *       header by {@link CorrelationIdRestTemplateInterceptor})</li>
 *   <li>Frontend error toasts (axios response interceptor reads the echoed
 *       header so the user can quote it when filing a bug)</li>
 * </ul>
 *
 * <p>The filter is registered with the highest precedence so that it runs
 * before Spring Security — this guarantees that even auth failures and
 * exception handlers see a correlation ID in the MDC.
 *
 * <p>Inbound header name: {@value #HEADER_NAME}. Clients may supply their own
 * ID (useful for end-to-end tests or cross-service flows); otherwise a
 * freshly-generated short UUID is used.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /**
     * Accepts only safe characters in client-supplied IDs (letters, digits,
     * hyphen, underscore) with a length cap. Anything else is replaced with
     * a fresh UUID so a malicious client cannot inject log-forging sequences
     * or oversized strings into the MDC and downstream log pipelines.
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER_NAME);
        String correlationId = isSafe(supplied) ? supplied : newCorrelationId();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isSafe(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    private static String newCorrelationId() {
        // Short, URL-safe, enough entropy for single-tenant request correlation.
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Utility for services that need to read the current correlation ID
     * (e.g. {@code AuditLoggerService} when persisting an audit row).
     * Returns {@code null} if no correlation ID is in the MDC — callers
     * should handle that case gracefully without throwing.
     */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
