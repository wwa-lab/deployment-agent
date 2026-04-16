package com.wwa.agenthub.domain.fileimport;

import com.wwa.agenthub.contracts.AgentId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private static final String BUILD_PROJECT_ID = "BD-PROJ-001";
    private static final String BUILD_PROJECT_NAME = "IBM i Build Workflow";
    private static final String BUILD_TASK_GROUP_ID = "BT-IBM-I-001";
    private static final String BUILD_TASK_GROUP_NAME = "IBM i Skill Starter Pack";
    private static final String BUILD_OWNER = "carol";

    /**
     * The shared default template schema (fallback for unknown agents).
     * Uses deployment-style sample data for backwards compatibility.
     */
    public static final TemplateSchema DEFAULT_SCHEMA = TemplateSchema.withSampleRow(
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

    private static final TemplateSchema DEPLOYMENT_SCHEMA = TemplateSchema.withSampleRow(
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

    private static final TemplateSchema TESTING_SCHEMA = TemplateSchema.withSampleRow(
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
                    buildSkillRow(1, "ibm-i-workflow-orchestrator", "workflow-routing",
                            List.of("current-work-item.md", "current-stage-artifacts.md"),
                            List.of("01-workflow-next-step.md"),
                            "01-workflow-next-step.md with the recommended next skill and routing rationale",
                            "Next-step guidance is ready for the operator",
                            true),
                    buildSkillRow(2, "ibm-i-requirement-normalizer", "requirement-package",
                            List.of("raw-request.md", "cr-summary.md"),
                            List.of("02-requirement-package.md"),
                            "02-requirement-package.md with normalized scope, assumptions, and requirement traceability",
                            "Structured requirement package markdown is ready for review",
                            false),
                    buildSkillRow(3, "ibm-i-program-analyzer", "program-analysis",
                            List.of("existing-source-ref.md"),
                            List.of("03-program-analysis.md"),
                            "03-program-analysis.md with logic summary, call flow, and program structure",
                            "Program analysis markdown captures logic, calls, and structure",
                            false),
                    buildSkillRow(4, "ibm-i-impact-analyzer", "impact-analysis",
                            List.of("02-requirement-package.md", "03-program-analysis.md"),
                            List.of("04-impact-analysis.md"),
                            "04-impact-analysis.md with impacted objects, change scope, and risk notes",
                            "Impact analysis is ready for downstream design work",
                            false),
                    buildSkillRow(5, "ibm-i-functional-spec", "functional-spec",
                            List.of("02-requirement-package.md", "04-impact-analysis.md"),
                            List.of("05-functional-spec.md"),
                            "05-functional-spec.md with current/future behavior, business rules, and acceptance criteria",
                            "Functional spec draft is ready for PR review",
                            false),
                    buildSkillRow(6, "ibm-i-technical-design", "technical-design",
                            List.of("02-requirement-package.md", "04-impact-analysis.md", "05-functional-spec.md"),
                            List.of("06-technical-design.md"),
                            "06-technical-design.md with module allocation, processing stages, and object interaction",
                            "Technical design markdown is drafted and ready for PR review",
                            false),
                    buildSkillRow(7, "ibm-i-spec-reviewer", "spec-review",
                            List.of("<target-spec>.md"),
                            List.of("07-spec-review.md"),
                            "07-spec-review.md with completeness, traceability, and readiness findings",
                            "Spec review findings are ready to attach to the PR",
                            true),
                    buildSkillRow(8, "ibm-i-program-spec", "program-spec",
                            List.of("06-technical-design.md"),
                            List.of("08-program-spec.md"),
                            "08-program-spec.md with step-by-step logic, data contracts, and BR traceability",
                            "Program spec is ready for code generation or review",
                            false),
                    buildSkillRow(9, "ibm-i-file-spec", "file-spec",
                            List.of("06-technical-design.md"),
                            List.of("09-file-spec.md", "09-file-spec.json"),
                            "09-file-spec.md plus 09-file-spec.json with DDS layout, keys, and field rules",
                            "File spec markdown and JSON are ready for DDS generation",
                            false),
                    buildSkillRow(10, "ibm-i-dds-generator", "dds-source",
                            List.of("09-file-spec.md", "09-file-spec.json"),
                            List.of("10-dds-source.md"),
                            "10-dds-source.md with generated PF/LF/PRTF/DSPF DDS source draft",
                            "DDS source draft is ready for DDS review",
                            false),
                    buildSkillRow(11, "ibm-i-dds-reviewer", "dds-review",
                            List.of("09-file-spec.md", "10-dds-source.md"),
                            List.of("11-dds-review.md"),
                            "11-dds-review.md with DDS correctness, syntax, and type-rule findings",
                            "DDS review findings are ready to attach to the PR",
                            true),
                    buildSkillRow(12, "ibm-i-ut-plan-generator", "ut-plan",
                            List.of("08-program-spec.md", "09-file-spec.md"),
                            List.of("12-ut-plan.md"),
                            "12-ut-plan.md with concrete IBM i-aware unit test cases and expected checks",
                            "UT plan markdown is ready for test scaffold generation",
                            false),
                    buildSkillRow(13, "ibm-i-test-scaffold", "test-scaffold",
                            List.of("12-ut-plan.md"),
                            List.of("13-test-scaffold.md"),
                            "13-test-scaffold.md with executable SQL/CL setup, execute, verify, and cleanup scripts",
                            "Test scaffold scripts are ready for review and execution",
                            false),
                    buildSkillRow(14, "ibm-i-code-generator", "code",
                            List.of("08-program-spec.md"),
                            List.of("14-code.md"),
                            "14-code.md with generated RPGLE or CLLE implementation draft",
                            "Generated code draft is ready for precheck and review",
                            false),
                    buildSkillRow(15, "ibm-i-compile-precheck", "compile-precheck",
                            List.of("14-code.md"),
                            List.of("15-compile-precheck.md"),
                            "15-compile-precheck.md with opcode safety, bounds, and format-policy findings",
                            "Compile precheck notes capture safety issues before PR approval",
                            true),
                    buildSkillRow(16, "ibm-i-code-reviewer", "code-review",
                            List.of("08-program-spec.md", "14-code.md"),
                            List.of("16-code-review.md"),
                            "16-code-review.md with correctness, enhancement-safety, and policy findings",
                            "Code review findings are ready to attach to the PR",
                            true)
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

    private static List<String> buildSkillRow(
            int stepSeq,
            String skillName,
            String stage,
            List<String> inputDocs,
            List<String> outputDocs,
            String expectedOutput,
            String validation,
            boolean critical
    ) {
        return List.of(
                BUILD_PROJECT_ID,
                BUILD_PROJECT_NAME,
                BUILD_TASK_GROUP_ID,
                BUILD_TASK_GROUP_NAME,
                String.valueOf(stepSeq),
                skillName,
                "MANUAL",
                skillName,
                buildSkillParameters(stage, inputDocs, outputDocs),
                expectedOutput,
                BUILD_OWNER,
                plannedTime(stepSeq, false),
                plannedTime(stepSeq, true),
                "Build",
                "Y",
                dependencyLabel(inputDocs),
                validation,
                critical ? "Y" : "N",
                "",
                "",
                ""
        );
    }

    private static String buildSkillParameters(
            String stage,
            List<String> inputDocs,
            List<String> outputDocs
    ) {
        return "{\"runner\":\"cli-skill\",\"project\":\"billing-modernization\","
                + "\"workItemId\":\"CR-2026-018\",\"stage\":\"" + stage + "\","
                + "\"inputDocs\":" + jsonArray(inputDocs) + ","
                + "\"outputDocs\":" + jsonArray(outputDocs) + ","
                + "\"notes\":\"Adjust project, work item, and docs for your change\"}";
    }

    private static String dependencyLabel(List<String> inputDocs) {
        return inputDocs.isEmpty()
                ? "Provide current markdown inputs before running the skill"
                : "Use approved input docs: " + String.join(", ", inputDocs);
    }

    private static String plannedTime(int stepSeq, boolean endTime) {
        int totalMinutes = ((stepSeq - 1) * 30) + (endTime ? 30 : 0);
        int hour = 8 + (totalMinutes / 60);
        int minute = totalMinutes % 60;
        return String.format("2026-03-22T%02d:%02d:00Z", hour, minute);
    }

    private static String jsonArray(List<String> values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add("\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return "[" + String.join(",", escaped) + "]";
    }
}
