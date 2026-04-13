package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AccessGrantService directory fallback")
class AccessGrantServiceDirectoryFallbackTest {

    @TestConfiguration
    static class DirectoryOnlyProviderConfig {

        private static final TeamBookEmployee LEO = new TeamBookEmployee("43910516", "Leo L Zhang", "DEVELOPER");

        @Bean
        @Primary
        TeamBookAuthenticationProvider teamBookAuthenticationProvider() {
            return new TeamBookAuthenticationProvider() {
                @Override
                public Optional<TeamBookEmployee> authenticate(String employeeId, String password) {
                    if (employeeId == null || password == null || password.isBlank()) {
                        return Optional.empty();
                    }
                    return employeeId.trim().equals(LEO.employeeId()) ? Optional.of(LEO) : Optional.empty();
                }

                @Override
                public Optional<TeamBookEmployee> findByEmployeeId(String employeeId) {
                    // Simulate a provider that supports directory search but not direct ID lookup.
                    return Optional.empty();
                }

                @Override
                public List<TeamBookEmployee> searchEmployees(String query, int limit) {
                    if (query == null || query.isBlank() || limit <= 0) {
                        return List.of();
                    }

                    String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
                    if (LEO.employeeId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                            || LEO.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                        return List.of(LEO);
                    }
                    return List.of();
                }

                @Override
                public List<TeamBookEmployee> listKnownEmployees() {
                    return List.of();
                }
            };
        }
    }

    @Autowired
    private AccessGrantService accessGrantService;

    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @Test
    @DisplayName("createGrant falls back to directory search when findByEmployeeId returns empty")
    void createGrant_usesDirectoryFallbackLookup() {
        UserContext admin = new UserContext("emp-003", "DEVOPS_ADMIN");

        AccessGrant created = accessGrantService.createGrant(
                "43910516",
                null,
                AccessGrantStatus.ACTIVE,
                List.of("DEVOPS_ADMIN"),
                List.of(),
                "Created through directory fallback",
                admin
        );

        assertThat(created.getEmployeeId()).isEqualTo("43910516");
        assertThat(created.getDisplayNameSnapshot()).isEqualTo("Leo L Zhang");
        assertThat(accessGrantRepository.findById("43910516")).isPresent();
    }

    @Test
    @DisplayName("createGrant allows manual employee creation when Team Book has no match and displayName is provided")
    void createGrant_allowsManualCreationWhenTeamBookMisses() {
        UserContext admin = new UserContext("emp-003", "DEVOPS_ADMIN");

        AccessGrant created = accessGrantService.createGrant(
                "43910156",
                "Leo L Zhang",
                AccessGrantStatus.ACTIVE,
                List.of("DEVOPS_ADMIN"),
                List.of(),
                "Manual access grant bootstrap",
                admin
        );

        assertThat(created.getEmployeeId()).isEqualTo("43910156");
        assertThat(created.getDisplayNameSnapshot()).isEqualTo("Leo L Zhang");
        assertThat(accessGrantRepository.findById("43910156")).isPresent();
    }
}
