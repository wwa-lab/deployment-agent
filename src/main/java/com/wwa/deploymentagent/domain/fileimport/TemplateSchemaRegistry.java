package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.AgentId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry mapping an agent key to the {@link TemplateSchema} it uses for
 * XLSX upload templates.
 *
 * <p>Day-1 state: every agent resolves to {@link #DEFAULT_SCHEMA}, so all three
 * per-agent template endpoints return byte-identical files. The registry is
 * the seam that lets a future customer-specific customization on, say, the
 * Testing Agent register its own extended schema without affecting the other
 * agents' templates or touching {@link UploadTemplateService}.
 *
 * <p>Intentionally does <b>not</b> support inheritance / overlay / merge
 * semantics — per-agent schemas, when they diverge, are registered as full
 * replacements. Copy-paste is cheaper than an inheritance graph for the small
 * number of templates we will ever have, and it makes it obvious from reading
 * the registry what each agent's template looks like.
 *
 * <p>MVP Foundation Seam — see docs/04-architecture/architecture.md
 * §MVP Foundation Seams.
 */
@Component
public class TemplateSchemaRegistry {

    /**
     * The shared default template schema used by every agent in MVP.
     *
     * <p>This is the single source of truth for headers, sheet name, sample
     * row, and downloadable file name. {@link UploadTemplateService} reads
     * from it instead of hardcoding the header list.
     */
    public static final TemplateSchema DEFAULT_SCHEMA = new TemplateSchema(
            "AMH_HCC_task",
            "request-template.xlsx",
            List.of(
                    "Project ID", "Project Name", "Task ID", "Task Name",
                    "Step seq#", "Step", "Execution Type",
                    "Script to be executed", "Parameter (input)",
                    "Parameter (Expected Output)", "Owner",
                    "Planned Start date/time", "Planned End date/time",
                    "Activity category", "Common", "Dependencies", "Validation", "Critical",
                    "Status", "Start date/time", "End date/time"
            ),
            List.of(
                    "WF-PROJ-001", "Workflow Project", "TG-001", "Deploy Application",
                    "1", "deploy-step-1", "MANUAL",
                    "deploy.sh", "--env uat",
                    "Deployment succeeds", "alice",
                    "2026-03-22T09:00:00Z", "2026-03-22T09:30:00Z",
                    "Application", "N", "DB ready", "Smoke test passes", "Y"
            ),
            // No per-agent custom columns in the default schema.
            List.of()
    );

    /**
     * Keyed by agent id. Day-1 every agent resolves to {@link #DEFAULT_SCHEMA}.
     * Add per-agent entries here when a customer requires a template column
     * beyond the shared core.
     */
    private final Map<String, TemplateSchema> schemasByAgent = Map.of(
            AgentId.DEPLOYMENT_AGENT, DEFAULT_SCHEMA,
            AgentId.TESTING_AGENT, DEFAULT_SCHEMA,
            AgentId.BUILD_AGENT, DEFAULT_SCHEMA
    );

    /**
     * Returns the template schema for the given agent, falling back to the
     * default schema if the agent key is unknown. Never returns {@code null}.
     */
    public TemplateSchema resolve(String agentId) {
        if (agentId == null) {
            return DEFAULT_SCHEMA;
        }
        return schemasByAgent.getOrDefault(agentId, DEFAULT_SCHEMA);
    }
}
