package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AuditLoggerService failure handling")
class AuditLoggerServiceFailureTest {

    @Autowired private AuditLoggerService auditLoggerService;

    @MockBean private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("log swallows repository failures so business flows can continue")
    void log_swallowsRepositoryFailure() {
        doThrow(new RuntimeException("invalid identifier TARGET_TYPE"))
                .when(auditLogRepository)
                .saveAndFlush(any(AuditLogEntry.class));

        assertThatCode(() -> auditLoggerService.log(
                new UserContext("archiver", "DEVOPS_ADMIN"),
                AuditActionType.request_archive,
                "rf-001",
                "req-001",
                null,
                Map.of("stage", "UAT")))
                .doesNotThrowAnyException();
    }
}
