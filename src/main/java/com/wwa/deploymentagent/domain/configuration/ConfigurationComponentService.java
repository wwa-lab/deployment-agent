package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.ConfigurationComponentDto;
import com.wwa.deploymentagent.contracts.dto.ConfigurationItemDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Source of truth for built-in configuration components and their derived raw rows.
 */
@Service
@RequiredArgsConstructor
public class ConfigurationComponentService {

    private static final String DEFAULT_SCOPE_SOURCE = "Platform Default";
    private static final String MASKED_SECRET = "••••••••";

    private final ConfigurationComponentRepository componentRepository;
    private final ConfigurationService legacyConfigurationService;
    private final SensitiveValueCipher sensitiveValueCipher;
    private final AuditLoggerService auditLogger;

    @Transactional
    public List<ConfigurationComponent> listComponents() {
        ensureBuiltInComponents();
        return BuiltInComponentDefinition.ordered(componentRepository.findAll());
    }

    @Transactional
    public ConfigurationComponent upsertComponent(ConfigurationComponentDto.UpsertRequest request, UserContext user) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.require(request.componentId());
        ensureBuiltInComponents();

        ConfigurationComponent component = componentRepository.findById(definition.componentId())
                .orElseThrow(() -> new ValidationAppException("Unknown configuration component: '" + request.componentId() + "'"));

        if (definition.tracksServiceUser() && (request.serviceUser() == null || request.serviceUser().isBlank())) {
            throw new ValidationAppException("Service user is required for component '" + definition.componentId() + "'");
        }
        if (definition.tracksCredential()
                && (component.getCredentialValue() == null || component.getCredentialValue().isBlank())
                && (request.credentialValue() == null || request.credentialValue().isBlank())) {
            throw new ValidationAppException("Credential is required for component '" + definition.componentId() + "'");
        }

        Map<String, Object> auditContext = baseAuditContext(component, definition);
        List<String> changedFields = new ArrayList<>();

        changedFieldsIfDifferent(changedFields, "displayName", component.getDisplayName(), request.displayName());
        changedFieldsIfDifferent(changedFields, "area", component.getArea(), request.area());
        changedFieldsIfDifferent(changedFields, "application", component.getApplication(), request.application());
        changedFieldsIfDifferent(changedFields, "snowGroup", component.getSnowGroup(), request.snowGroup());
        changedFieldsIfDifferent(changedFields, "agent", component.getAgent(), request.agent());
        changedFieldsIfDifferent(changedFields, "serviceEndpoint", component.getServiceEndpoint(), request.serviceEndpoint());
        changedFieldsIfDifferent(changedFields, "serviceUser", component.getServiceUser(), request.serviceUser());
        changedFieldsIfDifferent(changedFields, "description", component.getDescription(), request.description());

        component.setDisplayName(request.displayName().trim());
        component.setArea(request.area().trim());
        component.setApplication(request.application().trim());
        component.setSnowGroup(request.snowGroup().trim());
        component.setAgent(request.agent().trim());
        component.setServiceEndpoint(request.serviceEndpoint().trim());
        component.setServiceUser(definition.tracksServiceUser() ? trimToNull(request.serviceUser()) : null);
        component.setDescription(trimToNull(request.description()));
        component.setUpdatedBy(user.userId());

        boolean credentialChanged = request.credentialValue() != null && !request.credentialValue().isBlank();
        if (credentialChanged) {
            component.setCredentialValue(sensitiveValueCipher.encrypt(request.credentialValue().trim()));
        }

        ConfigurationComponent saved = componentRepository.save(component);

        if (!changedFields.isEmpty()) {
            auditContext.put("changedFields", changedFields);
        }
        auditContext.put("credentialChanged", credentialChanged);
        auditLogger.log(user, AuditActionType.config_update, auditContext);

        return saved;
    }

    @Transactional
    public List<ConfigurationItemDto> listDerivedConfigItems() {
        return listComponents().stream()
                .flatMap(component -> toDerivedItems(component).stream())
                .toList();
    }

    @Transactional
    public ConfigurationItemDto upsertDerivedConfigItem(ConfigurationItemDto.UpsertRequest request, UserContext user) {
        BuiltInComponentDefinition definition = request.componentId() != null && !request.componentId().isBlank()
                ? BuiltInComponentDefinition.require(request.componentId())
                : BuiltInComponentDefinition.forKey(request.key());
        ensureBuiltInComponents();

        ConfigurationComponent component = componentRepository.findById(definition.componentId())
                .orElseThrow(() -> new ValidationAppException("Unknown configuration component: '" + definition.componentId() + "'"));

        if (!definition.supports(request.key())) {
            throw new ValidationAppException("Config key '" + request.key() + "' does not belong to component '" + definition.componentId() + "'");
        }

        String trimmedValue = request.value().trim();
        if (trimmedValue.isEmpty()) {
            throw new ValidationAppException("Configuration value must not be blank");
        }

        Map<String, Object> auditContext = baseAuditContext(component, definition);
        auditContext.put("configKey", request.key().name());
        auditContext.put("changedFields", List.of(request.key().name()));

        if (request.key() == definition.endpointKey()) {
            component.setServiceEndpoint(trimmedValue);
            if (request.description() != null) {
                component.setDescription(trimToNull(request.description()));
            }
        } else if (Objects.equals(request.key(), definition.userKey())) {
            component.setServiceUser(trimmedValue);
        } else if (Objects.equals(request.key(), definition.secretKey())) {
            component.setCredentialValue(sensitiveValueCipher.encrypt(trimmedValue));
            auditContext.put("credentialChanged", true);
        } else {
            throw new ValidationAppException("Unsupported config key update: '" + request.key() + "'");
        }

        component.setUpdatedBy(user.userId());
        ConfigurationComponent saved = componentRepository.save(component);
        auditLogger.log(user, AuditActionType.config_update, auditContext);
        return toDerivedItems(saved).stream()
                .filter(item -> item.configKey() == request.key())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to derive updated config item"));
    }

    @Transactional
    public ResolvedSystemConfiguration resolveForSystem(String systemType) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.forSystemType(systemType);
        ensureBuiltInComponents();

        Optional<ConfigurationComponent> componentOptional = componentRepository.findById(definition.componentId());
        if (componentOptional.isPresent()) {
            ConfigurationComponent component = componentOptional.get();
            if (component.getServiceEndpoint() != null && !component.getServiceEndpoint().isBlank()) {
                return new ResolvedSystemConfiguration(
                        component.getServiceEndpoint(),
                        component.getServiceUser(),
                        component.getCredentialValue() != null
                                ? sensitiveValueCipher.decrypt(component.getCredentialValue())
                                : null
                );
            }
        }

        return resolveFromLegacy(definition);
    }

    private ResolvedSystemConfiguration resolveFromLegacy(BuiltInComponentDefinition definition) {
        String endpoint = legacyConfigurationService.getDecryptedValue(definition.endpointKey())
                .orElseThrow(() -> new IllegalStateException("Configuration missing: " + definition.endpointKey().name()));
        String user = definition.userKey() == null
                ? null
                : legacyConfigurationService.getDecryptedValue(definition.userKey()).orElse(null);
        String credential = definition.secretKey() == null
                ? null
                : legacyConfigurationService.getDecryptedValue(definition.secretKey()).orElse(null);
        return new ResolvedSystemConfiguration(endpoint, user, credential);
    }

    private void ensureBuiltInComponents() {
        for (BuiltInComponentDefinition definition : BuiltInComponentDefinition.values()) {
            componentRepository.findById(definition.componentId()).orElseGet(() -> {
                ConfigurationComponent component = new ConfigurationComponent();
                component.setComponentId(definition.componentId());
                component.setSystemType(definition.systemType());
                component.setDisplayName(definition.defaultLabel());
                component.setArea(definition.defaultArea());
                component.setTrackServiceUser(definition.tracksServiceUser());
                component.setTrackCredential(definition.tracksCredential());
                component.setDescription(definition.defaultDescription());
                component.setUpdatedBy("system-bootstrap");

                legacyConfigurationService.getDecryptedValue(definition.endpointKey()).ifPresent(component::setServiceEndpoint);
                if (definition.userKey() != null) {
                    legacyConfigurationService.getDecryptedValue(definition.userKey()).ifPresent(component::setServiceUser);
                }
                if (definition.secretKey() != null) {
                    legacyConfigurationService.getDecryptedValue(definition.secretKey())
                            .filter(value -> !value.isBlank())
                            .map(sensitiveValueCipher::encrypt)
                            .ifPresent(component::setCredentialValue);
                }
                return componentRepository.save(component);
            });
        }
    }

    private List<ConfigurationItemDto> toDerivedItems(ConfigurationComponent component) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.require(component.getComponentId());
        List<ConfigurationItemDto> items = new ArrayList<>();
        items.add(toConfigItem(component, definition.endpointKey(), component.getServiceEndpoint(), component.getDescription(), false));
        if (definition.userKey() != null) {
            items.add(toConfigItem(component, definition.userKey(), component.getServiceUser(), null, false));
        }
        if (definition.secretKey() != null) {
            boolean configured = component.getCredentialValue() != null && !component.getCredentialValue().isBlank();
            items.add(new ConfigurationItemDto(
                    component.getComponentId(),
                    definition.secretKey(),
                    configured ? MASKED_SECRET : "",
                    null,
                    component.getUpdatedBy(),
                    component.getUpdatedAt(),
                    component.getApplication(),
                    component.getSnowGroup(),
                    component.getAgent(),
                    component.getArea(),
                    component.getDisplayName(),
                    DEFAULT_SCOPE_SOURCE,
                    true,
                    configured
            ));
        }
        return items;
    }

    private ConfigurationItemDto toConfigItem(
            ConfigurationComponent component,
            ConfigKey key,
            String value,
            String description,
            boolean sensitive
    ) {
        return new ConfigurationItemDto(
                component.getComponentId(),
                key,
                value != null ? value : "",
                description,
                component.getUpdatedBy(),
                component.getUpdatedAt(),
                component.getApplication(),
                component.getSnowGroup(),
                component.getAgent(),
                component.getArea(),
                component.getDisplayName(),
                DEFAULT_SCOPE_SOURCE,
                sensitive,
                value != null && !value.isBlank()
        );
    }

    private Map<String, Object> baseAuditContext(ConfigurationComponent component, BuiltInComponentDefinition definition) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("componentId", definition.componentId());
        context.put("systemType", definition.systemType());
        if (component.getApplication() != null) {
            context.put("application", component.getApplication());
        }
        if (component.getSnowGroup() != null) {
            context.put("snowGroup", component.getSnowGroup());
        }
        if (component.getAgent() != null) {
            context.put("agent", component.getAgent());
        }
        return context;
    }

    private static void changedFieldsIfDifferent(List<String> changedFields, String field, String current, String next) {
        if (!Objects.equals(trimToNull(current), trimToNull(next))) {
            changedFields.add(field);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ResolvedSystemConfiguration(
            String endpoint,
            String serviceUser,
            String credential
    ) {}

    private enum BuiltInComponentDefinition {
        JENKINS(
                "jenkins",
                "JENKINS",
                "Jenkins Pipeline",
                "CI/CD",
                ConfigKey.jenkins_url,
                ConfigKey.jenkins_user,
                ConfigKey.jenkins_api_token,
                "Configuration used for Jenkins-triggered deployment jobs."
        ),
        ANSIBLE(
                "ansible",
                "ANSIBLE",
                "Ansible Automation",
                "Execution",
                ConfigKey.ansible_url,
                ConfigKey.ansible_user,
                ConfigKey.ansible_api_token,
                "Configuration used for Ansible execution and result collection."
        ),
        CALLBACK(
                "callback",
                "CALLBACK",
                "Execution Callback",
                "Integration",
                ConfigKey.execution_callback_endpoint,
                null,
                null,
                "HTTPS callback endpoint used by external tools to post execution updates."
        );

        private final String componentId;
        private final String systemType;
        private final String defaultLabel;
        private final String defaultArea;
        private final ConfigKey endpointKey;
        private final ConfigKey userKey;
        private final ConfigKey secretKey;
        private final String defaultDescription;

        BuiltInComponentDefinition(
                String componentId,
                String systemType,
                String defaultLabel,
                String defaultArea,
                ConfigKey endpointKey,
                ConfigKey userKey,
                ConfigKey secretKey,
                String defaultDescription
        ) {
            this.componentId = componentId;
            this.systemType = systemType;
            this.defaultLabel = defaultLabel;
            this.defaultArea = defaultArea;
            this.endpointKey = endpointKey;
            this.userKey = userKey;
            this.secretKey = secretKey;
            this.defaultDescription = defaultDescription;
        }

        static BuiltInComponentDefinition require(String componentId) {
            for (BuiltInComponentDefinition value : values()) {
                if (value.componentId.equalsIgnoreCase(componentId)) {
                    return value;
                }
            }
            throw new ValidationAppException("Unknown configuration component: '" + componentId + "'");
        }

        static BuiltInComponentDefinition forSystemType(String systemType) {
            for (BuiltInComponentDefinition value : values()) {
                if (value.systemType.equalsIgnoreCase(systemType)) {
                    return value;
                }
            }
            throw new ValidationAppException("Unsupported configuration component system type: '" + systemType + "'");
        }

        static BuiltInComponentDefinition forKey(ConfigKey key) {
            for (BuiltInComponentDefinition value : values()) {
                if (value.supports(key)) {
                    return value;
                }
            }
            throw new ValidationAppException("Unknown configuration key: '" + key + "'");
        }

        static List<ConfigurationComponent> ordered(List<ConfigurationComponent> components) {
            Map<String, ConfigurationComponent> byId = components.stream()
                    .collect(java.util.stream.Collectors.toMap(ConfigurationComponent::getComponentId, component -> component));
            List<ConfigurationComponent> ordered = new ArrayList<>();
            for (BuiltInComponentDefinition value : values()) {
                ConfigurationComponent component = byId.get(value.componentId);
                if (component != null) {
                    ordered.add(component);
                }
            }
            return ordered;
        }

        boolean supports(ConfigKey key) {
            return key == endpointKey || key == userKey || key == secretKey;
        }

        boolean tracksServiceUser() {
            return userKey != null;
        }

        boolean tracksCredential() {
            return secretKey != null;
        }

        String componentId() {
            return componentId;
        }

        String systemType() {
            return systemType;
        }

        String defaultLabel() {
            return defaultLabel;
        }

        String defaultArea() {
            return defaultArea;
        }

        ConfigKey endpointKey() {
            return endpointKey;
        }

        ConfigKey userKey() {
            return userKey;
        }

        ConfigKey secretKey() {
            return secretKey;
        }

        String defaultDescription() {
            return defaultDescription;
        }
    }
}
