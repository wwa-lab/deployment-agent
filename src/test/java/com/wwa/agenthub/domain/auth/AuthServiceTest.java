package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.errors.AccessNotGrantedAppException;
import com.wwa.agenthub.errors.AccessSuspendedAppException;
import com.wwa.agenthub.errors.UnauthorizedAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuthService")
class AuthServiceTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private AccessGrantRepository accessGrantRepository;

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
        AccessGrant grant = accessGrantRepository.findById("emp-005").orElseThrow();
        accessGrantRepository.deleteById("emp-005");
        accessGrantRepository.flush();

        try {
            assertThatThrownBy(() -> authService.authenticate("emp-005", "any-password"))
                    .isInstanceOf(AccessNotGrantedAppException.class)
                    .hasMessageContaining("Access not granted");
        } finally {
            accessGrantRepository.saveAndFlush(copyGrant(grant));
        }
    }

    @Test
    @DisplayName("suspended access grant → throws AccessSuspendedAppException")
    void authenticate_suspendedGrant_throwsForbidden() {
        AccessGrant grant = accessGrantRepository.findById("emp-004").orElseThrow();
        AccessGrantStatus originalStatus = grant.getGrantStatus();
        grant.setGrantStatus(AccessGrantStatus.SUSPENDED);
        accessGrantRepository.save(grant);

        try {
            assertThatThrownBy(() -> authService.authenticate("emp-004", "any-password"))
                    .isInstanceOf(AccessSuspendedAppException.class)
                    .hasMessageContaining("Access suspended");
        } finally {
            AccessGrant persistedGrant = accessGrantRepository.findById("emp-004").orElseThrow();
            persistedGrant.setGrantStatus(originalStatus);
            accessGrantRepository.save(persistedGrant);
        }
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

    private static AccessGrant copyGrant(AccessGrant source) {
        AccessGrant copy = new AccessGrant();
        copy.setEmployeeId(source.getEmployeeId());
        copy.setDisplayNameSnapshot(source.getDisplayNameSnapshot());
        copy.setGrantStatus(source.getGrantStatus());
        copy.setAssignedRoles(source.getAssignedRoles() == null ? null : java.util.List.copyOf(source.getAssignedRoles()));
        copy.setScopeGrants(source.getScopeGrants() == null ? null : java.util.List.copyOf(source.getScopeGrants()));
        copy.setNote(source.getNote());
        copy.setLastLoginAt(source.getLastLoginAt());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
