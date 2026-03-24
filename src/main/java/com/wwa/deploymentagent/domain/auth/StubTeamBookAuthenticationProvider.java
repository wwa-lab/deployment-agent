package com.wwa.deploymentagent.domain.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stub implementation of TeamBookAuthenticationProvider for dev/test profiles.
 * Accepts any non-empty password for hardcoded employee IDs.
 */
@Component
@Profile({"dev", "test", "default","local"})
public class StubTeamBookAuthenticationProvider implements TeamBookAuthenticationProvider {

    private static final Map<String, TeamBookEmployee> EMPLOYEES = Map.of(
            "emp-001", new TeamBookEmployee("emp-001", "Alice Park (Developer)", "DEVELOPER"),
            "emp-002", new TeamBookEmployee("emp-002", "Bob Kim (Tech Lead)", "TL"),
            "emp-003", new TeamBookEmployee("emp-003", "Carol Lee (DevOps Admin)", "DEVOPS_ADMIN"),
            "emp-004", new TeamBookEmployee("emp-004", "David Cho (Auditor)", "AUDIT"),
            "emp-005", new TeamBookEmployee("emp-005", "Eve Yoon (Management)", "MANAGEMENT")
    );

    @Override
    public Optional<TeamBookEmployee> authenticate(String employeeId, String password) {
        if (employeeId == null || password == null || password.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(EMPLOYEES.get(employeeId));
    }

    @Override
    public Optional<TeamBookEmployee> findByEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(EMPLOYEES.get(employeeId));
    }

    @Override
    public List<TeamBookEmployee> listKnownEmployees() {
        return EMPLOYEES.values().stream().toList();
    }
}
