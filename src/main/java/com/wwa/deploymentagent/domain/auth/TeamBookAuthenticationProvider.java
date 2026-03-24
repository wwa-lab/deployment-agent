package com.wwa.deploymentagent.domain.auth;

import java.util.List;
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
}
