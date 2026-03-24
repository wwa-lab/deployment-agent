package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.contracts.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AccessGrantService")
class AccessGrantServiceTest {

    @Autowired
    private AccessGrantService accessGrantService;

    @Autowired
    private AccessGrantRepository accessGrantRepository;

    @Test
    @DisplayName("list filters grants by employee ID query and status")
    void list_filtersByEmployeeIdAndStatus() {
        AccessGrant grant = new AccessGrant();
        grant.setEmployeeId("emp-list-001");
        grant.setDisplayNameSnapshot("Seeded Access User");
        grant.setGrantStatus(AccessGrantStatus.ACTIVE);
        grant.setAssignedRoles(List.of(Role.DEVOPS_ADMIN.name()));
        grant.setCreatedBy("seed");
        grant.setUpdatedBy("seed");
        accessGrantRepository.saveAndFlush(grant);

        Page<AccessGrant> result = accessGrantService.list(
                "emp-list-001",
                AccessGrantStatus.ACTIVE,
                PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(AccessGrant::getEmployeeId)
                .containsExactly("emp-list-001");
    }
}
