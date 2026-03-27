package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.errors.ValidationAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ConfigurationService")
class ConfigurationServiceTest {

    @Autowired private ConfigurationService configurationService;
    @Autowired private ConfigurationRepository configurationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private UserContext adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new UserContext("admin-user", "DEVOPS_ADMIN");
    }

    @Test
    @DisplayName("upsert creates a new jenkins_url entry")
    void upsert_jenkinsUrl_createsEntry() {
        ConfigurationItem item = configurationService.upsert(
                ConfigKey.jenkins_url, "http://jenkins.example.com", null, adminUser);

        assertThat(item.getConfigKey()).isEqualTo(ConfigKey.jenkins_url);
        assertThat(item.getConfigValue()).isEqualTo("http://jenkins.example.com");
        assertThat(item.getUpdatedBy()).isEqualTo("admin-user");
    }

    @Test
    @DisplayName("upsert updates an existing entry")
    void upsert_updatesExistingEntry() {
        configurationService.upsert(ConfigKey.jenkins_url, "http://jenkins.example.com", null, adminUser);
        ConfigurationItem updated = configurationService.upsert(
                ConfigKey.jenkins_url, "https://jenkins-v2.example.com", "new desc", adminUser);

        assertThat(updated.getConfigValue()).isEqualTo("https://jenkins-v2.example.com");
        assertThat(updated.getDescription()).isEqualTo("new desc");
    }

    @Test
    @DisplayName("upsert throws ValidationAppException for invalid jenkins_url format")
    void upsert_invalidUrl_throws() {
        assertThatThrownBy(() ->
                configurationService.upsert(ConfigKey.jenkins_url, "not-a-url", null, adminUser))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("jenkins_url");
    }

    @Test
    @DisplayName("execution_callback_endpoint requires HTTPS")
    void upsert_callbackEndpoint_requiresHttps() {
        assertThatThrownBy(() ->
                configurationService.upsert(
                        ConfigKey.execution_callback_endpoint, "http://insecure.example.com", null, adminUser))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    @DisplayName("execution_callback_endpoint accepts HTTPS URL")
    void upsert_callbackEndpoint_httpsAccepted() {
        ConfigurationItem item = configurationService.upsert(
                ConfigKey.execution_callback_endpoint, "https://callback.example.com", null, adminUser);

        assertThat(item.getConfigValue()).isEqualTo("https://callback.example.com");
    }

    @Test
    @DisplayName("listAll returns all configuration items")
    void listAll_returnsAll() {
        configurationService.upsert(ConfigKey.jenkins_url, "http://jenkins.example.com", null, adminUser);
        configurationService.upsert(ConfigKey.ansible_url, "http://ansible.example.com", null, adminUser);

        List<ConfigurationItem> items = configurationService.listAll();

        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("getByKey returns existing item")
    void getByKey_existingKey_returnsItem() {
        configurationService.upsert(ConfigKey.ansible_url, "http://ansible.example.com", null, adminUser);

        Optional<ConfigurationItem> result = configurationService.getByKey(ConfigKey.ansible_url);

        assertThat(result).isPresent();
        assertThat(result.get().getConfigValue()).isEqualTo("http://ansible.example.com");
    }

    @Test
    @DisplayName("getByKey returns empty for missing key")
    void getByKey_missing_returnsEmpty() {
        Optional<ConfigurationItem> result = configurationService.getByKey(ConfigKey.jenkins_url);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sensitive values are encrypted at rest and excluded from audit payload")
    void upsert_sensitiveValue_encryptsAndRedactsAudit() {
        configurationService.upsert(ConfigKey.jenkins_api_token, "top-secret-token", null, adminUser);

        ConfigurationItem stored = configurationRepository.findById(ConfigKey.jenkins_api_token).orElseThrow();

        assertThat(stored.getConfigValue()).startsWith("enc:v1:");
        assertThat(stored.getConfigValue()).isNotEqualTo("top-secret-token");
        assertThat(configurationService.getDecryptedValue(ConfigKey.jenkins_api_token))
                .contains("top-secret-token");

        var auditEntries = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getActionType() == AuditActionType.config_update)
                .toList();
        assertThat(auditEntries).isNotEmpty();
        assertThat(auditEntries.getLast().getContextPayload())
                .doesNotContainValue("top-secret-token")
                .containsEntry("credentialChanged", true);
    }
}
