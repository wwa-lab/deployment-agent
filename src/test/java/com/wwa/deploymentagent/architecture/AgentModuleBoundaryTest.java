package com.wwa.deploymentagent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fitness tests for the Agent Module refactor (BA-T26).
 *
 * <p>Six canonical rules per design §7, split into focused ArchTests for clearer
 * failure messages:
 * <ol>
 *   <li>Agent modules must not depend on each other (PL-2) — split per agent.</li>
 *   <li>Platform Core must not import any Agent Module code (PL-2).</li>
 *   <li>Platform Core must not reference the forbidden shared {@code Stage} enum (PL-3).</li>
 *   <li>Platform Core must not reference per-agent {@code Stage} enums in agents..domain.. (PL-3).</li>
 *   <li>Platform Core must not branch on specific agent IDs (PL-2).</li>
 *   <li>All {@code @RestController} classes live in platform.web.shared or agents..web (§API Boundaries).</li>
 * </ol>
 *
 * <p>Note: design §7's "no hardcoded stage literals in Platform Core" rule cannot be
 * expressed with stock ArchUnit 1.x conditions (no string-literal introspection). It is
 * deferred to a grep-based CI check and is not enforced here.
 */
@AnalyzeClasses(
        packages = "com.wwa.deploymentagent",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class AgentModuleBoundaryTest {

    // ─── Rule 1 — agents do not depend on each other (PL-2) ────────────────

    @ArchTest
    static final ArchRule agents_do_not_depend_on_each_other =
            noClasses().that().resideInAPackage("..agents.deployment..")
                    .should().dependOnClassesThat().resideInAnyPackage("..agents.testing..", "..agents.build..")
                    .because("Agent Modules must not import each other (PL-2)");

    @ArchTest
    static final ArchRule testing_does_not_depend_on_other_agents =
            noClasses().that().resideInAPackage("..agents.testing..")
                    .should().dependOnClassesThat().resideInAnyPackage("..agents.deployment..", "..agents.build..")
                    .because("Agent Modules must not import each other (PL-2)");

    @ArchTest
    static final ArchRule build_does_not_depend_on_other_agents =
            noClasses().that().resideInAPackage("..agents.build..")
                    .should().dependOnClassesThat().resideInAnyPackage("..agents.deployment..", "..agents.testing..")
                    .because("Agent Modules must not import each other (PL-2)");

    // ─── Rule 2 — platform does not depend on agents (PL-2) ────────────────

    @ArchTest
    static final ArchRule platform_does_not_import_agent_code =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().resideInAPackage("..agents..")
                    .because("Platform Core must not depend on any Agent Module (PL-2)");

    // ─── Rule 3 — platform does not reference the shared Stage enum (PL-3) ─

    @ArchTest
    static final ArchRule platform_does_not_reference_stage_enums =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.wwa.deploymentagent.contracts.enums.Stage")
                    .because("Platform must speak String stages only; the shared Stage enum is forbidden (PL-3)");

    // ─── Rule 4 — platform does not reference per-agent Stage enums (PL-3) ─

    @ArchTest
    static final ArchRule platform_does_not_reference_per_agent_stage_enums =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().resideInAPackage("..agents..domain..")
                    .because("Platform Core must not bind to any per-agent Stage enum (PL-3)");

    // ─── Rule 5 — platform does not branch on specific agent IDs (PL-2) ────

    @ArchTest
    static final ArchRule platform_does_not_reference_agent_id_constants =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.wwa.deploymentagent.contracts.AgentId")
                    .because("Platform Core must not branch on specific agents (PL-2). "
                            + "Agent resolution happens through StagePipelineRegistry, not by literal comparison.");

    // ─── Rule 6 — controllers only in platform.web.shared or agents..web ───

    @ArchTest
    static final ArchRule controllers_in_agent_modules_only =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage("..platform.web.shared..", "..agents..web..")
                    .because("All REST controllers belong either to platform shared capabilities "
                            + "or an Agent Module (§API Boundaries)");
}
