package com.wwa.agenthub.domain.resourcecenter;

import com.wwa.agenthub.contracts.dto.DirectoryGroupUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryLinkUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryScopeUpsertRequest;
import com.wwa.agenthub.contracts.enums.DirectoryGroupType;
import com.wwa.agenthub.contracts.enums.DirectoryLinkIconKey;
import com.wwa.agenthub.contracts.enums.DirectoryLinkKind;
import com.wwa.agenthub.contracts.enums.DirectoryScopeLayout;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryGroup;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryLink;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;
import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure validator for Resource Center catalog mutations and seed data. */
@Component
public class ResourceCenterValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,31}$");
    private static final Pattern WORKSPACE_URL = Pattern.compile("^/wwa/[A-Za-z0-9._~\\-/]*$");
    private static final int MAX_TITLE = 120;
    private static final int MAX_DESCRIPTION = 240;
    private static final int MAX_KIND_LABEL = 24;
    private static final int MAX_URL = 1024;
    private static final int MIN_SORT_ORDER = 0;
    private static final int MAX_SORT_ORDER = 9999;
    private static final int MIN_STAGE_ORDER = 1;
    private static final int MAX_STAGE_ORDER = 99;

    public void validateScopeUpsert(
            DirectoryScopeUpsertRequest request,
            List<DirectoryScope> existingScopes,
            String pathScopeKey) {
        if (request == null) {
            throw new ValidationAppException("request: body is required");
        }

        String normalizedKey = normalizeKeyRequired(request.key(), "key", pathScopeKey == null);

        if (pathScopeKey != null) {
            if (request.key() != null && !normalizedKey.equals(pathScopeKey)) {
                throw new ValidationAppException("key: scope key is immutable");
            }
        } else {
            boolean duplicate = existingScopes.stream().anyMatch(s -> s.key().equals(normalizedKey));
            if (duplicate) {
                throw new ValidationAppException("key: scope key already exists: " + normalizedKey);
            }
        }

        if (request.layout() == null) {
            throw new ValidationAppException("layout: layout is required");
        }

        long otherStageStrips = existingScopes.stream()
                .filter(s -> s.layout() == DirectoryScopeLayout.STAGE_STRIP)
                .filter(s -> pathScopeKey == null || !s.key().equals(pathScopeKey))
                .count();
        if (request.layout() == DirectoryScopeLayout.STAGE_STRIP && otherStageStrips > 0) {
            throw new ValidationAppException("layout: at most one scope may use stage-strip layout");
        }

        requireTitle(request.title(), "title");
        validateOptionalDescription(request.description(), "description");
        validateSortOrder(request.sortOrder(), "sortOrder");
    }

    public void validateGroupUpsert(
            DirectoryGroupUpsertRequest request,
            DirectoryScope parentScope,
            List<DirectoryGroup> existingGroupsInScope,
            String pathGroupKey) {
        if (request == null) {
            throw new ValidationAppException("request: body is required");
        }
        if (parentScope == null) {
            throw new ValidationAppException("scope: parent scope is required");
        }

        String normalizedKey = normalizeKeyRequired(request.key(), "key", pathGroupKey == null);

        if (pathGroupKey != null) {
            if (request.key() != null && !normalizedKey.equals(pathGroupKey)) {
                throw new ValidationAppException("key: group key is immutable");
            }
        } else {
            boolean duplicate = existingGroupsInScope.stream().anyMatch(g -> g.key().equals(normalizedKey));
            if (duplicate) {
                throw new ValidationAppException("key: group key already exists in scope: " + normalizedKey);
            }
        }

        if (request.type() == null) {
            throw new ValidationAppException("type: group type is required");
        }

        requireTitle(request.title(), "title");
        validateOptionalDescription(request.description(), "description");
        validateSortOrder(request.sortOrder(), "sortOrder");
        validateOptionalAgentName(request.agentName(), "agentName");

        if (request.type() == DirectoryGroupType.stage) {
            if (request.stageKey() == null) {
                throw new ValidationAppException("stageKey: required when type is stage");
            }
            if (request.stageOrder() == null) {
                throw new ValidationAppException("stageOrder: required when type is stage");
            }
            if (!normalizedKey.equals(request.stageKey().name())) {
                throw new ValidationAppException("key: stage group key must equal stageKey");
            }
            validateStageOrder(request.stageOrder(), "stageOrder");
        } else {
            if (request.stageKey() != null) {
                throw new ValidationAppException("stageKey: not allowed when type is bucket");
            }
            if (request.stageOrder() != null) {
                throw new ValidationAppException("stageOrder: not allowed when type is bucket");
            }
        }
    }

    public void validateLinkUpsert(
            DirectoryLinkUpsertRequest request,
            String pathScopeKey,
            String pathGroupKey,
            String targetScopeKey,
            String targetGroupKey) {
        if (request == null) {
            throw new ValidationAppException("request: body is required");
        }

        boolean hasTargetScope = targetScopeKey != null && !targetScopeKey.isBlank();
        boolean hasTargetGroup = targetGroupKey != null && !targetGroupKey.isBlank();
        if (hasTargetScope != hasTargetGroup) {
            throw new ValidationAppException(
                    "targetScopeKey: targetScopeKey and targetGroupKey must both be supplied or both omitted");
        }

        requireTitle(request.title(), "title");
        validateOptionalDescription(request.description(), "description");
        validateSortOrder(request.sortOrder(), "sortOrder");
        validateOptionalKindLabel(request.kindLabel(), "kindLabel");

        if (request.kind() == null) {
            throw new ValidationAppException("kind: link kind is required");
        }

        String url = requireUrl(request.url(), "url");
        validateUrl(url, request.kind());
        normalizeIconKey(request.iconKey());
    }

    public DirectoryLinkIconKey normalizeIconKey(String rawIconKey) {
        if (rawIconKey == null || rawIconKey.isBlank()) {
            return null;
        }
        String normalized = rawIconKey.trim().toLowerCase(Locale.ROOT);
        for (DirectoryLinkIconKey key : DirectoryLinkIconKey.values()) {
            if (key.name().equals(normalized)) {
                return key;
            }
        }
        throw new ValidationAppException("iconKey: unknown icon key: " + rawIconKey.trim());
    }

    public void validateScopeDelete(DirectoryScope scope) {
        if (scope == null) {
            throw new ValidationAppException("scope: scope is required");
        }
        if (scope.system()) {
            throw new ValidationAppException(
                    "scope: system scopes may be disabled or retitled but not deleted");
        }
    }

    public void validateFullCatalog(List<DirectoryScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new ValidationAppException("catalog: seed catalog must contain at least one scope");
        }

        Set<String> scopeKeys = new HashSet<>();
        int stageStripCount = 0;

        for (DirectoryScope scope : scopes) {
            if (!scopeKeys.add(scope.key())) {
                throw new ValidationAppException("key: duplicate scope key: " + scope.key());
            }

            DirectoryScopeUpsertRequest scopeRequest = new DirectoryScopeUpsertRequest(
                    scope.key(),
                    scope.title(),
                    scope.description(),
                    scope.layout(),
                    scope.enabled(),
                    scope.sortOrder());
            validateScopeUpsert(scopeRequest, scopes.stream().filter(s -> !s.key().equals(scope.key())).toList(), null);

            if (scope.layout() == DirectoryScopeLayout.STAGE_STRIP) {
                stageStripCount++;
            }

            Set<String> groupKeys = new HashSet<>();
            for (DirectoryGroup group : scope.groups()) {
                if (!groupKeys.add(group.key())) {
                    throw new ValidationAppException(
                            "key: duplicate group key in scope '" + scope.key() + "': " + group.key());
                }

                DirectoryGroupUpsertRequest groupRequest = new DirectoryGroupUpsertRequest(
                        group.key(),
                        group.title(),
                        group.description(),
                        group.type(),
                        group.stageKey(),
                        group.stageOrder(),
                        group.agentName(),
                        group.enabled(),
                        group.sortOrder());
                validateGroupUpsert(
                        groupRequest,
                        scope,
                        scope.groups().stream().filter(g -> !g.key().equals(group.key())).toList(),
                        null);

                for (DirectoryLink link : group.links()) {
                    if (link.id() == null || link.id().isBlank()) {
                        throw new ValidationAppException("id: link id is required");
                    }
                    DirectoryLinkUpsertRequest linkRequest = new DirectoryLinkUpsertRequest(
                            link.title(),
                            link.description(),
                            link.url(),
                            link.kind(),
                            link.kindLabel(),
                            link.iconKey() != null ? link.iconKey().name() : null,
                            link.enabled(),
                            link.sortOrder(),
                            null,
                            null);
                    validateLinkUpsert(linkRequest, scope.key(), group.key(), null, null);
                }
            }
        }

        if (stageStripCount > 1) {
            throw new ValidationAppException("layout: at most one scope may use stage-strip layout");
        }
    }

    public String normalizeKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        return rawKey.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyRequired(String rawKey, String field, boolean required) {
        if (rawKey == null || rawKey.isBlank()) {
            if (required) {
                throw new ValidationAppException(field + ": key is required");
            }
            throw new ValidationAppException(field + ": key is required");
        }
        String normalized = normalizeKey(rawKey);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationAppException(field + ": key must match pattern " + KEY_PATTERN.pattern());
        }
        return normalized;
    }

    private String requireTitle(String title, String field) {
        if (title == null || title.isBlank()) {
            throw new ValidationAppException(field + ": title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE) {
            throw new ValidationAppException(field + ": title must be at most " + MAX_TITLE + " characters");
        }
        return trimmed;
    }

    private void validateOptionalDescription(String description, String field) {
        if (description == null) {
            return;
        }
        if (description.trim().length() > MAX_DESCRIPTION) {
            throw new ValidationAppException(field + ": description must be at most " + MAX_DESCRIPTION + " characters");
        }
    }

    private void validateOptionalAgentName(String agentName, String field) {
        if (agentName == null) {
            return;
        }
        if (agentName.trim().length() > MAX_TITLE) {
            throw new ValidationAppException(field + ": agentName must be at most " + MAX_TITLE + " characters");
        }
    }

    private void validateOptionalKindLabel(String kindLabel, String field) {
        if (kindLabel == null) {
            return;
        }
        if (kindLabel.trim().length() > MAX_KIND_LABEL) {
            throw new ValidationAppException(field + ": kindLabel must be at most " + MAX_KIND_LABEL + " characters");
        }
    }

    private void validateSortOrder(Integer sortOrder, String field) {
        if (sortOrder == null) {
            return;
        }
        if (sortOrder < MIN_SORT_ORDER || sortOrder > MAX_SORT_ORDER) {
            throw new ValidationAppException(field + ": sortOrder must be between "
                    + MIN_SORT_ORDER + " and " + MAX_SORT_ORDER);
        }
    }

    private void validateStageOrder(Integer stageOrder, String field) {
        if (stageOrder == null) {
            return;
        }
        if (stageOrder < MIN_STAGE_ORDER || stageOrder > MAX_STAGE_ORDER) {
            throw new ValidationAppException(field + ": stageOrder must be between "
                    + MIN_STAGE_ORDER + " and " + MAX_STAGE_ORDER);
        }
    }

    private String requireUrl(String url, String field) {
        if (url == null || url.isBlank()) {
            throw new ValidationAppException(field + ": URL is required");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL) {
            throw new ValidationAppException(field + ": URL must be at most " + MAX_URL + " characters");
        }
        return trimmed;
    }

    private void validateUrl(String url, DirectoryLinkKind kind) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            throw new ValidationAppException("url: disallowed URL scheme");
        }
        if (url.startsWith("//")) {
            throw new ValidationAppException("url: protocol-relative URLs are not allowed");
        }

        if (kind == DirectoryLinkKind.workspace) {
            if (!WORKSPACE_URL.matcher(url).matches()) {
                throw new ValidationAppException("url: workspace links must be an in-Hub path starting with /wwa/");
            }
        } else if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ValidationAppException("url: link URL must use http or https scheme");
        }
    }
}
