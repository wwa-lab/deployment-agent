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

import java.util.*;

/**
 * Source of truth for built-in configuration components, including scoped
 * overrides and the raw key rows derived from them.
 */
@Service
@RequiredArgsConstructor
public class ConfigurationComponentService {

    private static final ConfigurationScope PLATFORM_SCOPE = ConfigurationScope.empty();
    private static final String MASKED_SECRET = "••••••••";

    private final ConfigurationComponentRepository componentRepository;
    private final ConfigurationService legacyConfigurationService;
    private final SensitiveValueCipher sensitiveValueCipher;
    private final AuditLoggerService auditLogger;

    @Transactional(readOnly = true)
    public List<ConfigurationComponent> listComponents() {
        return BuiltInComponentDefinition.ordered(withBuiltInDefaults(componentRepository.findAll()));
    }

    @Transactional
    public ConfigurationComponent upsertComponent(ConfigurationComponentDto.UpsertRequest request, UserContext user) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.require(request.componentId());

        ConfigurationScope scope = normalizeScope(request.application(), request.snowGroup(), request.agent());
        ConfigurationComponent component = resolveEditableComponent(request.componentInstanceId(), definition, scope);

        validateDuplicateScope(definition, scope, component.getId());

        Map<String, Object> auditContext = baseAuditContext(component, definition, scope);
        List<String> changedFields = new ArrayList<>();

        changedFieldsIfDifferent(changedFields, "displayName", component.getDisplayName(), request.displayName());
        changedFieldsIfDifferent(changedFields, "area", component.getArea(), request.area());
        changedFieldsIfDifferent(changedFields, "application", component.getApplication(), scope.application());
        changedFieldsIfDifferent(changedFields, "snowGroup", component.getSnowGroup(), scope.snowGroup());
        changedFieldsIfDifferent(changedFields, "agent", component.getAgent(), scope.agent());
        changedFieldsIfDifferent(changedFields, "serviceEndpoint", component.getServiceEndpoint(), request.serviceEndpoint());
        changedFieldsIfDifferent(changedFields, "serviceUser", component.getServiceUser(), request.serviceUser());
        changedFieldsIfDifferent(changedFields, "description", component.getDescription(), request.description());

        component.setComponentId(definition.componentId());
        component.setScopeKey(scope.scopeKey());
        component.setSystemType(definition.systemType());
        component.setDisplayName(request.displayName().trim());
        component.setArea(request.area().trim());
        component.setApplication(scope.application());
        component.setSnowGroup(scope.snowGroup());
        component.setAgent(scope.agent());
        component.setTrackServiceUser(definition.tracksServiceUser());
        component.setTrackCredential(definition.tracksCredential());
        component.setServiceEndpoint(requireNonBlank("Service endpoint", request.serviceEndpoint()));
        component.setServiceUser(definition.tracksServiceUser() ? requireNonBlank("Service user", request.serviceUser()) : null);
        component.setDescription(trimToNull(request.description()));
        component.setUpdatedBy(user.userId());

        boolean credentialChanged = trimToNull(request.credentialValue()) != null;
        if (credentialChanged) {
            component.setCredentialValue(sensitiveValueCipher.encrypt(request.credentialValue().trim()));
        } else if (definition.tracksCredential()
                && (component.getCredentialValue() == null || component.getCredentialValue().isBlank())) {
            throw new ValidationAppException("Credential is required for component '" + definition.componentId() + "'");
        }

        ConfigurationComponent saved = componentRepository.save(component);
        auditContext.put("componentInstanceId", saved.getId());
        auditContext.put("scopeSource", scope.scopeSource());
        if (!changedFields.isEmpty()) {
            auditContext.put("changedFields", changedFields);
        }
        auditContext.put("credentialChanged", credentialChanged);
        auditLogger.log(user, AuditActionType.config_update, auditContext);
        return saved;
    }

    @Transactional(readOnly = true)
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

        ConfigurationComponent component = resolveComponentForRawUpdate(request, definition);
        if (!definition.supports(request.key())) {
            throw new ValidationAppException("Config key '" + request.key() + "' does not belong to component '" + component.getComponentId() + "'");
        }

        String trimmedValue = request.value().trim();
        if (trimmedValue.isEmpty()) {
            throw new ValidationAppException("Configuration value must not be blank");
        }

        ConfigurationScope scope = scopeOf(component);
        Map<String, Object> auditContext = baseAuditContext(component, definition, scope);
        auditContext.put("componentInstanceId", component.getId());
        auditContext.put("scopeSource", scope.scopeSource());
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

    @Transactional(readOnly = true)
    public ResolvedSystemConfiguration resolveForSystem(String systemType, ConfigurationScope requestedScope) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.forSystemType(systemType);
        List<ConfigurationComponent> components = BuiltInComponentDefinition.orderWithinDefinition(
                componentRepository.findByComponentIdIgnoreCase(definition.componentId())
        );
        ConfigurationScope runtimeScope = requestedScope == null ? PLATFORM_SCOPE : requestedScope;

        String endpoint = null;
        String serviceUser = null;
        String credential = null;

        for (ConfigurationComponent component : components) {
            ConfigurationScope componentScope = scopeOf(component);
            if (!componentScope.matchesRequestedScope(runtimeScope)) {
                continue;
            }

            if (endpoint == null) {
                endpoint = trimToNull(component.getServiceEndpoint());
            }
            if (definition.userKey() != null && serviceUser == null) {
                serviceUser = trimToNull(component.getServiceUser());
            }
            if (definition.secretKey() != null && credential == null && trimToNull(component.getCredentialValue()) != null) {
                credential = sensitiveValueCipher.decrypt(component.getCredentialValue());
            }

            if (endpoint != null
                    && (definition.userKey() == null || serviceUser != null)
                    && (definition.secretKey() == null || credential != null)) {
                return new ResolvedSystemConfiguration(endpoint, serviceUser, credential);
            }
        }

        return resolveFromLegacy(definition);
    }

    @Transactional(readOnly = true)
    public ResolvedSystemConfiguration resolveForSystem(String systemType) {
        return resolveForSystem(systemType, PLATFORM_SCOPE);
    }

    private ConfigurationComponent resolveComponentForRawUpdate(
            ConfigurationItemDto.UpsertRequest request,
            BuiltInComponentDefinition definition
    ) {
        if (request.componentInstanceId() != null && !request.componentInstanceId().isBlank()) {
            ConfigurationComponent component = componentRepository.findById(request.componentInstanceId())
                    .orElseThrow(() -> new ValidationAppException(
                            "Unknown configuration component instance: '" + request.componentInstanceId() + "'"));
            if (!component.getComponentId().equalsIgnoreCase(definition.componentId())) {
                throw new ValidationAppException(
                        "Config key '" + request.key() + "' does not belong to component instance '" + request.componentInstanceId() + "'");
            }
            return component;
        }
        return getOrCreatePlatformDefault(definition);
    }

    private ConfigurationComponent resolveEditableComponent(
            String componentInstanceId,
            BuiltInComponentDefinition definition,
            ConfigurationScope scope
    ) {
        if (componentInstanceId == null || componentInstanceId.isBlank()) {
            return componentRepository.findByComponentIdIgnoreCaseAndScopeKey(definition.componentId(), scope.scopeKey())
                    .orElseGet(() -> {
                        ConfigurationComponent component = new ConfigurationComponent();
                        component.setComponentId(definition.componentId());
                        component.setScopeKey(scope.scopeKey());
                        component.setSystemType(definition.systemType());
                        component.setTrackServiceUser(definition.tracksServiceUser());
                        component.setTrackCredential(definition.tracksCredential());
                        return component;
                    });
        }

        ConfigurationComponent component = componentRepository.findById(componentInstanceId)
                .orElseThrow(() -> new ValidationAppException(
                        "Unknown configuration component instance: '" + componentInstanceId + "'"));
        if (!component.getComponentId().equalsIgnoreCase(definition.componentId())) {
            throw new ValidationAppException(
                    "Component instance '" + componentInstanceId + "' does not belong to component '" + definition.componentId() + "'");
        }
        return component;
    }

    private void validateDuplicateScope(
            BuiltInComponentDefinition definition,
            ConfigurationScope scope,
            String existingId
    ) {
        componentRepository.findByComponentIdIgnoreCaseAndScopeKey(definition.componentId(), scope.scopeKey())
                .filter(component -> !Objects.equals(component.getId(), existingId))
                .ifPresent(component -> {
                    throw new ValidationAppException(
                            "A scoped configuration already exists for component '" + definition.componentId()
                                    + "' with scope source '" + scope.scopeSource() + "'");
                });
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

    private List<ConfigurationComponent> withBuiltInDefaults(List<ConfigurationComponent> persistedComponents) {
        List<ConfigurationComponent> components = new ArrayList<>(persistedComponents);
        Set<String> platformDefaults = new HashSet<>();
        for (ConfigurationComponent component : persistedComponents) {
            if (scopeOf(component).specificity() == PLATFORM_SCOPE.specificity()) {
                platformDefaults.add(component.getComponentId().toLowerCase(Locale.ROOT));
            }
        }

        for (BuiltInComponentDefinition definition : BuiltInComponentDefinition.values()) {
            if (!platformDefaults.contains(definition.componentId().toLowerCase(Locale.ROOT))) {
                components.add(buildPlatformDefaultComponent(definition));
            }
        }
        return components;
    }

    private ConfigurationComponent buildPlatformDefaultComponent(BuiltInComponentDefinition definition) {
        ConfigurationComponent component = new ConfigurationComponent();
        component.setComponentId(definition.componentId());
        component.setScopeKey(PLATFORM_SCOPE.scopeKey());
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
        return component;
    }

    private ConfigurationComponent getOrCreatePlatformDefault(BuiltInComponentDefinition definition) {
        return componentRepository.findByComponentIdIgnoreCaseAndScopeKey(definition.componentId(), PLATFORM_SCOPE.scopeKey())
                .orElseGet(() -> componentRepository.save(buildPlatformDefaultComponent(definition)));
    }

    private List<ConfigurationItemDto> toDerivedItems(ConfigurationComponent component) {
        BuiltInComponentDefinition definition = BuiltInComponentDefinition.require(component.getComponentId());
        ConfigurationScope scope = scopeOf(component);
        List<ConfigurationItemDto> items = new ArrayList<>();
        items.add(toConfigItem(component, definition.endpointKey(), component.getServiceEndpoint(), component.getDescription(), false, scope));
        if (definition.userKey() != null) {
            items.add(toConfigItem(component, definition.userKey(), component.getServiceUser(), null, false, scope));
        }
        if (definition.secretKey() != null) {
            boolean configured = component.getCredentialValue() != null && !component.getCredentialValue().isBlank();
            items.add(new ConfigurationItemDto(
                    component.getId(),
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
                    scope.scopeSource(),
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
            boolean sensitive,
            ConfigurationScope scope
    ) {
        return new ConfigurationItemDto(
                component.getId(),
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
                scope.scopeSource(),
                sensitive,
                value != null && !value.isBlank()
        );
    }

    private Map<String, Object> baseAuditContext(
            ConfigurationComponent component,
            BuiltInComponentDefinition definition,
            ConfigurationScope scope
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("componentId", definition.componentId());
        context.put("systemType", definition.systemType());
        if (component.getId() != null) {
            context.put("componentInstanceId", component.getId());
        }
        if (scope.application() != null) {
            context.put("application", scope.application());
        }
        if (scope.snowGroup() != null) {
            context.put("snowGroup", scope.snowGroup());
        }
        if (scope.agent() != null) {
            context.put("agent", scope.agent());
        }
        return context;
    }

    private static ConfigurationScope normalizeScope(String application, String snowGroup, String agent) {
        ConfigurationScope scope = new ConfigurationScope(application, snowGroup, agent);
        try {
            scope.validateHierarchy();
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException(ex.getMessage());
        }
        return scope;
    }

    private static ConfigurationScope scopeOf(ConfigurationComponent component) {
        return new ConfigurationScope(
                component.getApplication(),
                component.getSnowGroup(),
                component.getAgent()
        );
    }

    private static void changedFieldsIfDifferent(List<String> changedFields, String field, String current, String next) {
        if (!Objects.equals(trimToNull(current), trimToNull(next))) {
            changedFields.add(field);
        }
    }

    private static String requireNonBlank(String label, String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ValidationAppException(label + " is required");
        }
        return trimmed;
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
            List<ConfigurationComponent> ordered = new ArrayList<>(components);
            ordered.sort(comparator());
            return ordered;
        }

        static List<ConfigurationComponent> orderWithinDefinition(List<ConfigurationComponent> components) {
            List<ConfigurationComponent> ordered = new ArrayList<>(components);
            ordered.sort(Comparator
                    .comparingInt((ConfigurationComponent component) -> scopeSpecificity(component)).reversed()
                    .thenComparing(ConfigurationComponent::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ConfigurationComponent::getId));
            return ordered;
        }

        private static Comparator<ConfigurationComponent> comparator() {
            return Comparator
                    .comparingInt((ConfigurationComponent component) -> require(component.getComponentId()).ordinal())
                    .thenComparing((ConfigurationComponent component) -> scopeSpecificity(component), Comparator.reverseOrder())
                    .thenComparing(ConfigurationComponent::getApplication, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(ConfigurationComponent::getSnowGroup, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(ConfigurationComponent::getAgent, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(ConfigurationComponent::getId);
        }

        private static Integer scopeSpecificity(ConfigurationComponent component) {
            return new ConfigurationScope(
                    component.getApplication(),
                    component.getSnowGroup(),
                    component.getAgent()
            ).specificity();
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
