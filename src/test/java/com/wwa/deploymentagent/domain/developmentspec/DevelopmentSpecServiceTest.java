package com.wwa.deploymentagent.domain.developmentspec;

import com.wwa.deploymentagent.contracts.AccessScope;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.DevelopmentSpecDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.DevelopmentCodeStyle;
import com.wwa.deploymentagent.contracts.enums.DevelopmentProgramType;
import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DevelopmentSpecService")
class DevelopmentSpecServiceTest {

    @Autowired
    private DevelopmentSpecService developmentSpecService;

    @Autowired
    private DevelopmentSpecRepository developmentSpecRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("list returns only specs inside visible scopes and matching filters")
    void list_returnsOnlyVisibleScopedSpecs() {
        DevelopmentSpec visible = seedSpec("Visible Spec", "MOD-01", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);
        seedSpec("Hidden Spec", "MOD-02", "PowerCARD", "HTSA-CSI-CARD-PRD", DevelopmentSpecStatus.DRAFT);

        UserContext scopedDeveloper = new UserContext(
                "emp-dev-001",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                "Scoped Developer",
                List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ"))
        );

        Page<DevelopmentSpec> result = developmentSpecService.list(
                "visible",
                DevelopmentSpecStatus.DRAFT,
                PageRequest.of(0, 20),
                scopedDeveloper
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(DevelopmentSpec::getId)
                .containsExactly(visible.getId());
    }

    @Test
    @DisplayName("create persists draft spec and writes audit log")
    void create_persistsDraftAndAudits() {
        UserContext developer = scopedDeveloper();

        DevelopmentSpec created = developmentSpecService.create(validRequest(), developer);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getTitle()).isEqualTo("Order sync enhancement");
        assertThat(created.getProgramType()).isEqualTo("RPGLE");
        assertThat(created.getCodeStyle()).isEqualTo("FREE_FORMAT");
        assertThat(created.getStatus()).isEqualTo(DevelopmentSpecStatus.DRAFT);
        assertThat(created.getGeneratedPayload()).isNull();
        assertThat(created.getGeneratedContent()).isNull();
        assertThat(created.getCreatedBy()).isEqualTo("emp-dev-001");
        assertThat(created.getUpdatedBy()).isEqualTo("emp-dev-001");

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_create);
        assertThat(audit.getOperatorId()).isEqualTo("emp-dev-001");
        assertThat(audit.getContextPayload())
                .containsEntry("developmentSpecId", created.getId())
                .containsEntry("title", "Order sync enhancement")
                .containsEntry("status", "DRAFT");
    }

    @Test
    @DisplayName("create rejects payload without business or implementation objectives")
    void create_rejectsMissingObjectives() {
        DevelopmentSpecDto.UpsertRequest request = new DevelopmentSpecDto.UpsertRequest(
                "Missing objectives",
                "MOD-01",
                DevelopmentProgramType.RPGLE,
                DevelopmentCodeStyle.FREE_FORMAT,
                "AMH HCC",
                "HTSA-CSI-HCC-AMH-PRJ",
                Map.of("notes", "no objectives here"),
                null
        );

        assertThatThrownBy(() -> developmentSpecService.create(request, scopedDeveloper()))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("At least one business objective or implementation objective is required");
    }

    @Test
    @DisplayName("update rejects version mismatch")
    void update_rejectsVersionMismatch() {
        DevelopmentSpec spec = seedSpec("Versioned Spec", "MOD-01", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);

        DevelopmentSpecDto.UpsertRequest request = new DevelopmentSpecDto.UpsertRequest(
                "Updated title",
                "MOD-01",
                DevelopmentProgramType.RPGLE,
                DevelopmentCodeStyle.FREE_FORMAT,
                "AMH HCC",
                "HTSA-CSI-HCC-AMH-PRJ",
                baseSourcePayload(),
                spec.getVersion() + 1
        );

        assertThatThrownBy(() -> developmentSpecService.update(spec.getId(), request, scopedDeveloper()))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("Version mismatch for Development Spec update");
    }

    @Test
    @DisplayName("generate builds deterministic markdown and writes audit")
    void generate_buildsDeterministicMarkdownAndAudits() {
        DevelopmentSpec spec = seedSpec("Generate Spec", "ORDSYNC", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);
        spec.setProgramType(DevelopmentProgramType.SQLRPGLE.name());
        spec.setCodeStyle(DevelopmentCodeStyle.BOTH.name());
        spec.setSourcePayload(new LinkedHashMap<>(Map.of(
                "implementationObjective", List.of("Add retry guard", "Write audit trail"),
                "businessObjective", "Prevent duplicate order syncs",
                "inputs", List.of("orderId", "customerId"),
                "outputs", List.of("syncStatus"),
                "deploymentNotes", "Coordinate release window",
                "programNotes", "Called from nightly batch"
        )));
        developmentSpecRepository.saveAndFlush(spec);

        DevelopmentSpec generated = developmentSpecService.generate(spec.getId(), scopedDeveloper());

        assertThat(generated.getStatus()).isEqualTo(DevelopmentSpecStatus.GENERATED);
        assertThat(generated.getGeneratedBy()).isEqualTo("emp-dev-001");
        assertThat(generated.getGeneratedAt()).isNotNull();
        assertThat(generated.getGeneratedPayload()).containsEntry("title", "Generate Spec");
        assertThat(generated.getGeneratedContent())
                .contains("## Title")
                .contains("Generate Spec")
                .contains("## Scope")
                .contains("Application: AMH HCC")
                .contains("SNOW Group: HTSA-CSI-HCC-AMH-PRJ")
                .contains("## Business Objective")
                .contains("Prevent duplicate order syncs")
                .contains("## Implementation Objective")
                .contains("- Add retry guard")
                .contains("- Write audit trail")
                .contains("## Program Details")
                .contains("Program Type: SQLRPGLE")
                .contains("Code Style: BOTH")
                .contains("Module Name: ORDSYNC")
                .contains("Called from nightly batch")
                .contains("## Inputs / Outputs")
                .contains("- orderId")
                .contains("- customerId")
                .contains("- syncStatus")
                .contains("## Deployment / Operational Notes")
                .contains("Coordinate release window");

        DevelopmentSpec regenerated = developmentSpecService.generate(spec.getId(), scopedDeveloper());
        assertThat(regenerated.getGeneratedPayload()).isEqualTo(generated.getGeneratedPayload());
        assertThat(regenerated.getGeneratedContent()).isEqualTo(generated.getGeneratedContent());

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_generate);
        assertThat(audit.getContextPayload())
                .containsEntry("developmentSpecId", spec.getId())
                .containsEntry("status", "GENERATED");
    }

    @Test
    @DisplayName("export returns markdown and writes audit")
    void export_returnsMarkdownAndAudits() {
        DevelopmentSpec spec = generatedSpec("Export Spec");

        DevelopmentSpecService.ExportDocument export = developmentSpecService.export(spec.getId(), "markdown", scopedDeveloper());

        assertThat(export.filename()).isEqualTo("export-spec.md");
        assertThat(export.contentType()).isEqualTo("text/markdown");
        assertThat(new String(export.content(), StandardCharsets.UTF_8))
                .contains("## Title")
                .contains("Export Spec");

        AuditLogEntry audit = latestAudit(AuditActionType.development_spec_export);
        assertThat(audit.getContextPayload())
                .containsEntry("developmentSpecId", spec.getId())
                .containsEntry("status", "GENERATED");
    }

    @Test
    @DisplayName("export returns json payload")
    void export_returnsJsonPayload() {
        DevelopmentSpec spec = generatedSpec("Json Export Spec");

        DevelopmentSpecService.ExportDocument export = developmentSpecService.export(spec.getId(), "json", scopedDeveloper());

        String body = new String(export.content(), StandardCharsets.UTF_8);
        assertThat(export.filename()).isEqualTo("json-export-spec.json");
        assertThat(export.contentType()).isEqualTo("application/json");
        assertThat(body)
                .contains("\"title\" : \"Json Export Spec\"")
                .contains("\"status\" : \"GENERATED\"")
                .contains("\"generatedContent\"")
                .contains("\"generatedPayload\"");
    }

    @Test
    @DisplayName("export rejects draft spec")
    void export_rejectsDraftSpec() {
        DevelopmentSpec spec = seedSpec("Draft Export", "MOD-01", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);

        assertThatThrownBy(() -> developmentSpecService.export(spec.getId(), "markdown", scopedDeveloper()))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("must be generated before export");
    }

    @Test
    @DisplayName("export rejects users outside the spec scope")
    void export_rejectsOutOfScopeUser() {
        DevelopmentSpec spec = generatedSpec("Scoped Export");

        UserContext otherScopeDeveloper = new UserContext(
                "emp-dev-002",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                "Other Developer",
                List.of(new AccessScope("PowerCARD", "HTSA-CSI-CARD-PRD"))
        );

        assertThatThrownBy(() -> developmentSpecService.export(spec.getId(), "json", otherScopeDeveloper))
                .isInstanceOf(ForbiddenAppException.class)
                .hasMessageContaining("view_development_spec");
    }

    @Test
    @DisplayName("generate rejects users outside the spec scope")
    void generate_rejectsOutOfScopeUser() {
        DevelopmentSpec spec = seedSpec("Scoped Spec", "MOD-01", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);

        UserContext otherScopeDeveloper = new UserContext(
                "emp-dev-002",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                "Other Developer",
                List.of(new AccessScope("PowerCARD", "HTSA-CSI-CARD-PRD"))
        );

        assertThatThrownBy(() -> developmentSpecService.generate(spec.getId(), otherScopeDeveloper))
                .isInstanceOf(ForbiddenAppException.class)
                .hasMessageContaining("generate_development_spec");
    }

    private DevelopmentSpec generatedSpec(String title) {
        DevelopmentSpec spec = seedSpec(title, "ORDSYNC", "AMH HCC", "HTSA-CSI-HCC-AMH-PRJ", DevelopmentSpecStatus.DRAFT);
        return developmentSpecService.generate(spec.getId(), scopedDeveloper());
    }

    private DevelopmentSpec seedSpec(String title,
                                     String moduleName,
                                     String application,
                                     String snowGroup,
                                     DevelopmentSpecStatus status) {
        DevelopmentSpec spec = new DevelopmentSpec();
        spec.setTitle(title);
        spec.setModuleName(moduleName);
        spec.setProgramType(DevelopmentProgramType.RPGLE.name());
        spec.setCodeStyle(DevelopmentCodeStyle.FREE_FORMAT.name());
        spec.setApplication(application);
        spec.setSnowGroup(snowGroup);
        spec.setSourcePayload(baseSourcePayload());
        spec.setStatus(status);
        spec.setCreatedBy("seed");
        spec.setUpdatedBy("seed");
        return developmentSpecRepository.saveAndFlush(spec);
    }

    private DevelopmentSpecDto.UpsertRequest validRequest() {
        return new DevelopmentSpecDto.UpsertRequest(
                "Order sync enhancement",
                "ORDSYNC",
                DevelopmentProgramType.RPGLE,
                DevelopmentCodeStyle.FREE_FORMAT,
                "AMH HCC",
                "HTSA-CSI-HCC-AMH-PRJ",
                baseSourcePayload(),
                null
        );
    }

    private Map<String, Object> baseSourcePayload() {
        return new LinkedHashMap<>(Map.of(
                "businessObjective", "Reduce manual order follow-up",
                "implementationObjective", List.of("Add validation", "Generate result details"),
                "inputs", List.of("orderId"),
                "outputs", List.of("resultFlag")
        ));
    }

    private UserContext scopedDeveloper() {
        return new UserContext(
                "emp-dev-001",
                "DEVELOPER",
                List.of("DEVELOPER"),
                Set.of(),
                "Scoped Developer",
                List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ"))
        );
    }

    private AuditLogEntry latestAudit(AuditActionType actionType) {
        return auditLogRepository.findByActionType(
                        actionType,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent()
                .getFirst();
    }
}
