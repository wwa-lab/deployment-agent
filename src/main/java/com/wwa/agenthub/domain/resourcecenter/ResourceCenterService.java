package com.wwa.agenthub.domain.resourcecenter;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.DirectoryGroupUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryLinkUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryScopeUpsertRequest;
import com.wwa.agenthub.contracts.dto.ResourceCenterCatalogDto;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryGroup;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryLink;
import com.wwa.agenthub.domain.resourcecenter.model.DirectoryScope;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.OptimisticLockConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceCenterService {

    private static final String AUDIT_TARGET_TYPE = "SERVICE_DIRECTORY";

    private final ResourceCenterCatalogRepository repository;
    private final ResourceCenterSeedLoader seedLoader;
    private final ResourceCenterValidator validator;
    private final AuditLoggerService auditLogger;

    @Transactional
    public ResourceCenterCatalogDto read(boolean includeDisabled, UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        boolean showDisabled = includeDisabled && user != null && user.hasRole("DEVOPS_ADMIN");
        return toDto(entity, projectCatalog(entity.getPayload(), showDisabled));
    }

    @Transactional
    public ResourceCenterCatalogDto createScope(DirectoryScopeUpsertRequest request, UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        List<DirectoryScope> scopes = mutablePayload(entity);

        validator.validateScopeUpsert(request, scopes, null);

        String key = validator.normalizeKey(request.key());
        DirectoryScope created = new DirectoryScope(
                key,
                request.title().trim(),
                trimDescription(request.description()),
                request.layout(),
                false,
                request.enabled() == null || request.enabled(),
                resolveSortOrder(request.sortOrder(), scopes.stream().map(DirectoryScope::sortOrder).toList()),
                new ArrayList<>());

        scopes.add(created);
        sortScopes(scopes);
        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("scope", key, created.title(), "create", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto updateScope(
            String scopeKey,
            long expectedVersion,
            DirectoryScopeUpsertRequest request,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope existing = findScope(scopes, scopeKey);
        validator.validateScopeUpsert(request, scopes, scopeKey);

        DirectoryScope updated = new DirectoryScope(
                existing.key(),
                request.title().trim(),
                trimDescription(request.description()),
                request.layout(),
                existing.system(),
                request.enabled() == null ? existing.enabled() : request.enabled(),
                request.sortOrder() != null ? request.sortOrder() : existing.sortOrder(),
                existing.groups());

        replaceScope(scopes, updated);
        sortScopes(scopes);
        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("scope", updated.key(), updated.title(), "update", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto deleteScope(String scopeKey, long expectedVersion, UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope existing = findScope(scopes, scopeKey);
        validator.validateScopeDelete(existing);

        int removedGroups = existing.groups().size();
        int removedLinks = countLinks(existing.groups());

        scopes.removeIf(scope -> scope.key().equals(scopeKey));
        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        Map<String, Object> context = auditContext(
                "scope", scopeKey, existing.title(), "delete", removedGroups, removedLinks, null, null, null, null);
        auditDelete(user, context);
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto createGroup(
            String scopeKey,
            DirectoryGroupUpsertRequest request,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope parentScope = findScope(scopes, scopeKey);
        validator.validateGroupUpsert(request, parentScope, parentScope.groups(), null);

        String key = validator.normalizeKey(request.key());
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : resolveSortOrder(null, parentScope.groups().stream().map(DirectoryGroup::sortOrder).toList());
        DirectoryGroup created = buildGroupFromRequest(request, key, sortOrder, new ArrayList<>());

        List<DirectoryGroup> groups = new ArrayList<>(parentScope.groups());
        groups.add(created);
        sortGroups(groups);
        replaceScope(scopes, parentScope.withGroups(groups));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("group", key, created.title(), "create", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto updateGroup(
            String scopeKey,
            String groupKey,
            long expectedVersion,
            DirectoryGroupUpsertRequest request,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope parentScope = findScope(scopes, scopeKey);
        DirectoryGroup existing = findGroup(parentScope, groupKey);
        validator.validateGroupUpsert(request, parentScope, parentScope.groups(), groupKey);

        DirectoryGroup updated = buildGroupFromRequest(
                request,
                existing.key(),
                request.sortOrder() != null ? request.sortOrder() : existing.sortOrder(),
                existing.links(),
                request.enabled() != null ? request.enabled() : existing.enabled());

        List<DirectoryGroup> groups = new ArrayList<>(parentScope.groups());
        replaceGroup(groups, updated);
        sortGroups(groups);
        replaceScope(scopes, parentScope.withGroups(groups));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("group", updated.key(), updated.title(), "update", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto deleteGroup(
            String scopeKey,
            String groupKey,
            long expectedVersion,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope parentScope = findScope(scopes, scopeKey);
        DirectoryGroup existing = findGroup(parentScope, groupKey);
        int removedLinks = existing.links().size();

        List<DirectoryGroup> groups = new ArrayList<>(parentScope.groups());
        groups.removeIf(group -> group.key().equals(groupKey));
        replaceScope(scopes, parentScope.withGroups(groups));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        Map<String, Object> context = auditContext(
                "group", groupKey, existing.title(), "delete", null, removedLinks, null, null, null, null);
        auditDelete(user, context);
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto createLink(
            String scopeKey,
            String groupKey,
            DirectoryLinkUpsertRequest request,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        List<DirectoryScope> scopes = mutablePayload(entity);

        DirectoryScope parentScope = findScope(scopes, scopeKey);
        DirectoryGroup parentGroup = findGroup(parentScope, groupKey);
        validator.validateLinkUpsert(request, scopeKey, groupKey, null, null);

        String linkId = UUID.randomUUID().toString();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : resolveSortOrder(null, parentGroup.links().stream().map(DirectoryLink::sortOrder).toList());
        DirectoryLink created = buildLinkFromRequest(
                request,
                linkId,
                sortOrder,
                request.enabled() == null || request.enabled());

        List<DirectoryLink> links = new ArrayList<>(parentGroup.links());
        links.add(created);
        sortLinks(links);
        replaceGroupInScope(scopes, parentScope, parentGroup.withLinks(links));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("link", linkId, created.title(), "create", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto updateLink(
            String linkId,
            long expectedVersion,
            DirectoryLinkUpsertRequest request,
            UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        LinkLocation location = findLinkLocation(scopes, linkId);
        validator.validateLinkUpsert(
                request,
                location.scopeKey(),
                location.groupKey(),
                request.targetScopeKey(),
                request.targetGroupKey());

        DirectoryLink updated = buildLinkFromRequest(
                request,
                linkId,
                request.sortOrder() != null ? request.sortOrder() : location.link().sortOrder(),
                request.enabled() != null ? request.enabled() : location.link().enabled());

        String targetScopeKey = blankToNull(request.targetScopeKey());
        String targetGroupKey = blankToNull(request.targetGroupKey());
        if (targetScopeKey != null) {
            targetScopeKey = validator.normalizeKey(targetScopeKey);
            targetGroupKey = validator.normalizeKey(targetGroupKey);
        }
        boolean moving = targetScopeKey != null;

        if (moving) {
            DirectoryScope fromScope = findScope(scopes, location.scopeKey());
            DirectoryGroup fromGroup = findGroup(fromScope, location.groupKey());
            DirectoryScope toScope = findScope(scopes, targetScopeKey);
            DirectoryGroup toGroup = findGroup(toScope, targetGroupKey);

            List<DirectoryLink> fromLinks = new ArrayList<>(fromGroup.links());
            fromLinks.removeIf(link -> link.id().equals(linkId));
            replaceGroupInScope(scopes, fromScope, fromGroup.withLinks(fromLinks));

            DirectoryScope refreshedToScope = findScope(scopes, targetScopeKey);
            DirectoryGroup refreshedToGroup = findGroup(refreshedToScope, targetGroupKey);
            List<DirectoryLink> toLinks = new ArrayList<>(refreshedToGroup.links());
            toLinks.add(updated);
            sortLinks(toLinks);
            replaceGroupInScope(scopes, refreshedToScope, refreshedToGroup.withLinks(toLinks));

            entity.setPayload(scopes);
            entity.setUpdatedBy(user.userId());
            ResourceCenterCatalogEntity saved = repository.save(entity);

            Map<String, Object> context = auditContext(
                    "link",
                    linkId,
                    updated.title(),
                    "update",
                    null,
                    null,
                    location.scopeKey(),
                    location.groupKey(),
                    targetScopeKey,
                    targetGroupKey);
            auditUpdate(user, context);
            return toDto(saved, projectCatalog(saved.getPayload(), true));
        }

        DirectoryScope scope = findScope(scopes, location.scopeKey());
        DirectoryGroup group = findGroup(scope, location.groupKey());
        List<DirectoryLink> links = new ArrayList<>(group.links());
        replaceLink(links, updated);
        sortLinks(links);
        replaceGroupInScope(scopes, scope, group.withLinks(links));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        auditUpdate(user, auditContext("link", linkId, updated.title(), "update", null, null, null, null, null, null));
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    @Transactional
    public ResourceCenterCatalogDto deleteLink(String linkId, long expectedVersion, UserContext user) {
        ResourceCenterCatalogEntity entity = loadOrSeed();
        assertExpectedVersion(entity, expectedVersion);
        List<DirectoryScope> scopes = mutablePayload(entity);

        LinkLocation location = findLinkLocation(scopes, linkId);
        DirectoryScope scope = findScope(scopes, location.scopeKey());
        DirectoryGroup group = findGroup(scope, location.groupKey());

        List<DirectoryLink> links = new ArrayList<>(group.links());
        links.removeIf(link -> link.id().equals(linkId));
        replaceGroupInScope(scopes, scope, group.withLinks(links));

        entity.setPayload(scopes);
        entity.setUpdatedBy(user.userId());
        ResourceCenterCatalogEntity saved = repository.save(entity);

        Map<String, Object> context = auditContext(
                "link", linkId, location.link().title(), "delete", null, null, null, null, null, null);
        auditDelete(user, context);
        return toDto(saved, projectCatalog(saved.getPayload(), true));
    }

    private ResourceCenterCatalogEntity loadOrSeed() {
        return repository.findFirstByOrderByIdAsc().orElseGet(this::seedCatalog);
    }

    private ResourceCenterCatalogEntity seedCatalog() {
        List<DirectoryScope> seed = seedLoader.loadAndValidate();
        ResourceCenterCatalogEntity entity = new ResourceCenterCatalogEntity();
        entity.setPayload(new ArrayList<>(seed));
        entity.setUpdatedBy(null);
        return repository.save(entity);
    }

    private void assertExpectedVersion(ResourceCenterCatalogEntity entity, long expectedVersion) {
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            throw new OptimisticLockConflictException("Resource Center catalog");
        }
    }

    private List<DirectoryScope> mutablePayload(ResourceCenterCatalogEntity entity) {
        return new ArrayList<>(entity.getPayload());
    }

    private ResourceCenterCatalogDto toDto(ResourceCenterCatalogEntity entity, List<DirectoryScope> projected) {
        return ResourceCenterCatalogDto.from(entity, projected);
    }

    private List<DirectoryScope> projectCatalog(List<DirectoryScope> scopes, boolean includeDisabled) {
        return scopes.stream()
                .filter(scope -> includeDisabled || scope.enabled())
                .sorted(scopeComparator())
                .map(scope -> projectScope(scope, includeDisabled))
                .toList();
    }

    private DirectoryScope projectScope(DirectoryScope scope, boolean includeDisabled) {
        List<DirectoryGroup> groups = scope.groups().stream()
                .filter(group -> includeDisabled || group.enabled())
                .sorted(groupComparator())
                .map(group -> projectGroup(group, includeDisabled))
                .toList();
        return scope.withGroups(groups);
    }

    private DirectoryGroup projectGroup(DirectoryGroup group, boolean includeDisabled) {
        List<DirectoryLink> links = group.links().stream()
                .filter(link -> includeDisabled || link.enabled())
                .sorted(linkComparator())
                .toList();
        return group.withLinks(links);
    }

    private DirectoryScope findScope(List<DirectoryScope> scopes, String scopeKey) {
        return scopes.stream()
                .filter(scope -> scope.key().equals(scopeKey))
                .findFirst()
                .orElseThrow(() -> new NotFoundAppException("Service directory scope", scopeKey));
    }

    private DirectoryGroup findGroup(DirectoryScope scope, String groupKey) {
        return scope.groups().stream()
                .filter(group -> group.key().equals(groupKey))
                .findFirst()
                .orElseThrow(() -> new NotFoundAppException("Service directory group", groupKey));
    }

    private LinkLocation findLinkLocation(List<DirectoryScope> scopes, String linkId) {
        for (DirectoryScope scope : scopes) {
            for (DirectoryGroup group : scope.groups()) {
                for (DirectoryLink link : group.links()) {
                    if (link.id().equals(linkId)) {
                        return new LinkLocation(scope.key(), group.key(), link);
                    }
                }
            }
        }
        throw new NotFoundAppException("Service directory link", linkId);
    }

    private DirectoryGroup buildGroupFromRequest(
            DirectoryGroupUpsertRequest request,
            String key,
            int sortOrder,
            List<DirectoryLink> links) {
        return buildGroupFromRequest(request, key, sortOrder, links, request.enabled() == null || request.enabled());
    }

    private DirectoryGroup buildGroupFromRequest(
            DirectoryGroupUpsertRequest request,
            String key,
            int sortOrder,
            List<DirectoryLink> links,
            boolean enabled) {
        return new DirectoryGroup(
                key,
                request.title().trim(),
                trimDescription(request.description()),
                request.type(),
                request.stageKey(),
                request.stageOrder(),
                request.agentName() == null ? "" : request.agentName().trim(),
                enabled,
                sortOrder,
                new ArrayList<>(links));
    }

    private DirectoryLink buildLinkFromRequest(
            DirectoryLinkUpsertRequest request,
            String linkId,
            int sortOrder,
            boolean enabled) {
        return new DirectoryLink(
                linkId,
                request.title().trim(),
                trimDescription(request.description()),
                request.url().trim(),
                request.kind(),
                request.kindLabel(),
                validator.normalizeIconKey(request.iconKey()),
                enabled,
                sortOrder);
    }

    private int resolveSortOrder(Integer requested, List<Integer> siblingOrders) {
        if (requested != null) {
            return requested;
        }
        int max = siblingOrders.stream().mapToInt(Integer::intValue).max().orElse(0);
        return Math.min(max + 10, 9999);
    }

    private int countLinks(List<DirectoryGroup> groups) {
        return groups.stream().mapToInt(group -> group.links().size()).sum();
    }

    private void replaceScope(List<DirectoryScope> scopes, DirectoryScope updated) {
        for (int i = 0; i < scopes.size(); i++) {
            if (scopes.get(i).key().equals(updated.key())) {
                scopes.set(i, updated);
                return;
            }
        }
    }

    private void replaceGroup(List<DirectoryGroup> groups, DirectoryGroup updated) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).key().equals(updated.key())) {
                groups.set(i, updated);
                return;
            }
        }
    }

    private void replaceLink(List<DirectoryLink> links, DirectoryLink updated) {
        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).id().equals(updated.id())) {
                links.set(i, updated);
                return;
            }
        }
    }

    private void replaceGroupInScope(
            List<DirectoryScope> scopes,
            DirectoryScope scope,
            DirectoryGroup updatedGroup) {
        List<DirectoryGroup> groups = new ArrayList<>(scope.groups());
        replaceGroup(groups, updatedGroup);
        replaceScope(scopes, scope.withGroups(groups));
    }

    private void sortScopes(List<DirectoryScope> scopes) {
        scopes.sort(scopeComparator());
    }

    private void sortGroups(List<DirectoryGroup> groups) {
        groups.sort(groupComparator());
    }

    private void sortLinks(List<DirectoryLink> links) {
        links.sort(linkComparator());
    }

    private Comparator<DirectoryScope> scopeComparator() {
        return Comparator.comparingInt(DirectoryScope::sortOrder)
                .thenComparing(DirectoryScope::key);
    }

    private Comparator<DirectoryGroup> groupComparator() {
        return Comparator.comparingInt(DirectoryGroup::sortOrder)
                .thenComparing(DirectoryGroup::key);
    }

    private Comparator<DirectoryLink> linkComparator() {
        return Comparator.comparingInt(DirectoryLink::sortOrder)
                .thenComparing(DirectoryLink::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DirectoryLink::id);
    }

    private String trimDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void auditUpdate(UserContext user, Map<String, Object> context) {
        auditLogger.log(user, AuditActionType.resource_center_update, context);
    }

    private void auditDelete(UserContext user, Map<String, Object> context) {
        auditLogger.log(user, AuditActionType.resource_center_delete, context);
    }

    private Map<String, Object> auditContext(
            String entity,
            String entityKey,
            String entityTitle,
            String operation,
            Integer removedGroups,
            Integer removedLinks,
            String fromScopeKey,
            String fromGroupKey,
            String toScopeKey,
            String toGroupKey) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("target_type", AUDIT_TARGET_TYPE);
        context.put("entity", entity);
        context.put("entity_key", entityKey);
        context.put("entity_title", entityTitle);
        context.put("operation", operation);
        if (removedGroups != null) {
            context.put("removed_groups", removedGroups);
        }
        if (removedLinks != null) {
            context.put("removed_links", removedLinks);
        }
        if (toGroupKey != null) {
            context.put("from_scope_key", fromScopeKey);
            context.put("from_group_key", fromGroupKey);
            context.put("to_scope_key", toScopeKey);
            context.put("to_group_key", toGroupKey);
        }
        return context;
    }

    private record LinkLocation(String scopeKey, String groupKey, DirectoryLink link) {}
}
