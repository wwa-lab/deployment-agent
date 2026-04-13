package com.wwa.agenthub.domain.auth;

import com.wwa.agenthub.contracts.AccessScope;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.AccessGrantStatus;
import com.wwa.agenthub.contracts.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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

    @Test
    @DisplayName("scoped admin list only returns grants within visible Application / SNOW Group scopes")
    void list_scopedAdminSeesOnlyMatchingScopes() {
        AccessGrant matchingGrant = new AccessGrant();
        matchingGrant.setEmployeeId("emp-scope-001");
        matchingGrant.setDisplayNameSnapshot("Scoped User");
        matchingGrant.setGrantStatus(AccessGrantStatus.ACTIVE);
        matchingGrant.setAssignedRoles(List.of(Role.TL.name()));
        matchingGrant.setScopeGrants(List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ")));
        matchingGrant.setCreatedBy("seed");
        matchingGrant.setUpdatedBy("seed");
        accessGrantRepository.save(matchingGrant);

        AccessGrant hiddenGrant = new AccessGrant();
        hiddenGrant.setEmployeeId("emp-scope-002");
        hiddenGrant.setDisplayNameSnapshot("Other Scoped User");
        hiddenGrant.setGrantStatus(AccessGrantStatus.ACTIVE);
        hiddenGrant.setAssignedRoles(List.of(Role.TL.name()));
        hiddenGrant.setScopeGrants(List.of(new AccessScope("PowerCARD", "HTSA-CSI-CARD-PRD")));
        hiddenGrant.setCreatedBy("seed");
        hiddenGrant.setUpdatedBy("seed");
        accessGrantRepository.saveAndFlush(hiddenGrant);

        UserContext scopedAdmin = new UserContext(
                "emp-admin-001",
                "DEVOPS_ADMIN",
                List.of("DEVOPS_ADMIN"),
                Set.of("access.manage"),
                "Scoped Admin",
                List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ"))
        );

        Page<AccessGrant> result = accessGrantService.list(
                null,
                AccessGrantStatus.ACTIVE,
                PageRequest.of(0, 20),
                scopedAdmin
        );

        assertThat(result.getContent()).extracting(AccessGrant::getEmployeeId)
                .contains("emp-scope-001")
                .doesNotContain("emp-scope-002");
    }
}
