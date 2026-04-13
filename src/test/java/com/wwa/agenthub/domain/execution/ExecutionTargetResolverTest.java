package com.wwa.agenthub.domain.execution;

import com.wwa.agenthub.errors.ValidationAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionTargetResolver")
class ExecutionTargetResolverTest {

    private final ExecutionTargetResolver resolver = new ExecutionTargetResolver();

    // ─── Legacy plain script ───────────────────────────────────────────────────

    @Test
    @DisplayName("plain script with no system → Jenkins (legacy compatibility)")
    void plainScript_noSystem_resolvesToJenkins() {
        ExecutionTarget target = resolver.resolve(Map.of("script", "my-deploy-job"));

        assertThat(target.systemType()).isEqualTo("JENKINS");
        assertThat(target.targetKind()).isEqualTo(ExecutionTarget.KIND_JENKINS_JOB_PATH);
        assertThat(target.normalizedTarget()).isEqualTo("my-deploy-job");
        assertThat(target.displayUrl()).isNull();
        assertThat(target.explicitOverride()).isFalse();
    }

    @Test
    @DisplayName("plain script + system=JENKINS → Jenkins explicit")
    void plainScript_systemJenkins_resolvesToJenkins() {
        ExecutionTarget target = resolver.resolve(Map.of("script", "my-job", "system", "JENKINS"));

        assertThat(target.systemType()).isEqualTo("JENKINS");
        assertThat(target.explicitOverride()).isTrue();
    }

    @Test
    @DisplayName("plain numeric script + system=ANSIBLE → Ansible job template")
    void plainScript_systemAnsible_resolvesToAnsible() {
        ExecutionTarget target = resolver.resolve(Map.of("script", "42", "system", "ANSIBLE"));

        assertThat(target.systemType()).isEqualTo("ANSIBLE");
        assertThat(target.targetKind()).isEqualTo(ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE);
        assertThat(target.normalizedTarget()).isEqualTo("42");
        assertThat(target.explicitOverride()).isTrue();
    }

    // ─── Jenkins URL inference ─────────────────────────────────────────────────

    @Test
    @DisplayName("Jenkins job URL in script → resolves to JENKINS with job path")
    void jenkinsUrl_resolvesToJenkins() {
        ExecutionTarget target = resolver.resolve(
                Map.of("script", "http://jenkins:8080/job/my-pipeline/"));

        assertThat(target.systemType()).isEqualTo("JENKINS");
        assertThat(target.targetKind()).isEqualTo(ExecutionTarget.KIND_JENKINS_JOB_URL);
        assertThat(target.normalizedTarget()).isEqualTo("my-pipeline");
        assertThat(target.displayUrl()).isEqualTo("http://jenkins:8080/job/my-pipeline/");
        assertThat(target.explicitOverride()).isFalse();
    }

    @Test
    @DisplayName("Jenkins URL + system=JENKINS override → accepted (no conflict)")
    void jenkinsUrl_withJenkinsOverride_accepted() {
        ExecutionTarget target = resolver.resolve(
                Map.of("script", "http://jenkins:8080/job/my-pipeline/", "system", "JENKINS"));

        assertThat(target.systemType()).isEqualTo("JENKINS");
        assertThat(target.explicitOverride()).isTrue();
    }

    @Test
    @DisplayName("Jenkins URL with nested job path → extracts multi-segment path")
    void jenkinsUrl_nestedPath_extracted() {
        ExecutionTarget target = resolver.resolve(
                Map.of("script", "http://jenkins:8080/job/team/job/my-pipeline/build"));

        assertThat(target.normalizedTarget()).isEqualTo("team/job/my-pipeline");
    }

    // ─── Ansible URL inference ─────────────────────────────────────────────────

    @Test
    @DisplayName("Ansible job_templates URL → resolves to ANSIBLE job template")
    void ansibleJobTemplateUrl_resolvesToAnsible() {
        ExecutionTarget target = resolver.resolve(
                Map.of("script", "http://awx.company.com/api/v2/job_templates/42/launch/"));

        assertThat(target.systemType()).isEqualTo("ANSIBLE");
        assertThat(target.targetKind()).isEqualTo(ExecutionTarget.KIND_ANSIBLE_JOB_TEMPLATE);
        assertThat(target.normalizedTarget()).isEqualTo("42");
        assertThat(target.displayUrl()).contains("job_templates/42");
    }

    @Test
    @DisplayName("Ansible workflow_job_templates URL → resolves to ANSIBLE workflow template")
    void ansibleWorkflowUrl_resolvesToAnsibleWorkflow() {
        ExecutionTarget target = resolver.resolve(
                Map.of("script", "http://awx.company.com/api/v2/workflow_job_templates/7/launch/"));

        assertThat(target.systemType()).isEqualTo("ANSIBLE");
        assertThat(target.targetKind()).isEqualTo(ExecutionTarget.KIND_ANSIBLE_WORKFLOW_TEMPLATE);
        assertThat(target.normalizedTarget()).isEqualTo("7");
    }

    // ─── Conflict detection ────────────────────────────────────────────────────

    @Test
    @DisplayName("system=ANSIBLE + Jenkins URL → ValidationAppException")
    void ansibleOverride_withJenkinsUrl_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(
                Map.of("script", "http://jenkins:8080/job/my-job/", "system", "ANSIBLE")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    @DisplayName("system=JENKINS + Ansible URL → ValidationAppException")
    void jenkinsOverride_withAnsibleUrl_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(
                Map.of("script", "http://awx/api/v2/job_templates/42/", "system", "JENKINS")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    @DisplayName("unsupported URL pattern → ValidationAppException")
    void unsupportedUrl_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(
                Map.of("script", "http://unknown-tool.company.com/execute/42")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("Unsupported URL pattern");
    }

    @Test
    @DisplayName("invalid system value → ValidationAppException")
    void invalidSystem_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(
                Map.of("script", "my-job", "system", "BAMBOO")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("Invalid 'system' value");
    }

    // ─── Validation guards ─────────────────────────────────────────────────────

    @Test
    @DisplayName("blank script → ValidationAppException")
    void blankScript_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(Map.of("script", "  ")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    @DisplayName("missing script → ValidationAppException")
    void missingScript_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(Map.of("parameters", "foo")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    @DisplayName("null inputParameters → ValidationAppException")
    void nullParams_throwsValidation() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(ValidationAppException.class);
    }

    // ─── extractJenkinsJobPath ────────────────────────────────────────────────

    @Test
    @DisplayName("extractJenkinsJobPath strips trailing build segment")
    void extractJenkinsJobPath_stripsBuildSegment() {
        String path = ExecutionTargetResolver.extractJenkinsJobPath(
                "http://jenkins:8080/job/my-job/buildWithParameters");
        assertThat(path).isEqualTo("my-job");
    }

    @Test
    @DisplayName("extractAnsibleTemplateId extracts numeric ID")
    void extractAnsibleTemplateId_extractsId() {
        String id = ExecutionTargetResolver.extractAnsibleTemplateId(
                "http://awx/api/v2/job_templates/99/launch/");
        assertThat(id).isEqualTo("99");
    }
}
