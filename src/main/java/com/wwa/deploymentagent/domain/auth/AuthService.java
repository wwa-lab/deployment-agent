package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.errors.UnauthorizedAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AuthService – delegates authentication to the TeamBookAuthenticationProvider
 * and returns a UserContext on success.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TeamBookAuthenticationProvider authProvider;

    /**
     * Authenticate a user and return their context.
     *
     * @param employeeId employee identifier
     * @param password   password
     * @return UserContext with userId and role
     * @throws UnauthorizedAppException if authentication fails
     */
    public UserContext authenticate(String employeeId, String password) {
        TeamBookEmployee employee = authProvider.authenticate(employeeId, password)
                .orElseThrow(() -> new UnauthorizedAppException(
                        "Invalid employee ID or password"));

        return new UserContext(employee.employeeId(), employee.role());
    }

    /**
     * Look up display name for an authenticated employee.
     */
    public String getDisplayName(String employeeId) {
        // Re-use the provider to look up the name (stub accepts any password)
        return authProvider.authenticate(employeeId, "lookup")
                .map(TeamBookEmployee::displayName)
                .orElse(employeeId);
    }
}
