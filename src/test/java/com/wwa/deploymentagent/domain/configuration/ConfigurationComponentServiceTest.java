package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.ConfigurationComponentDto;
import com.wwa.deploymentagent.errors.ValidationAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ConfigurationComponentService")
class ConfigurationComponentServiceTest {

    @Autowired private ConfigurationComponentService configurationComponentService;
    @Autowired private ConfigurationComponentRepository configurationComponentRepository;

    private final UserContext adminUser = new UserContext("admin-user", "DEVOPS_ADMIN");

    @Test
    @DisplayName("resolveForSystem prefers the most specific matching scoped row")
    void resolveForSystem_prefersMostSpecificScope() {
        upsertJenkins(null, null, null, "http://default-jenkins:8080", "default-user", "default-token");
        upsertJenkins("AMH HCC", null, null, "http://app-jenkins:8080", "app-user", "app-token");
        upsertJenkins("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", null, "http://snow-jenkins:8080", "snow-user", "snow-token");
        upsertJenkins("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", "Deployment Agent", "http://agent-jenkins:8080", "agent-user", "agent-token");

        var resolvedAgent = configurationComponentService.resolveForSystem(
                "JENKINS",
                new ConfigurationScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", "Deployment Agent")
        );
        var resolvedSnow = configurationComponentService.resolveForSystem(
                "JENKINS",
                new ConfigurationScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", "Different Agent")
        );
        var resolvedApplication = configurationComponentService.resolveForSystem(
                "JENKINS",
                new ConfigurationScope("AMH HCC", "DIFFERENT-SNOW", null)
        );
        var resolvedDefault = configurationComponentService.resolveForSystem(
                "JENKINS",
                new ConfigurationScope("OTHER APP", null, null)
        );

        assertThat(resolvedAgent.endpoint()).isEqualTo("http://agent-jenkins:8080");
        assertThat(resolvedAgent.serviceUser()).isEqualTo("agent-user");
        assertThat(resolvedAgent.credential()).isEqualTo("agent-token");

        assertThat(resolvedSnow.endpoint()).isEqualTo("http://snow-jenkins:8080");
        assertThat(resolvedSnow.serviceUser()).isEqualTo("snow-user");
        assertThat(resolvedSnow.credential()).isEqualTo("snow-token");

        assertThat(resolvedApplication.endpoint()).isEqualTo("http://app-jenkins:8080");
        assertThat(resolvedApplication.serviceUser()).isEqualTo("app-user");
        assertThat(resolvedApplication.credential()).isEqualTo("app-token");

        assertThat(resolvedDefault.endpoint()).isEqualTo("http://default-jenkins:8080");
        assertThat(resolvedDefault.serviceUser()).isEqualTo("default-user");
        assertThat(resolvedDefault.credential()).isEqualTo("default-token");
    }

    @Test
    @DisplayName("upsertComponent rejects invalid scope hierarchy")
    void upsertComponent_rejectsInvalidScopeHierarchy() {
        assertThatThrownBy(() -> configurationComponentService.upsertComponent(
                new ConfigurationComponentDto.UpsertRequest(
                        null,
                        "jenkins",
                        "Jenkins Pipeline",
                        "CI/CD",
                        null,
                        "HTSA-CSI-HCC-AMH-PRJ",
                        null,
                        "http://jenkins.invalid",
                        "svc-user",
                        "svc-token",
                        null
                ),
                adminUser
        )).isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("SNOW Group scope requires Application");
    }

    @Test
    @DisplayName("list endpoints surface built-in defaults without persisting bootstrap rows")
    void listEndpoints_doNotPersistBootstrapRows() {
        assertThat(configurationComponentRepository.count()).isZero();

        var components = configurationComponentService.listComponents();
        var items = configurationComponentService.listDerivedConfigItems();

        assertThat(components)
                .extracting(ConfigurationComponent::getComponentId)
                .containsExactly("jenkins", "ansible", "callback");
        assertThat(items).hasSize(7);
        assertThat(configurationComponentRepository.count()).isZero();
    }

    @Test
    @DisplayName("deleteComponent removes the persisted scoped row and keeps built-in defaults available")
    void deleteComponent_removesPersistedScopedRow() {
        upsertJenkins("AMH HCC", null, null, "http://app-jenkins:8080", "app-user", "app-token");

        ConfigurationComponent persisted = configurationComponentRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        configurationComponentService.deleteComponent(persisted.getId(), adminUser);

        assertThat(configurationComponentRepository.findById(persisted.getId())).isEmpty();
        assertThat(configurationComponentService.listComponents())
                .extracting(ConfigurationComponent::getComponentId)
                .containsExactly("jenkins", "ansible", "callback");
    }

    private void upsertJenkins(
            String application,
            String snowGroup,
            String agent,
            String endpoint,
            String serviceUser,
            String credential
    ) {
        configurationComponentService.upsertComponent(
                new ConfigurationComponentDto.UpsertRequest(
                        null,
                        "jenkins",
                        "Jenkins Pipeline",
                        "CI/CD",
                        application,
                        snowGroup,
                        agent,
                        endpoint,
                        serviceUser,
                        credential,
                        null
                ),
                adminUser
        );
    }
}
