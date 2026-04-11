package com.wwa.deploymentagent.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.wwa.deploymentagent")
public class AgentModuleBoundaryTest {

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

    @ArchTest
    static final ArchRule platform_does_not_reference_stage_enums =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.wwa.deploymentagent.contracts.enums.Stage")
                    .because("Platform must speak String stages only; the shared Stage enum is forbidden (PL-3)");
}
