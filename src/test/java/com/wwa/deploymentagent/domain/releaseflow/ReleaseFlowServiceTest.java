package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.AccessScope;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.RequestArchiveResultDto;
import com.wwa.deploymentagent.contracts.dto.RequestPurgeResultDto;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ReleaseFlowService")
class ReleaseFlowServiceTest {

    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private ReleaseFlowRepository releaseFlowRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TestDataHelper helper;

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create sets all fields and defaults to Pending/Pending_Review")
    void create_setsAllFields() {
        ReleaseFlow rf = releaseFlowService.create(
                "PROJ-X", "Project X", "sit-projectx-0001", "sit-projectx-0001", Stage.SIT);

        assertThat(rf.getId()).isNotNull();
        assertThat(rf.getProjectId()).isEqualTo("PROJ-X");
        assertThat(rf.getProjectName()).isEqualTo("Project X");
        assertThat(rf.getReleaseId()).isEqualTo("sit-projectx-0001");
        assertThat(rf.getNormalizedReleaseId()).isEqualTo("sit-projectx-0001");
        assertThat(rf.getCurrentStage()).isEqualTo(Stage.SIT);
        assertThat(rf.getFlowStatus()).isEqualTo(FlowStatus.Pending);
        assertThat(rf.getReviewStatus())
                .isEqualTo(com.wwa.deploymentagent.contracts.enums.ReviewStatus.Pending_Review);
    }

    // ─── getById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById returns the flow when it exists")
    void getById_exists_returnsFlow() {
        ReleaseFlow rf = helper.seedReleaseFlow();

        ReleaseFlow found = releaseFlowService.getById(rf.getId());

        assertThat(found.getId()).isEqualTo(rf.getId());
        assertThat(found.getProjectId()).isEqualTo(rf.getProjectId());
    }

    @Test
    @DisplayName("getById throws NotFoundAppException for unknown ID")
    void getById_notFound_throws() {
        assertThatThrownBy(() -> releaseFlowService.getById("non-existent"))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── getByIdWithFullHierarchy (T4.3) ─────────────────────────────────────

    @Test
    @DisplayName("getByIdWithFullHierarchy loads requests and tasks in one query")
    void getByIdWithFullHierarchy_loadsHierarchy() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req);

        ReleaseFlow loaded = releaseFlowService.getByIdWithFullHierarchy(rf.getId());

        assertThat(loaded.getRequests()).hasSize(1);
        assertThat(loaded.getRequests().get(0).getId()).isEqualTo(req.getId());
        assertThat(loaded.getRequests().get(0).getTasks()).hasSize(1);
        assertThat(loaded.getRequests().get(0).getTasks().get(0).getId()).isEqualTo(task.getId());
    }

    @Test
    @DisplayName("getByIdWithFullHierarchy throws NotFoundAppException for unknown ID")
    void getByIdWithFullHierarchy_notFound_throws() {
        assertThatThrownBy(() -> releaseFlowService.getByIdWithFullHierarchy("non-existent"))
                .isInstanceOf(NotFoundAppException.class);
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list with no filters returns all flows")
    void list_noFilters_returnsAll() {
        helper.seedReleaseFlow(); // PROJ-001
        releaseFlowService.create("PROJ-002", "Other Project", "sit-proj002-0001", "sit-proj002-0001", Stage.SIT);

        Page<ReleaseFlow> result = releaseFlowService.list(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("list filtered by projectId returns matching flows only")
    void list_filteredByProjectId() {
        helper.seedReleaseFlow(); // projectId = "PROJ-001"
        releaseFlowService.create("OTHER-PROJ", "Other", "sit-other-0001", "sit-other-0001", Stage.SIT);

        Page<ReleaseFlow> result = releaseFlowService.list("PROJ-001", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).allMatch(rf -> rf.getProjectId().equals("PROJ-001"));
    }

    @Test
    @DisplayName("list filtered by flowStatus returns matching flows only")
    void list_filteredByFlowStatus() {
        helper.seedReleaseFlow(); // status = Pending
        releaseFlowService.create("PROJ-R", "Running", "sit-running-0001", "sit-running-0001", Stage.SIT);
        // Mark second flow as Running
        releaseFlowRepository.findFirstByProjectId("PROJ-R").ifPresent(rf -> {
            rf.setFlowStatus(FlowStatus.Running);
            releaseFlowRepository.save(rf);
        });

        Page<ReleaseFlow> pending = releaseFlowService.list(null, FlowStatus.Pending, null, PageRequest.of(0, 10));
        assertThat(pending.getContent()).allMatch(rf -> rf.getFlowStatus() == FlowStatus.Pending);
    }

    @Test
    @DisplayName("list filtered by stage returns matching flows only")
    void list_filteredByStage() {
        helper.seedReleaseFlow(); // stage = SIT
        releaseFlowService.create("UAT-PROJ", "UAT Project", "uat-uatproj-0001", "uat-uatproj-0001", Stage.UAT);

        Page<ReleaseFlow> sitFlows = releaseFlowService.list(null, null, Stage.SIT, PageRequest.of(0, 10));
        assertThat(sitFlows.getContent()).allMatch(rf -> rf.getCurrentStage() == Stage.SIT);
    }

    @Test
    @DisplayName("list filtered by application, SNOW Group, and agent returns matching flows only")
    void list_filteredByScopeFields() {
        ReleaseFlow matchingFlow = helper.seedReleaseFlow();
        Request matchingRequest = helper.seedRequest(matchingFlow);
        matchingRequest.setApplication("AMH HCC");
        matchingRequest.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        matchingRequest.setAgent("Deployment Agent");
        requestRepository.save(matchingRequest);

        ReleaseFlow otherFlow = releaseFlowService.create(
                "PROJ-002", "PowerCARD", "sit-powercard-0001", "sit-powercard-0001", Stage.SIT);
        Request otherRequest = helper.seedRequest(otherFlow);
        otherRequest.setApplication("PowerCARD");
        otherRequest.setSnowGroup("HTSA-CSI-CARD-PRD");
        otherRequest.setAgent("PowerCARD Agent");
        requestRepository.save(otherRequest);

        Page<ReleaseFlow> result = releaseFlowService.list(
                null,
                null,
                null,
                "AMH",
                "HTSA-CSI-HCC",
                "Deployment",
                PageRequest.of(0, 10),
                false);

        assertThat(result.getContent())
                .extracting(ReleaseFlow::getProjectId)
                .containsExactly("PROJ-001");
    }

    @Test
    @DisplayName("list restricts non-global users to release flows inside their Application / SNOW Group scope")
    void list_respectsUserScope() {
        ReleaseFlow matchingFlow = helper.seedReleaseFlow();
        Request matchingRequest = helper.seedRequest(matchingFlow);
        matchingRequest.setApplication("AMH HCC");
        matchingRequest.setSnowGroup("HTSA-CSI-HCC-AMH-PRJ");
        requestRepository.save(matchingRequest);

        ReleaseFlow hiddenFlow = releaseFlowService.create(
                "PROJ-002", "PowerCARD", "sit-powercard-0001", "sit-powercard-0001", Stage.SIT);
        Request hiddenRequest = helper.seedRequest(hiddenFlow);
        hiddenRequest.setApplication("PowerCARD");
        hiddenRequest.setSnowGroup("HTSA-CSI-CARD-PRD");
        requestRepository.save(hiddenRequest);

        UserContext scopedUser = new UserContext(
                "emp-admin-001",
                "DEVOPS_ADMIN",
                List.of("DEVOPS_ADMIN"),
                Set.of("access.manage"),
                "Scoped Admin",
                List.of(new AccessScope("AMH HCC", "HTSA-CSI-HCC-AMH-PRJ"))
        );

        Page<ReleaseFlow> result = releaseFlowService.list(
                null,
                null,
                null,
                null,
                null,
                null,
                scopedUser,
                PageRequest.of(0, 10),
                false
        );

        assertThat(result.getContent())
                .extracting(ReleaseFlow::getProjectId)
                .containsExactly("PROJ-001");
    }

    // ─── advanceStage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("advanceStage moves SIT → UAT and sets status to Running")
    void advanceStage_sitToUat() {
        ReleaseFlow rf = helper.seedReleaseFlow(); // SIT
        assertThat(rf.getCurrentStage()).isEqualTo(Stage.SIT);

        releaseFlowService.advanceStage(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getCurrentStage()).isEqualTo(Stage.UAT);
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Running);
    }

    @Test
    @DisplayName("advanceStage moves UAT → PROD")
    void advanceStage_uatToProd() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage(Stage.UAT);
        releaseFlowRepository.save(rf);

        releaseFlowService.advanceStage(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getCurrentStage()).isEqualTo(Stage.PROD);
    }

    @Test
    @DisplayName("advanceStage on PROD does nothing (already at final stage)")
    void advanceStage_prod_noOp() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        rf.setCurrentStage(Stage.PROD);
        rf.setFlowStatus(FlowStatus.Running);
        releaseFlowRepository.save(rf);

        releaseFlowService.advanceStage(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getCurrentStage()).isEqualTo(Stage.PROD);
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Running); // unchanged
    }

    // ─── recomputeAndPersistStatus ────────────────────────────────────────────

    @Test
    @DisplayName("recompute aggregates Pending tasks to Pending flow status")
    void recomputeStatus_allPending_flowPending() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req, TaskStatus.Pending);

        releaseFlowService.recomputeAndPersistStatus(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Pending);
    }

    @Test
    @DisplayName("recompute aggregates Running tasks to Running flow status")
    void recomputeStatus_runningTask_flowRunning() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req, TaskStatus.Executing);

        releaseFlowService.recomputeAndPersistStatus(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Running);
    }

    @Test
    @DisplayName("recompute aggregates all-Approved tasks to Completed flow status")
    void recomputeStatus_allApproved_flowCompleted() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req, TaskStatus.Approved);

        // Make request reflect completed state
        req.setRequestStatus(RequestStatus.Completed);
        requestRepository.save(req);

        releaseFlowService.recomputeAndPersistStatus(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Completed);
    }

    @Test
    @DisplayName("recompute with no requests leaves flow status unchanged")
    void recomputeStatus_noRequests_unchanged() {
        ReleaseFlow rf = helper.seedReleaseFlow(); // Pending, no requests

        releaseFlowService.recomputeAndPersistStatus(rf.getId());

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        // No requests → stageStatuses list is empty → aggregation returns Pending
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Pending);
    }

    @Test
    @DisplayName("archiveRequestRundown archives the release flow when the final active request is archived")
    void archiveRequestRundown_lastRequest_archivesFlow() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        RequestArchiveResultDto result = releaseFlowService.archiveRequestRundown(
                rf.getId(), req.getId(), new UserContext("admin-001", "DEVOPS_ADMIN"));

        ReleaseFlow archivedFlow = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        Request archivedRequest = requestRepository.findById(req.getId()).orElseThrow();

        assertThat(result.requestArchived()).isTrue();
        assertThat(result.releaseFlowArchived()).isTrue();
        assertThat(result.activeRequestCount()).isZero();
        assertThat(archivedFlow.getArchivedAt()).isNotNull();
        assertThat(archivedRequest.getArchivedAt()).isNotNull();
    }

    @Test
    @DisplayName("archiveRequestRundown realigns the remaining flow state when other active requests still exist")
    void archiveRequestRundown_updatesCurrentStageAndStatuses() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request sit = helper.seedRequest(rf, Stage.SIT, RequestStatus.Pending);
        Request uat = helper.seedRequest(rf, Stage.UAT, RequestStatus.Pending);

        RequestArchiveResultDto result = releaseFlowService.archiveRequestRundown(
                rf.getId(), sit.getId(), new UserContext("admin-001", "DEVOPS_ADMIN"));

        ReleaseFlow updated = releaseFlowRepository.findById(rf.getId()).orElseThrow();
        assertThat(result.requestArchived()).isTrue();
        assertThat(result.releaseFlowArchived()).isFalse();
        assertThat(result.activeRequestCount()).isEqualTo(1);
        assertThat(updated.getCurrentStage()).isEqualTo(Stage.UAT);
        assertThat(updated.getFlowStatus()).isEqualTo(FlowStatus.Pending);
        assertThat(updated.getReviewStatus()).isEqualTo(ReviewStatus.Pending_Review);
        assertThat(requestRepository.findById(sit.getId())).get().extracting(Request::getArchivedAt).isNotNull();
        assertThat(requestRepository.findById(uat.getId())).isPresent();
    }

    @Test
    @DisplayName("purgeArchivedRequestRundown permanently removes the final archived request and its flow")
    void purgeArchivedRequestRundown_lastArchivedRequest_deletesFlow() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);
        UserContext admin = new UserContext("admin-001", "DEVOPS_ADMIN");

        releaseFlowService.archiveRequestRundown(rf.getId(), req.getId(), admin);
        RequestPurgeResultDto result = releaseFlowService.purgeArchivedRequestRundown(rf.getId(), req.getId(), admin);

        assertThat(result.releaseFlowDeleted()).isTrue();
        assertThat(result.remainingRequestCount()).isZero();
        assertThat(result.activeRequestCount()).isZero();
        assertThat(requestRepository.findById(req.getId())).isEmpty();
        assertThat(releaseFlowRepository.findById(rf.getId())).isEmpty();
    }

    @Test
    @DisplayName("purgeArchivedRequestRundown rejects active rundowns")
    void purgeArchivedRequestRundown_activeRequest_rejected() {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        helper.seedTask(req);

        assertThatThrownBy(() -> releaseFlowService.purgeArchivedRequestRundown(
                rf.getId(), req.getId(), new UserContext("admin-001", "DEVOPS_ADMIN")))
                .isInstanceOf(ValidationAppException.class)
                .hasMessageContaining("Only archived rundowns can be permanently deleted.");
    }
}
