package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.ScopeDirectoryEntryDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ValidationAppException;
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
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional
    public ScopeDirectoryEntry upsert(ScopeDirectoryEntryDto.UpsertRequest request, UserContext user) {
        if (user == null) {
            throw new ValidationAppException("Authenticated user is required.");
        }

        ConfigurationScope scope = normalizeScope(request.application(), request.snowGroup());

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
                                    + (scope.snowGroup() != null ? " / '" + scope.snowGroup() + "'" : ""));
                });

        entry.setScopeKey(scope.scopeKey());
        entry.setApplication(scope.application());
        entry.setSnowGroup(scope.snowGroup());
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

    private static ConfigurationScope normalizeScope(String application, String snowGroup) {
        try {
            ConfigurationScope scope = new ConfigurationScope(application, snowGroup, null);
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
        context.put("agent", "platform");
        context.put("scopeDirectoryEntryId", entry.getId());
        context.put("scopeSource", new ConfigurationScope(entry.getApplication(), entry.getSnowGroup(), null).scopeSource());
        context.put("changedFields", java.util.List.of(changedField));
        return context;
    }
}
