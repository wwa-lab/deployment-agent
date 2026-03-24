package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * ConfigurationService – CRUD for system configuration items.
 *
 * <p>Changes apply to future executions only (locked design decision).
 * Updates are gated to DevOps Admin role – enforced in the HTTP controller.
 */
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTTPS_PATTERN = Pattern.compile("^https://.+", Pattern.CASE_INSENSITIVE);

    /** Validation rules for each known config key. */
    private static final Map<ConfigKey, Function<String, String>> CONFIG_VALIDATION =
            new EnumMap<>(ConfigKey.class);

    static {
        CONFIG_VALIDATION.put(ConfigKey.jenkins_url, v ->
                URL_PATTERN.matcher(v).matches() ? null : "jenkins_url must be a valid URI (http/https)");
        CONFIG_VALIDATION.put(ConfigKey.jenkins_user, v ->
                (v != null && !v.isBlank()) ? null : "jenkins_user must not be blank");
        CONFIG_VALIDATION.put(ConfigKey.jenkins_api_token, v ->
                (v != null && !v.isBlank()) ? null : "jenkins_api_token must not be blank");
        CONFIG_VALIDATION.put(ConfigKey.ansible_url, v ->
                URL_PATTERN.matcher(v).matches() ? null : "ansible_url must be a valid URI (http/https)");
        CONFIG_VALIDATION.put(ConfigKey.ansible_user, v ->
                (v != null && !v.isBlank()) ? null : "ansible_user must not be blank");
        CONFIG_VALIDATION.put(ConfigKey.ansible_api_token, v ->
                (v != null && !v.isBlank()) ? null : "ansible_api_token must not be blank");
        CONFIG_VALIDATION.put(ConfigKey.execution_callback_endpoint, v ->
                HTTPS_PATTERN.matcher(v).matches() ? null : "execution_callback_endpoint must use HTTPS");
    }

    private final ConfigurationRepository configurationRepository;
    private final AuditLoggerService auditLogger;

    @Transactional(readOnly = true)
    public List<ConfigurationItem> listAll() {
        return configurationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ConfigurationItem> getByKey(ConfigKey key) {
        return configurationRepository.findById(key);
    }

    /**
     * Create or update a configuration item.
     * Validates value format per key rules.
     * Creates audit log entry on success.
     */
    @Transactional
    public ConfigurationItem upsert(ConfigKey key, String value, String description, UserContext user) {
        Function<String, String> validator = CONFIG_VALIDATION.get(key);
        if (validator == null) {
            throw new ValidationAppException("Unknown configuration key: '" + key + "'");
        }

        String validationError = validator.apply(value);
        if (validationError != null) {
            throw new ValidationAppException(validationError,
                    Map.of("key", key.name(), "value", value));
        }

        Optional<ConfigurationItem> existing = configurationRepository.findById(key);
        String oldValue = existing.map(ConfigurationItem::getConfigValue).orElse(null);

        ConfigurationItem item = existing.orElseGet(ConfigurationItem::new);
        item.setConfigKey(key);
        item.setConfigValue(value);
        if (description != null) item.setDescription(description);
        else if (existing.isPresent()) item.setDescription(existing.get().getDescription());
        item.setUpdatedBy(user.userId());

        ConfigurationItem saved = configurationRepository.save(item);

        auditLogger.log(user, AuditActionType.config_update,
                Map.of("configKey", key.name(),
                       "oldValue", oldValue != null ? oldValue : "",
                       "newValue", value,
                       "application", "Deployment Agent",
                       "snowGroup", "WWA Platform",
                       "agent", "Deployment Agent"));

        return saved;
    }
}
