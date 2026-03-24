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
    private final AccessGrantService accessGrantService;

    /**
     * Authenticate a user and return their context.
     *
     * @param employeeId employee identifier
     * @param password   password
     * @return UserContext with product access roles and permissions
     * @throws UnauthorizedAppException if authentication fails
     */
    public UserContext authenticate(String employeeId, String password) {
        TeamBookEmployee employee = authProvider.authenticate(employeeId, password)
                .orElseThrow(() -> new UnauthorizedAppException(
                        "Invalid employee ID or password"));

        return accessGrantService.authorizeAuthenticatedEmployee(employee);
    }

    /**
     * Look up display name for an authenticated employee.
     */
    public String getDisplayName(String employeeId) {
        return authProvider.findByEmployeeId(employeeId)
                .map(TeamBookEmployee::displayName)
                .orElse(employeeId);
    }
}
