package com.wwa.agenthub.domain.configuration;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.ScopeDirectoryEntryDto;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScopeDirectoryService {

    private final ScopeDirectoryRepository scopeDirectoryRepository;
    private final AuditLoggerService auditLogger;

    @Transactional(readOnly = true)
    public List<ScopeDirectoryEntry> listEntries() {
        return scopeDirectoryRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(ScopeDirectoryEntry::getApplication, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                ScopeDirectoryEntry::getSnowGroup,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(
                                ScopeDirectoryEntry::getAgent,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional
    public ScopeDirectoryEntry upsert(ScopeDirectoryEntryDto.UpsertRequest request, UserContext user) {
        if (user == null) {
            throw new ValidationAppException("Authenticated user is required.");
        }

        ConfigurationScope scope = normalizeScope(request.application(), request.snowGroup(), request.agent());

        ScopeDirectoryEntry entry = request.id() == null || request.id().isBlank()
                ? new ScopeDirectoryEntry()
                : scopeDirectoryRepository.findById(request.id().trim())
                        .orElseThrow(() -> new ValidationAppException(
                                "Scope directory entry not found: '" + request.id() + "'"));

        scopeDirectoryRepository.findByScopeKey(scope.scopeKey())
                .filter(existing -> !existing.getId().equals(entry.getId()))
                .ifPresent(existing -> {
                    throw new ValidationAppException(
                            "Scope directory entry already exists for '"
                                    + scope.application()
                                    + "'"
                                    + (scope.snowGroup() != null ? " / '" + scope.snowGroup() + "'" : "")
                                    + (scope.agent() != null ? " / '" + scope.agent() + "'" : ""));
                });

        entry.setScopeKey(scope.scopeKey());
        entry.setApplication(scope.application());
        entry.setSnowGroup(scope.snowGroup());
        entry.setAgent(scope.agent());
        entry.setUpdatedBy(user.userId());
        ScopeDirectoryEntry saved = scopeDirectoryRepository.save(entry);

        auditLogger.log(user, AuditActionType.config_update, auditContext(saved, "scope_directory"));
        return saved;
    }

    @Transactional
    public void delete(String id, UserContext user) {
        ScopeDirectoryEntry entry = scopeDirectoryRepository.findById(id)
                .orElseThrow(() -> new ValidationAppException("Scope directory entry not found: '" + id + "'"));
        scopeDirectoryRepository.delete(entry);
        auditLogger.log(user, AuditActionType.config_delete, auditContext(entry, "scope_directory"));
    }

    private static ConfigurationScope normalizeScope(String application, String snowGroup, String agent) {
        try {
            ConfigurationScope scope = new ConfigurationScope(application, snowGroup, agent);
            scope.validateHierarchy();
            if (scope.application() == null) {
                throw new ValidationAppException("Application is required.");
            }
            return scope;
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException(ex.getMessage());
        }
    }

    private static java.util.Map<String, Object> auditContext(ScopeDirectoryEntry entry, String changedField) {
        java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("application", entry.getApplication());
        context.put("snowGroup", entry.getSnowGroup() != null ? entry.getSnowGroup() : "");
        context.put("agent", entry.getAgent() != null ? entry.getAgent() : "platform");
        context.put("scopeDirectoryEntryId", entry.getId());
        context.put("scopeSource", new ConfigurationScope(
                entry.getApplication(),
                entry.getSnowGroup(),
                entry.getAgent()).scopeSource());
        context.put("changedFields", java.util.List.of(changedField));
        return context;
    }
}
