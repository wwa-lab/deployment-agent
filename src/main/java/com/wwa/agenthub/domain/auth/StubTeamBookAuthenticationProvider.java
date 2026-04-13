package com.wwa.agenthub.domain.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stub implementation of TeamBookAuthenticationProvider for dev/test profiles.
 * Accepts any non-empty password for hardcoded employee IDs.
 */
@Component
@Profile({"dev", "test", "default","local"})
public class StubTeamBookAuthenticationProvider implements TeamBookAuthenticationProvider {

    private static final List<TeamBookEmployee> BOOTSTRAP_EMPLOYEES = List.of(
            new TeamBookEmployee("emp-001", "Alice Park (Developer)", "DEVELOPER"),
            new TeamBookEmployee("emp-002", "Bob Kim (Tech Lead)", "TL"),
            new TeamBookEmployee("emp-003", "Carol Lee (DevOps Admin)", "DEVOPS_ADMIN"),
            new TeamBookEmployee("emp-004", "David Cho (Auditor)", "AUDIT"),
            new TeamBookEmployee("emp-005", "Eve Yoon (Management)", "MANAGEMENT")
    );

    private static final List<TeamBookEmployee> DIRECTORY_EMPLOYEES = List.of(
            BOOTSTRAP_EMPLOYEES.get(0),
            BOOTSTRAP_EMPLOYEES.get(1),
            BOOTSTRAP_EMPLOYEES.get(2),
            BOOTSTRAP_EMPLOYEES.get(3),
            BOOTSTRAP_EMPLOYEES.get(4),
            new TeamBookEmployee("emp-006", "Frank Han (Developer)", "DEVELOPER"),
            new TeamBookEmployee("emp-007", "Grace Lin (Tech Lead)", "TL"),
            new TeamBookEmployee("emp-008", "Henry Seo (Auditor)", "AUDIT")
    );

    private static final Map<String, TeamBookEmployee> DIRECTORY_EMPLOYEE_MAP = DIRECTORY_EMPLOYEES.stream()
            .collect(Collectors.toMap(TeamBookEmployee::employeeId, Function.identity()));

    @Override
    public Optional<TeamBookEmployee> authenticate(String employeeId, String password) {
        if (employeeId == null || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DIRECTORY_EMPLOYEE_MAP.get(employeeId));
    }

    @Override
    public Optional<TeamBookEmployee> findByEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DIRECTORY_EMPLOYEE_MAP.get(employeeId));
    }

    @Override
    public List<TeamBookEmployee> listKnownEmployees() {
        return BOOTSTRAP_EMPLOYEES;
    }

    @Override
    public List<TeamBookEmployee> searchEmployees(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        return DIRECTORY_EMPLOYEES.stream()
                .filter(employee -> employee.employeeId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || employee.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(TeamBookEmployee::displayName))
                .limit(limit)
                .toList();
    }
}
