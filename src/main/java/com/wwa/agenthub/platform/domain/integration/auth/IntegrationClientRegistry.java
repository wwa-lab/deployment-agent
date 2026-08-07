package com.wwa.agenthub.platform.domain.integration.auth;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.validation.IntegrationResourceIds;
import com.wwa.agenthub.domain.auth.PermissionResolver;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IntegrationClientRegistry {

    private static final HexFormat HEX = HexFormat.of();
    private final IntegrationClientProperties properties;
    private final PermissionResolver permissionResolver;
    private final StagePipelineRegistry stagePipelineRegistry;
    private final Clock clock;

    private List<IntegrationClientDescriptor> descriptors = List.of();

    @PostConstruct
    void initialize() {
        List<IntegrationClientDescriptor> loaded = new ArrayList<>();
        Set<String> digests = new HashSet<>();
        Set<String> applications = new HashSet<>();

        for (IntegrationClientProperties.Client source : properties.getClients()) {
            String digest = required(source.getTokenSha256(), "token-sha256").toLowerCase();
            if (!digest.matches("[a-f0-9]{64}")) {
                throw new IllegalStateException("Integration client token-sha256 must be 64 lowercase hex characters");
            }
            String applicationId = required(source.getApplicationId(), "application-id");
            String userId = required(source.getUserId(), "user-id");
            if (!IntegrationResourceIds.isValid(applicationId)) {
                throw new IllegalStateException(
                        "Integration client application-id must be a safe 1-128 character identifier");
            }
            if (!IntegrationResourceIds.isValid(userId)) {
                throw new IllegalStateException(
                        "Integration client user-id must match the Atlas ResourceID contract");
            }
            if (source.getClientType() == null) {
                throw new IllegalStateException("Integration client client-type is required");
            }
            if (!digests.add(digest) || !applications.add(applicationId)) {
                throw new IllegalStateException("Duplicate Integration client digest or application-id");
            }

            Set<String> allowedAgents = normalizedSet(source.getAllowedAgents());
            if (allowedAgents.isEmpty()) {
                throw new IllegalStateException("Integration client allowed-agents must not be empty");
            }
            for (String agent : allowedAgents) {
                if (!"*".equals(agent) && !stagePipelineRegistry.contains(agent)) {
                    throw new IllegalStateException("Unknown Integration client Agent Module: " + agent);
                }
            }

            List<String> roles = normalizedList(source.getRoles());
            if (roles.stream().anyMatch(role -> "GUEST".equalsIgnoreCase(role))) {
                throw new IllegalStateException(
                        "Integration client roles cannot contain GUEST; Guest is a synthetic Web identity");
            }
            Set<String> permissions = new LinkedHashSet<>(permissionResolver.resolvePermissions(roles));
            permissions.addAll(normalizedSet(source.getPermissions()));
            List<AccessScope> scopes = source.getScopes().stream()
                    .map(IntegrationClientRegistry::parseScope)
                    .toList();
            String displayName = normalize(source.getDisplayName());
            if (displayName != null
                    && (displayName.length() > 300
                    || displayName.chars().anyMatch(Character::isISOControl))) {
                throw new IllegalStateException("Integration client display-name is invalid");
            }
            UserContext user = new UserContext(
                    userId,
                    roles.isEmpty() ? "INTEGRATION_CLIENT" : roles.getFirst(),
                    roles.isEmpty() ? List.of("INTEGRATION_CLIENT") : roles,
                    Set.copyOf(permissions),
                    displayName,
                    scopes);
            String clientVersion = normalize(source.getClientVersion());
            if (clientVersion != null
                    && (clientVersion.length() > 128
                    || clientVersion.chars().anyMatch(Character::isISOControl))) {
                throw new IllegalStateException("Integration client client-version is invalid");
            }

            if (source.isEnabled()) {
                loaded.add(new IntegrationClientDescriptor(
                        HEX.parseHex(digest),
                        applicationId,
                        source.getClientType(),
                        clientVersion,
                        user,
                        allowedAgents,
                        source.getExpiresAt()));
            }
        }

        descriptors = List.copyOf(loaded);
    }

    public Optional<IntegrationClientDescriptor> authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() < 16 || rawToken.length() > 4096) {
            return Optional.empty();
        }
        byte[] presented = sha256(rawToken);
        IntegrationClientDescriptor match = null;
        for (IntegrationClientDescriptor descriptor : descriptors) {
            boolean equals = MessageDigest.isEqual(presented, descriptor.tokenDigest());
            if (equals && (descriptor.expiresAt() == null || descriptor.expiresAt().isAfter(clock.instant()))) {
                match = descriptor;
            }
        }
        return Optional.ofNullable(match);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static AccessScope parseScope(String value) {
        String[] parts = required(value, "scope").split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("Integration client scope must be application|snowGroup");
        }
        return new AccessScope(parts[0], parts[1]);
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalStateException("Integration client " + field + " is required");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> normalizedList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(IntegrationClientRegistry::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private static Set<String> normalizedSet(List<String> values) {
        return Set.copyOf(normalizedList(values));
    }
}
