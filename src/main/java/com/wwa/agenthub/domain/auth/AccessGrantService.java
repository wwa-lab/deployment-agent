package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.Role;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.AccessNotGrantedAppException;
import com.wwa.agenthub.errors.AccessSuspendedAppException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccessGrantService {

    private final AccessGrantRepository accessGrantRepository;
    private final PermissionResolver permissionResolver;
    private final TeamBookAuthenticationProvider authProvider;
    private final AuditLoggerService auditLogger;
    private final Clock clock;

    public AccessGrantService(AccessGrantRepository accessGrantRepository,
                              PermissionResolver permissionResolver,
                              TeamBookAuthenticationProvider authProvider,
                              AuditLoggerService auditLogger,
                              Clock clock) {
        this.accessGrantRepository = accessGrantRepository;
        this.permissionResolver = permissionResolver;
        this.authProvider = authProvider;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AccessGrant> list(String query, AccessGrantStatus status, Pageable pageable, UserContext user) {
        String normalizedQuery = normalizeSearchQuery(query);
        List<AccessGrant> filtered = accessGrantRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt")).stream()
                .filter(grant -> status == null || grant.getGrantStatus() == status)
                .filter(grant -> normalizedQuery == null || matchesQuery(grant, normalizedQuery))
                .filter(grant -> canViewGrant(user, grant))
                .toList();

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }

        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
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

    @Transactional(readOnly = true)
    public List<AccessGrantDirectoryCandidate> searchDirectory(String query, int limit, UserContext user) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (normalizedQuery == null || limit <= 0) {
            return List.of();
        }

        List<TeamBookEmployee> employees = authProvider.searchEmployees(normalizedQuery, Math.min(limit, 20));
        if (employees.isEmpty()) {
            return List.of();
        }

        Map<String, AccessGrant> grantsById = accessGrantRepository.findAllById(
                        employees.stream()
                                .map(TeamBookEmployee::employeeId)
                                .toList())
                .stream()
                .collect(Collectors.toMap(AccessGrant::getEmployeeId, Function.identity()));

        return employees.stream()
                .map(employee -> toDirectoryCandidate(employee, grantsById.get(employee.employeeId()), user))
                .filter(candidate -> candidate != null)
                .toList();
    }

    @Transactional
    public UserContext authorizeAuthenticatedEmployee(TeamBookEmployee employee) {
        AccessGrant grant = accessGrantRepository.findById(employee.employeeId())
                .orElseThrow(AccessNotGrantedAppException::new);

        if (grant.getGrantStatus() == AccessGrantStatus.SUSPENDED) {
            throw new AccessSuspendedAppException();
        }

        List<String> roles = normalizeAuthorizedGrantRoles(grant.getAssignedRoles());
        if (roles.isEmpty()) {
            throw new AccessNotGrantedAppException();
        }

        grant.setDisplayNameSnapshot(employee.displayName());
        grant.setLastLoginAt(clock.instant());
        accessGrantRepository.save(grant);

        Set<String> permissions = permissionResolver.resolvePermissions(roles);
        return new UserContext(
                employee.employeeId(),
                roles.get(0),
                roles,
                permissions,
                employee.displayName(),
                List.copyOf(grant.getScopeGrants())
        );
    }

    /**
     * Rebuilds a session identity from the current grant so role, permission,
     * scope, and suspension changes take effect without waiting for logout.
     */
    @Transactional(readOnly = true)
    public UserContext refreshAuthorizedContext(String employeeId) {
        AccessGrant grant = accessGrantRepository.findById(employeeId)
                .orElseThrow(AccessNotGrantedAppException::new);
        if (grant.getGrantStatus() == AccessGrantStatus.SUSPENDED) {
            throw new AccessSuspendedAppException();
        }
        List<String> roles = normalizeAuthorizedGrantRoles(grant.getAssignedRoles());
        if (roles.isEmpty()) {
            throw new AccessNotGrantedAppException();
        }
        return new UserContext(
                grant.getEmployeeId(),
                roles.getFirst(),
                roles,
                permissionResolver.resolvePermissions(roles),
                grant.getDisplayNameSnapshot(),
                List.copyOf(grant.getScopeGrants()));
    }

    @Transactional
    public AccessGrant createGrant(String employeeId,
                                   String displayName,
                                   AccessGrantStatus grantStatus,
                                   Collection<String> assignedRoles,
                                   Collection<AccessScope> scopeGrants,
                                   String note,
                                   UserContext user) {
        String normalizedEmployeeId = normalizeEmployeeId(employeeId);
        if (accessGrantRepository.existsById(normalizedEmployeeId)) {
            throw new ConflictAppException("Access grant already exists for employee: " + normalizedEmployeeId);
        }

        Optional<TeamBookEmployee> employee = resolveTeamBookEmployeeForGrantCreation(normalizedEmployeeId);
        String resolvedDisplayName = employee
                .map(TeamBookEmployee::displayName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> normalizeManualDisplayName(displayName));

        List<String> normalizedRoles = normalizeRolesForMutation(assignedRoles);
        List<AccessScope> normalizedScopes = normalizeScopesForMutation(scopeGrants);
        validateActiveRoles(grantStatus, normalizedRoles, normalizedScopes);
        validateManagerScopeAccess(user, normalizedScopes);

        AccessGrant grant = new AccessGrant();
        grant.setEmployeeId(normalizedEmployeeId);
        grant.setDisplayNameSnapshot(resolvedDisplayName);
        grant.setGrantStatus(grantStatus);
        grant.setAssignedRoles(normalizedRoles);
        grant.setScopeGrants(normalizedScopes);
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
                                   Collection<AccessScope> scopeGrants,
                                   String note,
                                   UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        validateVisibleGrant(user, grant, "update_access_grant");

        List<String> previousRoles = List.copyOf(grant.getAssignedRoles());
        List<AccessScope> previousScopes = List.copyOf(grant.getScopeGrants());
        String previousNote = grant.getNote();

        if (assignedRoles != null) {
            grant.setAssignedRoles(normalizeRolesForMutation(assignedRoles));
        }
        if (scopeGrants != null) {
            grant.setScopeGrants(normalizeScopesForMutation(scopeGrants));
        }

        if (note != null) {
            grant.setNote(normalizeBlank(note));
        }

        validateActiveRoles(grant.getGrantStatus(), grant.getAssignedRoles(), grant.getScopeGrants());
        validateManagerScopeAccess(user, grant.getScopeGrants());
        refreshDisplayNameSnapshot(grant);
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_update,
                buildUpdateAuditContext(saved, previousRoles, previousScopes, previousNote));
        return saved;
    }

    @Transactional
    public AccessGrant suspendGrant(String employeeId, String note, UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        validateVisibleGrant(user, grant, "suspend_access_grant");

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
                                       Collection<AccessScope> scopeGrants,
                                       String note,
                                       UserContext user) {
        AccessGrant grant = accessGrantRepository.findById(normalizeEmployeeId(employeeId))
                .orElseThrow(() -> new NotFoundAppException("AccessGrant", employeeId));

        validateVisibleGrant(user, grant, "reactivate_access_grant");

        if (grant.getGrantStatus() == AccessGrantStatus.ACTIVE) {
            throw new ConflictAppException("Access grant is already active for employee: " + grant.getEmployeeId());
        }

        AccessGrantStatus previousStatus = grant.getGrantStatus();
        List<String> previousRoles = List.copyOf(grant.getAssignedRoles());
        List<AccessScope> previousScopes = List.copyOf(grant.getScopeGrants());
        String previousNote = grant.getNote();

        if (assignedRoles != null) {
            grant.setAssignedRoles(normalizeRolesForMutation(assignedRoles));
        }
        if (scopeGrants != null) {
            grant.setScopeGrants(normalizeScopesForMutation(scopeGrants));
        }
        validateActiveRoles(AccessGrantStatus.ACTIVE, grant.getAssignedRoles(), grant.getScopeGrants());
        validateManagerScopeAccess(user, grant.getScopeGrants());

        grant.setGrantStatus(AccessGrantStatus.ACTIVE);
        if (note != null) {
            grant.setNote(normalizeBlank(note));
        }
        refreshDisplayNameSnapshot(grant);
        grant.setUpdatedBy(user.userId());

        AccessGrant saved = accessGrantRepository.save(grant);
        auditLogger.log(user, AuditActionType.access_grant_reactivate,
                buildReactivateAuditContext(saved, previousStatus, previousRoles, previousScopes, previousNote));
        return saved;
    }

    @Transactional
    public AccessGrant ensureBootstrapGrant(String employeeId,
                                            String displayName,
                                            Collection<String> assignedRoles,
                                            Collection<AccessScope> scopeGrants,
                                            String note) {
        List<String> normalizedRoles = normalizeRolesForMutation(assignedRoles);
        List<AccessScope> normalizedScopes = normalizeScopesForMutation(scopeGrants);
        validateActiveRoles(AccessGrantStatus.ACTIVE, normalizedRoles, normalizedScopes);
        return accessGrantRepository.findById(employeeId)
                .orElseGet(() -> {
                    AccessGrant grant = new AccessGrant();
                    grant.setEmployeeId(employeeId);
                    grant.setDisplayNameSnapshot(displayName == null || displayName.isBlank() ? employeeId : displayName);
                    grant.setGrantStatus(AccessGrantStatus.ACTIVE);
                    grant.setAssignedRoles(normalizedRoles);
                    grant.setScopeGrants(normalizedScopes);
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

    private void validateActiveRoles(AccessGrantStatus grantStatus,
                                     Collection<String> roles,
                                     Collection<AccessScope> scopeGrants) {
        if (grantStatus == AccessGrantStatus.ACTIVE && (roles == null || roles.isEmpty())) {
            throw new ValidationAppException("Active access grants must have at least one assigned role.");
        }
        if (roles != null && roles.contains(Role.GUEST.name())) {
            throw new ValidationAppException(
                    "GUEST is reserved for the synthetic guest session and cannot be assigned to an access grant.");
        }
        boolean adminGrant = roles != null && roles.contains(Role.DEVOPS_ADMIN.name());
        boolean hasScopes = scopeGrants != null && !scopeGrants.isEmpty();
        if (grantStatus == AccessGrantStatus.ACTIVE && !adminGrant && !hasScopes) {
            throw new ValidationAppException(
                    "Active non-admin access grants must include at least one Application / SNOW Group scope.");
        }
    }

    private String normalizeEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new ValidationAppException("Employee ID is required.");
        }
        return employeeId.trim();
    }

    private Optional<TeamBookEmployee> resolveTeamBookEmployeeForGrantCreation(String normalizedEmployeeId) {
        return authProvider.findByEmployeeId(normalizedEmployeeId)
                .or(() -> authProvider.searchEmployees(normalizedEmployeeId, 20).stream()
                        .filter(candidate -> candidate.employeeId() != null
                                && normalizedEmployeeId.equalsIgnoreCase(candidate.employeeId().trim()))
                        .findFirst());
    }

    private String normalizeManualDisplayName(String displayName) {
        String normalizedDisplayName = normalizeBlank(displayName);
        if (normalizedDisplayName == null) {
            throw new ValidationAppException(
                    "Display name is required when employee ID cannot be resolved from Team Book.");
        }
        return normalizedDisplayName;
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesQuery(AccessGrant grant, String normalizedQuery) {
        return grant.getEmployeeId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || grant.getDisplayNameSnapshot().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AccessGrantDirectoryCandidate toDirectoryCandidate(TeamBookEmployee employee,
                                                               AccessGrant grant,
                                                               UserContext user) {
        if (grant != null && !canViewGrant(user, grant)) {
            return null;
        }
        return new AccessGrantDirectoryCandidate(
                employee.employeeId(),
                employee.displayName(),
                grant != null,
                grant == null ? null : grant.getGrantStatus()
        );
    }

    private List<String> normalizeRolesForMutation(Collection<String> rawRoles) {
        try {
            List<String> roles = normalizeRoles(rawRoles);
            if (roles.contains(Role.GUEST.name())) {
                throw new IllegalArgumentException(
                        "GUEST is reserved for the synthetic guest session.");
            }
            return roles;
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException("Invalid role assignment: " + ex.getMessage());
        }
    }

    private List<String> normalizeAuthorizedGrantRoles(Collection<String> rawRoles) {
        try {
            List<String> roles = normalizeRoles(rawRoles);
            if (roles.contains(Role.GUEST.name())) {
                throw new AccessNotGrantedAppException();
            }
            return roles;
        } catch (IllegalArgumentException exception) {
            throw new AccessNotGrantedAppException();
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

    private List<AccessScope> normalizeScopesForMutation(Collection<AccessScope> rawScopes) {
        try {
            return normalizeScopes(rawScopes);
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException("Invalid scope assignment: " + ex.getMessage());
        }
    }

    private List<AccessScope> normalizeScopes(Collection<AccessScope> rawScopes) {
        if (rawScopes == null) {
            return List.of();
        }

        LinkedHashSet<AccessScope> normalizedScopes = new LinkedHashSet<>();
        for (AccessScope rawScope : rawScopes) {
            if (rawScope == null || rawScope.isEmpty()) {
                continue;
            }

            AccessScope normalizedScope = new AccessScope(rawScope.application(), rawScope.snowGroup());
            if (normalizedScope.application() == null || normalizedScope.snowGroup() == null) {
                throw new IllegalArgumentException(
                        "Both application and SNOW Group are required for a scoped access grant.");
            }
            normalizedScopes.add(normalizedScope);
        }
        return List.copyOf(normalizedScopes);
    }

    private Map<String, Object> buildCreateAuditContext(AccessGrant saved) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("grantStatus", saved.getGrantStatus().name());
        context.put("assignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("scopeGrants", List.copyOf(saved.getScopeGrants()));
        if (saved.getNote() != null) {
            context.put("note", saved.getNote());
        }
        return context;
    }

    private Map<String, Object> buildUpdateAuditContext(AccessGrant saved,
                                                        List<String> previousRoles,
                                                        List<AccessScope> previousScopes,
                                                        String previousNote) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("grantStatus", saved.getGrantStatus().name());
        context.put("oldAssignedRoles", previousRoles);
        context.put("newAssignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("oldScopeGrants", previousScopes);
        context.put("newScopeGrants", List.copyOf(saved.getScopeGrants()));
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
        context.put("scopeGrants", List.copyOf(saved.getScopeGrants()));
        context.put("oldNote", previousNote);
        context.put("newNote", saved.getNote());
        return context;
    }

    private Map<String, Object> buildReactivateAuditContext(AccessGrant saved,
                                                            AccessGrantStatus previousStatus,
                                                            List<String> previousRoles,
                                                            List<AccessScope> previousScopes,
                                                            String previousNote) {
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("employeeId", saved.getEmployeeId());
        context.put("displayName", saved.getDisplayNameSnapshot());
        context.put("oldGrantStatus", previousStatus.name());
        context.put("newGrantStatus", saved.getGrantStatus().name());
        context.put("oldAssignedRoles", previousRoles);
        context.put("newAssignedRoles", List.copyOf(saved.getAssignedRoles()));
        context.put("oldScopeGrants", previousScopes);
        context.put("newScopeGrants", List.copyOf(saved.getScopeGrants()));
        context.put("oldNote", previousNote);
        context.put("newNote", saved.getNote());
        return context;
    }

    private boolean canViewGrant(UserContext user, AccessGrant grant) {
        if (user == null) {
            return false;
        }
        if (user.isGlobalDevOpsAdmin()) {
            return true;
        }
        if (!user.hasRole(Role.DEVOPS_ADMIN.name())) {
            return false;
        }
        return grant.getScopeGrants().stream()
                .anyMatch(scope -> user.hasScopedAccess(scope.application(), scope.snowGroup()));
    }

    private void validateVisibleGrant(UserContext user, AccessGrant grant, String action) {
        if (!canViewGrant(user, grant)) {
            throw new ValidationAppException("You do not have scoped access to " + action + " for this employee.");
        }
    }

    private void validateManagerScopeAccess(UserContext user, List<AccessScope> targetScopes) {
        if (user == null) {
            throw new ValidationAppException("Authenticated user is required for scoped access management.");
        }
        if (user.isGlobalDevOpsAdmin()) {
            return;
        }
        if (!user.canManageScopes(targetScopes)) {
            throw new ValidationAppException(
                    "The requested scopes are outside the current admin's Application / SNOW Group visibility.");
        }
    }
}
