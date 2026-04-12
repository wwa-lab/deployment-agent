package com.wwa.deploymentagent.agents.deployment.domain;

import com.wwa.deploymentagent.contracts.dto.ReleaseFlowDetailDto;
import com.wwa.deploymentagent.contracts.dto.ReleaseFlowListItemDto;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DeploymentStitchingService")
class DeploymentStitchingServiceTest {

    @Autowired private DeploymentStitchingService stitchingService;
    @Autowired private ReleaseFlowService releaseFlowService;
    @Autowired private RequestRepository requestRepository;
    @Autowired private TestDataHelper helper;

    @Test
    @DisplayName("listStitchedSummaries groups SIT/UAT flows from the same family into one row")
    void listStitchedSummaries_stitchesStagePrefixedFamily() {
        ReleaseFlow sitFlow = releaseFlowService.create(
                "PROJ-STITCH", "Stitched Project", "sit-stitch-0001", "sit-stitch-0001", "SIT");
        Request sitRequest = helper.seedRequest(sitFlow, "SIT", RequestStatus.Completed);
        sitRequest.setApplication("AMH HCC");
        requestRepository.save(sitRequest);

        ReleaseFlow uatFlow = releaseFlowService.create(
                "PROJ-STITCH", "Stitched Project", "uat-stitch-0001", "uat-stitch-0001", "UAT");
        Request uatRequest = helper.seedRequest(uatFlow, "UAT", RequestStatus.Pending);
        uatRequest.setApplication("AMH HCC");
        requestRepository.save(uatRequest);

        Page<ReleaseFlowListItemDto> page = stitchingService.listStitchedSummaries(
                null, null, null, null, null, null, null, "latest",
                PageRequest.of(0, 20), false);

        assertThat(page.getContent()).hasSize(1);
        ReleaseFlowListItemDto row = page.getContent().get(0);
        assertThat(row.stitched()).isTrue();
        assertThat(row.linkedReleaseCount()).isEqualTo(2);
        assertThat(row.stageStatuses()).containsKey("SIT");
        assertThat(row.stageStatuses()).containsKey("UAT");
        assertThat(row.stagesPresent()).contains("SIT", "UAT");
    }

    @Test
    @DisplayName("listStitchedSummaries keeps generated blank-identifier uploads as separate rows")
    void listStitchedSummaries_doesNotStitchGeneratedSequenceIds() {
        ReleaseFlow sitFlow = releaseFlowService.create(
                "PROJ-SEPARATE", "Separate Project", "sit-separateproject-0001", "sit-separateproject-0001", "SIT");
        Request sitRequest = helper.seedRequest(sitFlow, "SIT", RequestStatus.Completed);
        sitRequest.setApplication("AMH HCC");
        requestRepository.save(sitRequest);

        ReleaseFlow uatFlow = releaseFlowService.create(
                "PROJ-SEPARATE", "Separate Project", "uat-separateproject-0002", "uat-separateproject-0002", "UAT");
        Request uatRequest = helper.seedRequest(uatFlow, "UAT", RequestStatus.Pending);
        uatRequest.setApplication("AMH HCC");
        requestRepository.save(uatRequest);

        Page<ReleaseFlowListItemDto> page = stitchingService.listStitchedSummaries(
                "PROJ-SEPARATE", null, null, null, null, null, null, "latest",
                PageRequest.of(0, 20), false);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent())
                .allSatisfy(row -> {
                    assertThat(row.stitched()).isFalse();
                    assertThat(row.linkedReleaseCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("getStitchedDetail returns a stitched detail for explicit linked flow ids")
    void getStitchedDetail_returnsStitchedDetail() {
        ReleaseFlow sitFlow = releaseFlowService.create(
                "PROJ-DETAIL", "Detail Project", "sit-detail-0001", "sit-detail-0001", "SIT");
        helper.seedRequest(sitFlow, "SIT", RequestStatus.Completed);

        ReleaseFlow uatFlow = releaseFlowService.create(
                "PROJ-DETAIL", "Detail Project", "uat-detail-0001", "uat-detail-0001", "UAT");
        helper.seedRequest(uatFlow, "UAT", RequestStatus.Pending);

        ReleaseFlowDetailDto detail = stitchingService.getStitchedDetail(
                sitFlow.getId(), List.of(sitFlow.getId(), uatFlow.getId()), false, null);

        assertThat(detail).isNotNull();
        assertThat(detail.stitched()).isTrue();
        assertThat(detail.linkedReleaseCount()).isEqualTo(2);
    }
}
