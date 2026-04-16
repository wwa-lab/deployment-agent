package com.wwa.agenthub.contracts.enums;

/**
 * Known configuration keys managed by the ConfigurationService.
 *
 * <p><b>Classification (WWA-011):</b>
 * <ul>
 *   <li><b>Agent-private (Deployment Agent):</b> All current keys below. These belong to
 *       Deployment Agent's execution integrations and are not shared across the WWA platform.</li>
 *   <li><b>Platform-shared (future):</b> Reserved for future platform-level configuration
 *       such as agent registry metadata or notification settings. Introduce new enum values
 *       in a clearly labelled section when needed.</li>
 * </ul>
 *
 * <p>Configuration items are displayed on the "Deployment Agent Configuration" section of the
 * Configuration Management page. See {@code ConfigAdminView.vue}.
 */
public enum ConfigKey {
    // --- Agent-private: Deployment Agent execution integrations ---
    jenkins_url,
    jenkins_user,
    jenkins_api_token,
    ansible_url,
    ansible_user,
    ansible_api_token,
    execution_callback_endpoint;

    // --- Platform-shared: (none yet) ---
    // Add platform-level config keys here when a genuine shared concern arises.
    // Do not add agent-specific settings to this section.

    public boolean isSensitive() {
        return this == jenkins_api_token || this == ansible_api_token;
    }
}
