package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.IntegrationClientType;
import com.wwa.agenthub.contracts.validation.IntegrationResourceIds;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.web.security.IntegrationClientAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class IntegrationActorResolver {

    private static final Pattern SAFE_APPLICATION = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Value("${app.auth.header-fallback-enabled:true}")
    private boolean headerFallbackEnabled;

    private final StagePipelineRegistry stagePipelineRegistry;

    public IntegrationActorResolver(StagePipelineRegistry stagePipelineRegistry) {
        this.stagePipelineRegistry = stagePipelineRegistry;
    }

    public IntegrationActor resolve(UserContext user, HttpServletRequest request) {
        if (user == null) {
            throw new IntegrationApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required.",
                    false);
        }
        UserContext contractUser = contractUser(user);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IntegrationClientAuthentication integration) {
            IntegrationClientDescriptor descriptor = integration.clientDescriptor();
            return new IntegrationActor(
                    contractUser,
                    descriptor.applicationId(),
                    descriptor.clientType(),
                    descriptor.clientVersion(),
                    descriptor.allowedAgents(),
                    true);
        }

        boolean explicitHeaderIdentity = headerFallbackEnabled
                && request.getHeader("X-User-Id") != null;
        String applicationId = explicitHeaderIdentity
                ? safeApplication(request.getHeader("X-Atlas-Client-Application"), "atlas-test-client")
                : "atlas-web";
        IntegrationClientType clientType = explicitHeaderIdentity
                ? parseClientType(request.getHeader("X-Atlas-Client-Type"))
                : IntegrationClientType.MANUAL;
        String version = normalizeBounded(request.getHeader("X-Atlas-Client-Version"), 128);

        return new IntegrationActor(
                contractUser,
                applicationId,
                clientType,
                version,
                stagePipelineRegistry.registeredAgentIds(),
                false);
    }

    private static UserContext contractUser(UserContext user) {
        if (!IntegrationResourceIds.isValid(user.userId())) {
            throw IntegrationApiException.badRequest(
                    "INVALID_ACTOR_IDENTITY",
                    "The authenticated user identifier is incompatible with the Atlas ResourceID contract.");
        }
        String displayName = user.displayName();
        if (displayName == null
                || displayName.isBlank()
                || displayName.length() > 300
                || displayName.chars().anyMatch(Character::isISOControl)) {
            displayName = user.userId();
        }
        return new UserContext(
                user.userId(),
                user.role(),
                user.roles(),
                user.permissions(),
                displayName,
                user.scopes());
    }

    private static IntegrationClientType parseClientType(String value) {
        if (value == null || value.isBlank()) {
            return IntegrationClientType.MANUAL;
        }
        try {
            return IntegrationClientType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "X-Atlas-Client-Type is not supported.");
        }
    }

    private static String safeApplication(String value, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value.trim();
        if (!SAFE_APPLICATION.matcher(candidate).matches()) {
            throw IntegrationApiException.badRequest(
                    "INVALID_REQUEST",
                    "X-Atlas-Client-Application is invalid.");
        }
        return candidate;
    }

    private static String normalizeBounded(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw IntegrationApiException.badRequest("INVALID_REQUEST", "Client version is too long.");
        }
        return normalized;
    }
}
