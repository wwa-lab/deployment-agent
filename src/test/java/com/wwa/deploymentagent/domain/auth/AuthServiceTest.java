package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.AccessScope;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.errors.AccessNotGrantedAppException;
import com.wwa.deploymentagent.errors.AccessSuspendedAppException;
import com.wwa.deploymentagent.errors.UnauthorizedAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthService")
class AuthServiceTest {

    private static final List<TestGrantSeed> KNOWN_GRANTS = List.of(
            new TestGrantSeed("emp-001", "Alice Park (Developer)", "DEVELOPER"),
            new TestGrantSeed("emp-002", "Bob Kim (Tech Lead)", "TL"),
            new TestGrantSeed("emp-003", "Carol Lee (DevOps Admin)", "DEVOPS_ADMIN"),
            new TestGrantSeed("emp-004", "David Cho (Auditor)", "AUDIT"),
            new TestGrantSeed("emp-005", "Eve Yoon (Management)", "MANAGEMENT")
    );

    @Autowired
    private AuthService authService;
    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @BeforeEach
    void resetKnownAccessGrants() {
        accessGrantRepository.deleteAll();
        accessGrantRepository.saveAll(KNOWN_GRANTS.stream()
                .map(AuthServiceTest::toAccessGrant)
                .toList());
    }

    @Test
    @DisplayName("valid employee ID → returns UserContext with roles and permissions")
    void authenticate_validEmployee_returnsContext() {
        UserContext ctx = authService.authenticate("emp-001", "any-password");
        assertThat(ctx.userId()).isEqualTo("emp-001");
        assertThat(ctx.role()).isEqualTo("DEVELOPER");
        assertThat(ctx.roles()).containsExactly("DEVELOPER");
        assertThat(ctx.permissions()).contains("release.upload", "release.view");
        assertThat(ctx.displayName()).contains("Alice Park");
        assertThat(ctx.scopes()).containsExactly(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD));
    }

    @Test
    @DisplayName("TL employee → returns TL role and workflow permissions")
    void authenticate_tlEmployee_returnsTLRole() {
        UserContext ctx = authService.authenticate("emp-002", "any-password");
        assertThat(ctx.userId()).isEqualTo("emp-002");
        assertThat(ctx.role()).isEqualTo("TL");
        assertThat(ctx.roles()).containsExactly("TL");
        assertThat(ctx.permissions()).contains("task.edit", "task.run", "task.review");
    }

    @Test
    @DisplayName("DevOps admin → returns DEVOPS_ADMIN role")
    void authenticate_devopsAdmin_returnsDevOpsRole() {
        UserContext ctx = authService.authenticate("emp-003", "any-password");
        assertThat(ctx.role()).isEqualTo("DEVOPS_ADMIN");
        assertThat(ctx.scopes()).containsExactly(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD));
    }

    @Test
    @DisplayName("Auditor → returns AUDIT role")
    void authenticate_auditor_returnsAuditRole() {
        UserContext ctx = authService.authenticate("emp-004", "any-password");
        assertThat(ctx.role()).isEqualTo("AUDIT");
    }

    @Test
    @DisplayName("Management → returns MANAGEMENT role")
    void authenticate_management_returnsManagementRole() {
        UserContext ctx = authService.authenticate("emp-005", "any-password");
        assertThat(ctx.role()).isEqualTo("MANAGEMENT");
        // WWA-007: MANAGEMENT now also receives platform-level permission keys alongside the legacy audit.view key
        assertThat(ctx.permissions()).contains("audit.view", "platform.enter", "platform.audit.view");
    }

    @Test
    @DisplayName("employee without access grant → throws AccessNotGrantedAppException")
    void authenticate_missingAccessGrant_throwsForbidden() {
        accessGrantRepository.deleteById("emp-005");

        assertThatThrownBy(() -> authService.authenticate("emp-005", "any-password"))
                .isInstanceOf(AccessNotGrantedAppException.class)
                .hasMessageContaining("Access not granted");
    }

    @Test
    @DisplayName("suspended access grant → throws AccessSuspendedAppException")
    void authenticate_suspendedGrant_throwsForbidden() {
        AccessGrant grant = accessGrantRepository.findById("emp-004").orElseThrow();
        grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
        accessGrantRepository.save(grant);

        assertThatThrownBy(() -> authService.authenticate("emp-004", "any-password"))
                .isInstanceOf(AccessSuspendedAppException.class)
                .hasMessageContaining("Access suspended");
    }

    @Test
    @DisplayName("unknown employee → throws UnauthorizedAppException")
    void authenticate_unknownEmployee_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.authenticate("unknown", "password"))
                .isInstanceOf(UnauthorizedAppException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("blank password → throws UnauthorizedAppException")
    void authenticate_blankPassword_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.authenticate("emp-001", ""))
                .isInstanceOf(UnauthorizedAppException.class);
    }

    @Test
    @DisplayName("null password → throws UnauthorizedAppException")
    void authenticate_nullPassword_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.authenticate("emp-001", null))
                .isInstanceOf(UnauthorizedAppException.class);
    }

    private static AccessGrant toAccessGrant(TestGrantSeed seed) {
        AccessGrant grant = new AccessGrant();
        grant.setEmployeeId(seed.employeeId());
        grant.setDisplayNameSnapshot(seed.displayName());
        grant.setGrantStatus(AccessGrantStatus.ACTIVE);
        grant.setAssignedRoles(List.of(seed.role()));
        grant.setScopeGrants(List.of(new AccessScope(AccessScope.WILDCARD, AccessScope.WILDCARD)));
        grant.setCreatedBy("test");
        grant.setUpdatedBy("test");
        return grant;
    }

    private record TestGrantSeed(String employeeId, String displayName, String role) {
    }
}
