package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.AccessScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccessGrantBootstrapRunner implements ApplicationRunner {

    private final AccessGrantService accessGrantService;
    private final TeamBookAuthenticationProvider authProvider;

    @Value("${app.auth.bootstrap-admin-ids:}")
    private String bootstrapAdminIds;

    @Override
    public void run(ApplicationArguments args) {
        seedKnownEmployees();
        seedConfiguredAdmins();
    }

    private void seedKnownEmployees() {
        List<TeamBookEmployee> knownEmployees = authProvider.listKnownEmployees();
        if (knownEmployees.isEmpty()) {
            return;
        }

        for (TeamBookEmployee employee : knownEmployees) {
            accessGrantService.ensureBootstrapGrant(
                    employee.employeeId(),
                    employee.displayName(),
                    List.of(employee.role()),
                    List.of(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD)),
                    "Bootstrap grant for known non-production employee"
            );
        }
        log.info("Bootstrapped {} access grants for known employees", knownEmployees.size());
    }

    private void seedConfiguredAdmins() {
        if (bootstrapAdminIds == null || bootstrapAdminIds.isBlank()) {
            return;
        }

        Arrays.stream(bootstrapAdminIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .forEach(employeeId -> {
                    String displayName = authProvider.findByEmployeeId(employeeId)
                            .map(TeamBookEmployee::displayName)
                            .orElse(employeeId);
                    accessGrantService.ensureBootstrapGrant(
                            employeeId,
                            displayName,
                            List.of("DEVOPS_ADMIN"),
                            List.of(),
                            "Bootstrap admin grant from app.auth.bootstrap-admin-ids"
                    );
                });
    }
}
