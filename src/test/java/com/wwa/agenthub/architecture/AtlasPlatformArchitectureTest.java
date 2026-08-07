package com.wwa.agenthub.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasPlatformArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.wwa.agenthub");

    @Test
    void integrationPlatformSliceExistsAndDoesNotDependOnAgentModules() {
        assertThat(classes.stream()
                .anyMatch(type -> type.getPackageName().startsWith(
                        "com.wwa.agenthub.platform.domain.integration")))
                .as("the Platform Integration slice must exist")
                .isTrue();

        noClasses()
                .that().resideInAPackage("..platform.domain.integration..")
                .should().dependOnClassesThat().resideInAPackage("..agents..")
                .check(classes);
    }

    @Test
    void integrationPlatformDoesNotReferenceConcreteAgentIdsOrExecutionAdapters() {
        noClasses()
                .that().resideInAnyPackage(
                        "..platform.domain.integration..",
                        "..platform.web.shared.integration..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.wwa.agenthub.contracts.AgentId")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..platform.domain.integration..")
                .should().dependOnClassesThat().resideInAPackage("..domain.execution..")
                .check(classes);
    }

    @Test
    void integrationControllersRemainSharedPlatformControllers() {
        noClasses()
                .that().haveSimpleNameEndingWith("IntegrationController")
                .should().resideOutsideOfPackage("..platform.web.shared.integration..")
                .check(classes);

        noClasses()
                .that().resideInAPackage("..platform.web.shared.integration..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(classes);
    }

    @Test
    void agentModulesDoNotOwnPlatformIntegrationPersistence() {
        assertThat(classes.stream()
                .filter(type -> type.getPackageName().contains(".agents."))
                .map(type -> type.getSimpleName()))
                .noneMatch(name -> name.matches(
                        ".*(Artifact|ExecutionEvent|Idempotency|ReviewDecision|CapabilityUsage).*Repository"));
    }

    @Test
    void agentModulesDoNotOwnOrInvokeTheSharedIntegrationControlPlane() {
        noClasses()
                .that().resideInAPackage("..agents..")
                .should().dependOnClassesThat().resideInAPackage("..platform.domain.integration..")
                .check(classes);
    }

    @Test
    void integrationContractsRemainPersistenceAndDomainIndependent() {
        noClasses()
                .that().resideInAPackage("..contracts.dto.integration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "..domain..")
                .check(classes);
    }

    @Test
    void serverDoesNotIntroduceLangGraphOrLocalSkillRuntime() throws Exception {
        String pom = Files.readString(Path.of("pom.xml")).toLowerCase();
        assertThat(pom).doesNotContain("langgraph");
        assertThat(classes.stream().map(type -> type.getName().toLowerCase()))
                .noneMatch(name -> name.contains("langgraph") || name.contains("skillruntime"));
    }
}
