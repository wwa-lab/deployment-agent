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
    jenkins_url("Deployment Agent", "Deployment Agent"),
    jenkins_user("Deployment Agent", "Deployment Agent"),
    jenkins_api_token("Deployment Agent", "Deployment Agent"),
    ansible_url("Deployment Agent", "Deployment Agent"),
    ansible_user("Deployment Agent", "Deployment Agent"),
    ansible_api_token("Deployment Agent", "Deployment Agent"),
    execution_callback_endpoint("Deployment Agent", "Deployment Agent"),

    // --- Platform-shared ---
    // Add platform-level config keys here when a genuine shared concern arises.
    // Do not add agent-specific settings to this section.
    agent_contribution_dashboard_statuses("WWA Platform", "Agent Contribute Dashboard");

    private final String auditApplication;
    private final String auditAgent;

    ConfigKey(String auditApplication, String auditAgent) {
        this.auditApplication = auditApplication;
        this.auditAgent = auditAgent;
    }

    public String auditApplication() {
        return auditApplication;
    }

    public String auditAgent() {
        return auditAgent;
    }

    public boolean isSensitive() {
        return this == jenkins_api_token || this == ansible_api_token;
    }
}
