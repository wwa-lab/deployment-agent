package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.Role;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ConflictAppException;
import com.wwa.deploymentagent.errors.AccessNotGrantedAppException;
import com.wwa.deploymentagent.errors.AccessSuspendedAppException;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AccessGrantService {

    private final AccessGrantRepository accessGrantRepository;
    private final PermissionResolver permissionResolver;
    private final TeamBookAuthenticationProvider authProvider;
    private final AuditLoggerService auditLogger;

    public AccessGrantService(AccessGrantRepository accessGrantRepository,
                              PermissionResolver permissionResolver,
                              TeamBookAuthenticationProvider authProvider,
                              AuditLoggerService auditLogger) {
        this.accessGrantRepository = accessGrantRepository;
        this.permissionResolver = permissionResolver;
        this.authProvider = authProvider;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public Page<AccessGrant> list(String query, AccessGrantStatus status, Pageable pageable) {
        String normalizedQuery = normalizeSearchQuery(query);
        List<AccessGrant> filtered = accessGrantRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(grant -> status == null || grant.getGrantStatus() == status)
                .filter(grant -> normalizedQuery == null || matchesQuery(grant, normalizedQuery))
                .toList();

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }

        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional
    public UserContext authorizeAuthenticatedEmployee(TeamBookEmployee employee) {
        AccessGrant grant = accessGrantRepository.findById(employee.employeeId())
                .orElseThrow(AccessNotGrantedAppException::new);

        if (grant.getGrantStatus() == AccessGrantStatus.SUSPENDED) {
            throw new AccessSuspendedAppException();
        }

        List<String> roles = normalizeRoles(grant.getAssignedRoles());
        if (roles.isEmpty()) {
            throw new AccessNotGrantedAppException();
        }

        grant.setDisplayNameSnapshot(employee.displayName());
        grant.setLastLoginAt(Instant.now());
        accessGrantRepository.save(grant);

        Set<String> permissions = permissionResolver.resolvePermissions(roles);
        return new UserContext(
                employee.employeeId(),
                roles.get(0),
                roles,
                permissions,
                employee.displayName()
        );
    }

    @Transactional
    public AccessGrant createGrant(String employeeId,
                                   AccessGrantStatus grantStatus,
                                   Collection<String> assignedRoles,
                                   String note,
                                   UserContext user) {
        String normalizedEmployeeId = normalizeEmployeeId(employeeId);
        if (accessGrantRepository.existsById(normalizedEmployeeId)) {
            throw new ConflictAppException("Access grant already exists for employee: " + normalizedEmployeeId);
        }

        TeamBookEmployee employee = authProvider.findByEmployeeId(normalizedEmployeeId)
                .orElseThrow(() -> new ValidationAppException("Unknown employee ID: " + normalizedEmployeeId));

        List<String> normalizedRoles = normalizeRolesForMutation(assignedRoles);
        validateActiveRoles(grantStatus, normalizedRoles);

        AccessGrant grant = new AccessGrant();
        grant.setEmployeeId(normalizedEmployeeId);
        grant.setDisplayNameSnapshot(employee.displayName());
        grant.setGrantStatus(grantStatus);
        grant.setAssignedRoles(normalizedRoles);
        grant.setNote(normalizeBlank(note));
        grant.setCreatedBy(user.userId());
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_create, buildCreateAuditContext(saved));
        return saved;
    }

    @Transactional
    public AccessGrant updateGrant(String employeeId,
                                   Collection<String> assignedRoles,
                                   String note,
                                   UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        List<String> previousRoles = List.copyOf(grant.getAssignedRoles());
        String previousNote = grant.getNote();

        if (assignedRoles != null) {
            grant.setAssignedRoles(normalizeRolesForMutation(assignedRoles));
        }

        if (note != null) {
            grant.setNote(normalizeBlank(note));
        }

        validateActiveRoles(grant.getGrantStatus(), grant.getAssignedRoles());
        refreshDisplayNameSnapshot(grant);
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_update,
                buildUpdateAuditContext(saved, previousRoles, previousNote));
        return saved;
    }

    @Transactional
    public AccessGrant suspendGrant(String employeeId, String note, UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        if (grant.getGrantStatus() == AccessGrantStatus.SUSPENDED) {
            throw new ConflictAppException("Access grant is already suspended for employee: " + grant.getEmployeeId());
        }

        AccessGrantStatus previousStatus = grant.getGrantStatus();
        String previousNote = grant.getNote();
        grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
        if (note != null) {
            grant.setNote(normalizeBlank(note));
        }
        refreshDisplayNameSnapshot(grant);
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_suspend,
                buildStatusAuditContext(saved, previousStatus, previousNote));
        return saved;
    }

    @Transactional
    public AccessGrant reactivateGrant(String employeeId,
                                       Collection<String> assignedRoles,
                                       String note,
                                       UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        if (grant.getGrantStatus() == AccessGrantStatus.ACTIVE) {
            throw new ConflictAppException("Access grant is already active for employee: " + grant.getEmployeeId());
        }

        AccessGrantStatus previousStatus = grant.getGrantStatus();
        List<String> previousRoles = List.copyOf(grant.getAssignedRoles());
        String previousNote = grant.getNote();

        if (assignedRoles != null) {
            grant.setAssignedRoles(normalizeRolesForMutation(assignedRoles));
        }
        validateActiveRoles(AccessGrantStatus.ACTIVE, grant.getAssignedRoles());

        grant.setGrantStatus(AccessGrantStatus.ACTIVE);
        if (note != null) {
            grant.setNote(normalizeBlank(note));
        }
        refreshDisplayNameSnapshot(grant);
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_reactivate,
                buildReactivateAuditContext(saved, previousStatus, previousRoles, previousNote));
        return saved;
    }

    @Transactional
    public AccessGrant ensureBootstrapGrant(String employeeId,
                                            String displayName,
                                            Collection<String> assignedRoles,
                                            String note) {
        return accessGrantRepository.findById(employeeId)
                .orElseGet(() -> {
                    AccessGrant grant = new AccessGrant();
                    grant.setEmployeeId(employeeId);
                    grant.setDisplayNameSnapshot(displayName == null || displayName.isBlank() ? employeeId : displayName);
                    grant.setGrantStatus(AccessGrantStatus.ACTIVE);
                    grant.setAssignedRoles(normalizeRoles(assignedRoles));
                    grant.setNote(note);
                    grant.setCreatedBy("system");
                    grant.setUpdatedBy("system");
                    return accessGrantRepository.save(grant);
                });
    }

    private void refreshDisplayNameSnapshot(AccessGrant grant) {
        authProvider.findByEmployeeId(grant.getEmployeeId())
                .map(TeamBookEmployee::displayName)
                .filter(name -> !name.isBlank())
                .ifPresent(grant::setDisplayNameSnapshot);
    }

    private void validateActiveRoles(AccessGrantStatus grantStatus, Collection<String> roles) {
        if (grantStatus == AccessGrantStatus.ACTIVE && (roles == null || roles.isEmpty())) {
            throw new ValidationAppException("Active access grants must have at least one assigned role.");
        }
    }

    private String normalizeEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new ValidationAppException("Employee ID is required.");
        }
        return employeeId.trim();
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase();
    }

    private boolean matchesQuery(AccessGrant grant, String normalizedQuery) {
        return grant.getEmployeeId().toLowerCase().contains(normalizedQuery)
                || grant.getDisplayNameSnapshot().toLowerCase().contains(normalizedQuery);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> normalizeRolesForMutation(Collection<String> rawRoles) {
        try {
            return normalizeRoles(rawRoles);
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException("Invalid role assignment: " + ex.getMessage());
        }
    }

    private List<String> normalizeRoles(Collection<String> rawRoles) {
        if (rawRoles == null) {
            return List.of();
        }

        LinkedHashSet<String> normalizedRoles = new LinkedHashSet<>();
        for (String rawRole : rawRoles) {
            if (rawRole == null || rawRole.isBlank()) {
                continue;
            }
            normalizedRoles.add(Role.valueOf(rawRole.trim()).name());
        }
        return List.copyOf(normalizedRoles);
    }

    private Map<String, Object> buildCreateAuditContext(AccessGrant saved) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("grantStatus", saved.getGrantStatus().name());
        context.put("assignedRoles", List.copyOf(saved.getAssignedRoles()));
        if (saved.getNote() != null) {
            context.put("note", saved.getNote());
        }
        return context;
    }

    private Map<String, Object> buildUpdateAuditContext(AccessGrant saved,
                                                        List<String> previousRoles,
                                                        String previousNote) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("grantStatus", saved.getGrantStatus().name());
        context.put("oldAssignedRoles", previousRoles);
        context.put("newAssignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("oldNote", previousNote);
        context.put("newNote", saved.getNote());
        return context;
    }

    private Map<String, Object> buildStatusAuditContext(AccessGrant saved,
                                                        AccessGrantStatus previousStatus,
                                                        String previousNote) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("oldGrantStatus", previousStatus.name());
        context.put("newGrantStatus", saved.getGrantStatus().name());
        context.put("assignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("oldNote", previousNote);
        context.put("newNote", saved.getNote());
        return context;
    }

    private Map<String, Object> buildReactivateAuditContext(AccessGrant saved,
                                                            AccessGrantStatus previousStatus,
                                                            List<String> previousRoles,
                                                            String previousNote) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("oldGrantStatus", previousStatus.name());
        context.put("newGrantStatus", saved.getGrantStatus().name());
        context.put("oldAssignedRoles", previousRoles);
        context.put("newAssignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("oldNote", previousNote);
        context.put("newNote", saved.getNote());
        return context;
    }
}
