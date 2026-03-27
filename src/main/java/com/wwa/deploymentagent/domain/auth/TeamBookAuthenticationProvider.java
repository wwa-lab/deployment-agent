package com.wwa.deploymentagent.domain.auth;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interface for authenticating users against the company team book.
 * Implementations may call an external API or use a stub for development.
 */
public interface TeamBookAuthenticationProvider {

    /**
     * Authenticate an employee by ID and password.
     *
     * @param employeeId employee identifier
     * @param password   password
     * @return employee details if authentication succeeds, empty otherwise
     */
    Optional<TeamBookEmployee> authenticate(String employeeId, String password);

    default Optional<TeamBookEmployee> findByEmployeeId(String employeeId) {
        return Optional.empty();
    }

    default List<TeamBookEmployee> listKnownEmployees() {
        return List.of();
    }

    default List<TeamBookEmployee> searchEmployees(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        return listKnownEmployees().stream()
                .filter(employee -> employee.employeeId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || employee.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(TeamBookEmployee::displayName))
                .limit(limit)
                .toList();
    }
}
