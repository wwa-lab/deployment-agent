package com.wwa.agenthub.platform.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwa.agenthub.contracts.dto.integration.IntegrationEnvelope;
import com.wwa.agenthub.platform.domain.integration.artifact.ArtifactTransferAdmissionService;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Holds bounded transfer permits through response serialization for Artifact downloads. */
@Component
public class ArtifactDownloadAdmissionFilter extends OncePerRequestFilter {

    private final ArtifactTransferAdmissionService transferAdmission;
    private final ObjectMapper objectMapper;

    public ArtifactDownloadAdmissionFilter(
            ArtifactTransferAdmissionService transferAdmission,
            ObjectMapper objectMapper
    ) {
        this.transferAdmission = transferAdmission;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"GET".equalsIgnoreCase(request.getMethod())
                || !uri.startsWith("/api/v1/integration/")
                || !uri.endsWith("/content");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String identity = authentication == null ? request.getRemoteAddr() : authentication.getName();
        if (authentication instanceof IntegrationClientAuthentication integration) {
            identity = integration.clientDescriptor().applicationId();
        }
        ArtifactTransferAdmissionService.Permit permit = transferAdmission.tryAcquire(identity);
        if (permit == null) {
            reject(response);
            return;
        }
        try (permit) {
            filterChain.doFilter(request, response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), IntegrationEnvelope.ErrorResponse.of(
                "ARTIFACT_DOWNLOAD_BUSY",
                "Artifact download capacity is busy.",
                true,
                CorrelationIdFilter.current(),
                List.of()));
    }
}
