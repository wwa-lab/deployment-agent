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

    private static final String SHEET_NAME = "AMH_HCC_task";
    private static final String FILE_NAME = "request-template.xlsx";
    private static final List<String> HEADERS = List.of(
            "Project ID", "Project Name", "Task ID", "Task Name",
            "Step seq#", "Step", "Execution Type",
            "Script to be executed", "Parameter (input)",
            "Parameter (Expected Output)", "Owner",
            "Planned Start date/time", "Planned End date/time",
            "Activity category", "Common", "Dependencies", "Validation", "Critical",
            "Status", "Start date/time", "End date/time"
    );
    private static final List<String> NO_CUSTOM_COLUMNS = List.of();

    /**
     * The shared default template schema (fallback for unknown agents).
     * Uses deployment-style sample data for backwards compatibility.
     */
    public static final TemplateSchema DEFAULT_SCHEMA = new TemplateSchema(
            SHEET_NAME, FILE_NAME, HEADERS,
            List.of(
                    "WF-PROJ-001", "Workflow Project", "TG-001", "Deploy Application",
                    "1", "deploy-step-1", "MANUAL",
                    "deploy.sh", "--env uat",
                    "Deployment succeeds", "alice",
                    "2026-03-22T09:00:00Z", "2026-03-22T09:30:00Z",
                    "Application", "N", "DB ready", "Smoke test passes", "Y"
            ),
            NO_CUSTOM_COLUMNS
    );

    private static final TemplateSchema DEPLOYMENT_SCHEMA = new TemplateSchema(
            SHEET_NAME, FILE_NAME, HEADERS,
            List.of(
                    "WF-PROJ-001", "Workflow Project", "TG-001", "Deploy Application",
                    "1", "deploy-step-1", "MANUAL",
                    "deploy.sh", "--env uat",
                    "Deployment succeeds", "alice",
                    "2026-03-22T09:00:00Z", "2026-03-22T09:30:00Z",
                    "Application", "N", "DB ready", "Smoke test passes", "Y"
            ),
            NO_CUSTOM_COLUMNS
    );

    private static final TemplateSchema TESTING_SCHEMA = new TemplateSchema(
            SHEET_NAME, FILE_NAME, HEADERS,
            List.of(
                    "QA-PROJ-001", "Regression Suite", "TC-001", "Run Regression Tests",
                    "1", "execute-tests", "AUTO",
                    "test-runner.sh", "--suite regression --env uat",
                    "All tests pass", "bob",
                    "2026-03-22T10:00:00Z", "2026-03-22T11:00:00Z",
                    "Testing", "N", "Build complete", "Test report generated", "Y"
            ),
            NO_CUSTOM_COLUMNS
    );

    private static final TemplateSchema BUILD_SCHEMA = new TemplateSchema(
            SHEET_NAME, FILE_NAME, HEADERS,
            List.of(
                    "BD-PROJ-001", "Build Pipeline", "BT-001", "Compile Application",
                    "1", "maven-build", "AUTO",
                    "mvn-build.sh", "--profile release",
                    "Build succeeds with 0 errors", "carol",
                    "2026-03-22T08:00:00Z", "2026-03-22T08:30:00Z",
                    "Build", "N", "Source code merged", "Artifact published", "Y"
            ),
            NO_CUSTOM_COLUMNS
    );

    /**
     * Keyed by agent id. Each agent resolves to its own schema with
     * agent-appropriate sample data.
     */
    private final Map<String, TemplateSchema> schemasByAgent = Map.of(
            AgentId.DEPLOYMENT_AGENT, DEPLOYMENT_SCHEMA,
            AgentId.TESTING_AGENT, TESTING_SCHEMA,
            AgentId.BUILD_AGENT, BUILD_SCHEMA
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
