package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.errors.UnauthorizedAppException;
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

    @Test
    @DisplayName("valid employee ID → returns UserContext with correct role")
    void authenticate_validEmployee_returnsContext() {
        UserContext ctx = authService.authenticate("emp-001", "any-password");
        assertThat(ctx.userId()).isEqualTo("emp-001");
        assertThat(ctx.role()).isEqualTo("DEVELOPER");
    }

    @Test
    @DisplayName("TL employee → returns TL role")
    void authenticate_tlEmployee_returnsTLRole() {
        UserContext ctx = authService.authenticate("emp-002", "any-password");
        assertThat(ctx.userId()).isEqualTo("emp-002");
        assertThat(ctx.role()).isEqualTo("TL");
    }

    @Test
    @DisplayName("DevOps admin → returns DEVOPS_ADMIN role")
    void authenticate_devopsAdmin_returnsDevOpsRole() {
        UserContext ctx = authService.authenticate("emp-003", "any-password");
        assertThat(ctx.role()).isEqualTo("DEVOPS_ADMIN");
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
}
