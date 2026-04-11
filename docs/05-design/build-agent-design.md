# Detailed Design: Build Agent

**Date:** 2026-04-11
**Status:** Draft (v3, aligned with `build-agent-architecture.md` v3)
**Scope:** This document carries the **implementation-level specifics** that `build-agent-architecture.md` deliberately deferred per its §Document Scope. It does not re-derive any architectural decision. If a statement here appears to conflict with an architecture PL-*/BA-* decision, architecture wins.
**Source:** `build-agent-architecture.md` (primary, structural decisions), `build-agent-spec.md` (product intent), `build-agent-tasks.md` (downstream).
**Supersedes:** v2 (2026-04-11). v2 was structured around the four v2-era surgical shared-contract changes. v3 replaces it with code skeletons and test matrices for the Agent Module refactor.

---

## 1. Scope Contract

### 1.1 What this document contains
- Full class signatures for every new class
- JPA attribute-level diffs for every modified entity
- Record signatures for every modified DTO
- Algorithm specifications for migrated or signature-changed services
- Per-module unit and integration test matrices
- ArchUnit fitness test specifications
- Commit-ordered implementation sequence
- LOC estimates per module

### 1.2 What this document does NOT contain
- Architectural rationale for any decision — see `build-agent-architecture.md` §Architecture Decisions
- Product intent — see `build-agent-spec.md`
- Route prefix inventory and breaking-change mapping — see architecture §API Boundaries
- Spec Delta — see architecture §Spec Delta
- Per-task effort breakdown — see `build-agent-tasks.md`

### 1.3 File-path convention

All file paths in this document use the **post-refactor target location** under `com.wwa.deploymentagent.platform.*` for Platform Core code and `com.wwa.deploymentagent.agents.<name>.*` for Agent Module code. Before this delivery's Phase H migration, Platform Core code currently lives under flat packages (`com.wwa.deploymentagent.domain.*`, `com.wwa.deploymentagent.contracts.*`, `com.wwa.deploymentagent.web.*`). When a path in this document is prefixed with `platform/` or `agents/`, read it as "the location after Phase H has landed". Where the pre-refactor location matters for a specific line edit (e.g. `ReleaseFlowProgressionService.java:72` which is the current `domain/decision/` location), it is stated explicitly alongside the target path.

---

## 2. Module Design — Platform Core

Eleven platform-level modules (M1–M11) constitute Part A of the delivery. Each is presented with: **purpose**, **signature**, **call-site impact**, and **test matrix**.

### M1. `StagePipeline` Interface + `StagePipelineRegistry`

**Purpose:** Per-agent stage ordering, resolved at call time through a Platform Core registry keyed by `agentId`. Owned by architecture PL-4.

**Why a registry, not a method parameter:** The v3 design originally proposed passing `StagePipeline` as a method parameter to `progressAfterDecision`. That approach does not survive contact with the real caller graph — `progressAfterDecision` is invoked from `RecordResultService`, `AutoExecutionService`, and `ExternalExecutionMonitorService` (the last of which runs on a Jenkins/Ansible callback thread with no HTTP context). Threading a per-agent pipeline through every intermediate service would push agent semantics deep into Platform Core and violate PL-2. The registry pattern resolves the pipeline exactly once at the point where it is needed (inside `progressAfterDecision`), derived from the task's parent request's `agent` column, and leaves every other Platform Core service agent-agnostic.

#### M1.1 Interface

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/StagePipeline.java`

```java
package com.wwa.deploymentagent.platform.domain;

import java.util.List;
import java.util.Optional;

public interface StagePipeline {

    /**
     * The agent ID this pipeline belongs to. Used by {@link StagePipelineRegistry}
     * to build the agent → pipeline map at application startup. Must be a stable
     * string constant drawn from {@code AgentId}.
     */
    String agentId();

    /**
     * Returns the next stage after {@code currentStage}, or {@link Optional#empty()}
     * if {@code currentStage} is the terminal stage in this pipeline.
     *
     * @throws IllegalArgumentException if {@code currentStage} is not a declared
     *         member of this pipeline's {@link #orderedStages()}. Fail-loud
     *         intentional: a mis-routed progression call (e.g. passing a "SIT" flow
     *         through {@code BuildStagePipeline}) MUST crash visibly rather than
     *         silently be treated as terminal. Silent terminal behavior was the
     *         "unknown = terminal" design that an earlier draft proposed and that
     *         reviewer feedback flagged as silent corruption.
     */
    Optional<String> next(String currentStage);

    /**
     * True if {@code stage} is terminal (has no successor) in this pipeline.
     *
     * @throws IllegalArgumentException if {@code stage} is not a declared member
     *         of this pipeline's {@link #orderedStages()}. Same fail-loud
     *         rationale as {@link #next(String)}.
     */
    boolean isTerminal(String stage);

    /** All stages owned by this pipeline, in declared order. Non-empty, no duplicates. */
    List<String> orderedStages();
}
```

**Design constraints:**
- Stateless; `@Component` implementations are singletons.
- **Fail-loud on unknown stages:** `next(unknownStage)` and `isTerminal(unknownStage)` throw `IllegalArgumentException`. Passing a wrong-pipeline stage is a routing bug, not a terminal-state signal.
- `agentId()` must return a value from `AgentId` constants and must be unique across all `@Component` implementations in the Spring context.

#### M1.2 Registry

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/StagePipelineRegistry.java`

```java
package com.wwa.deploymentagent.platform.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Platform-level registry that maps {@code agentId} to its {@link StagePipeline}.
 * Spring auto-injects every {@code StagePipeline} @Component implementation at
 * startup; the registry builds an immutable map from each pipeline's
 * {@link StagePipeline#agentId()}.
 *
 * <p>Duplicate agentId detection and missing-agent lookup are fail-loud:
 * startup fails with IllegalStateException on duplicate; runtime lookup throws
 * on missing agent. This is the single source of truth that
 * {@link com.wwa.deploymentagent.platform.domain.decision.ReleaseFlowProgressionService}
 * uses to resolve the correct pipeline for a release flow.
 */
@Component
public class StagePipelineRegistry {

    private final Map<String, StagePipeline> byAgent;

    public StagePipelineRegistry(List<StagePipeline> pipelines) {
        this.byAgent = pipelines.stream()
                .collect(Collectors.toUnmodifiableMap(
                        StagePipeline::agentId,
                        p -> p,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate StagePipeline for agentId " + a.agentId()
                                            + ": " + a.getClass().getName()
                                            + " and " + b.getClass().getName());
                        }));
    }

    /**
     * Resolve the pipeline for a given agent.
     *
     * @throws IllegalStateException if no pipeline is registered for the given
     *         agent. This indicates a configuration gap (a new agent was added
     *         without a corresponding StagePipeline @Component) or a data-integrity
     *         gap (a Request row carries an unrecognized agentId value).
     */
    public StagePipeline forAgent(String agentId) {
        StagePipeline pipeline = byAgent.get(agentId);
        if (pipeline == null) {
            throw new IllegalStateException(
                    "No StagePipeline registered for agentId: " + agentId);
        }
        return pipeline;
    }
}
```

#### M1.3 Test matrix

**`StagePipelineContractTest`** (parameterized across all 3 implementations):

| # | Case | Assertion |
|---|---|---|
| 1 | `next(firstStage)` where pipeline has ≥2 stages | returns `Optional.of(secondStage)` |
| 2 | `next(firstStage)` where pipeline has 1 stage | returns `Optional.empty()` |
| 3 | `next(lastStage)` | returns `Optional.empty()` |
| 4 | `next("totally-unknown")` | **throws `IllegalArgumentException`** (fail-loud) |
| 5 | `next("")` | throws `IllegalArgumentException` |
| 6 | `next(null)` | throws `IllegalArgumentException` or `NullPointerException` — impl must pick one consistently |
| 7 | `isTerminal(lastStage)` | returns `true` |
| 8 | `isTerminal(firstStage)` where pipeline has ≥2 stages | returns `false` |
| 9 | `isTerminal("totally-unknown")` | **throws `IllegalArgumentException`** |
| 10 | `orderedStages()` | non-empty, immutable, no duplicates |
| 11 | `agentId()` | returns the agent's `AgentId` constant, non-null, non-blank |

**`StagePipelineRegistryTest`**:

| # | Case | Assertion |
|---|---|---|
| 1 | 3 pipelines registered, `forAgent("deployment-agent")` | returns `DeploymentStagePipeline` instance |
| 2 | `forAgent("build-agent")` | returns `BuildStagePipeline` instance |
| 3 | `forAgent("totally-unknown")` | throws `IllegalStateException` |
| 4 | Two `@Component` pipelines with same `agentId()` | Spring context startup fails with `IllegalStateException` (duplicate detection) |

---

### M2. `AgentBoundaryGuard` (Platform-Level)

**Purpose:** Controller-layer data isolation (architecture PL-9). Promoted from v2's Build-Agent-only helper.

**File:** `src/main/java/com/wwa/deploymentagent/platform/web/security/AgentBoundaryGuard.java`

```java
package com.wwa.deploymentagent.platform.web.security;

import com.wwa.deploymentagent.platform.domain.releaseflow.ReleaseFlowRepository;
import com.wwa.deploymentagent.platform.domain.releaseflow.Request;
import com.wwa.deploymentagent.platform.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.platform.domain.task.Task;
import com.wwa.deploymentagent.platform.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentBoundaryGuard {

    private final TaskRepository taskRepository;
    private final RequestRepository requestRepository;
    private final ReleaseFlowRepository releaseFlowRepository;

    @Transactional(readOnly = true)
    public void assertTaskBelongsToAgent(String taskId, String expectedAgent) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundAppException("Task", taskId));
        Request request = task.getRequest();
        if (request == null || !expectedAgent.equals(request.getAgent())) {
            throw new NotFoundAppException("Task", taskId);
        }
    }

    @Transactional(readOnly = true)
    public void assertRequestBelongsToAgent(String requestId, String expectedAgent) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        if (!expectedAgent.equals(request.getAgent())) {
            throw new NotFoundAppException("Request", requestId);
        }
    }

    @Transactional(readOnly = true)
    public void assertFlowBelongsToAgent(String flowId, String expectedAgent) {
        releaseFlowRepository.findById(flowId)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", flowId));
        List<Request> requests = requestRepository.findByReleaseFlowIds(List.of(flowId), true);
        boolean hasAgentRequest = requests.stream()
                .anyMatch(r -> expectedAgent.equals(r.getAgent()));
        if (!hasAgentRequest) {
            throw new NotFoundAppException("ReleaseFlow", flowId);
        }
    }
}
```

**Design notes:**
- `@Transactional(readOnly = true)` on each assertion method is mandatory. Without it, each call opens its own Hibernate session (OSIV is disabled per `application.properties:7`), and lazy `task.getRequest()` navigation would fail.
- Mismatches throw `NotFoundAppException`, mapped to HTTP 404 by the existing `GlobalExceptionHandler`. No new exception type.
- Flow-level guard uses `findByReleaseFlowIds(..., includeArchived=true)` so that archived-but-agent-owned flows are not mistaken as "not owned by this agent". Archived visibility is a separate concern enforced by the controller.

**Test matrix (`AgentBoundaryGuardTest`):**

| # | Scenario | Expected |
|---|---|---|
| 1 | Task exists, `request.agent == expectedAgent` | Returns normally |
| 2 | Task exists, `request.agent != expectedAgent` | `NotFoundAppException` |
| 3 | Task exists, `request == null` | `NotFoundAppException` |
| 4 | Task exists, `request.agent == null` (legacy) | `NotFoundAppException` |
| 5 | Task does not exist | `NotFoundAppException` |
| 6 | Request exists, `agent == expectedAgent` | Returns normally |
| 7 | Request exists, `agent != expectedAgent` | `NotFoundAppException` |
| 8 | Request does not exist | `NotFoundAppException` |
| 9 | Flow exists with ≥1 matching-agent request | Returns normally |
| 10 | Flow exists with 0 matching-agent requests | `NotFoundAppException` |
| 11 | Flow exists with only archived matching-agent requests | Returns normally |
| 12 | Flow does not exist | `NotFoundAppException` |

---

### M3. `ReleaseFlowService` Trim-Down

**Purpose:** Platform `ReleaseFlowService` sheds stitching and switches all stage parameters from `Stage` enum to `String`. `advanceStage(...)` internally uses `StagePipelineRegistry` (no signature change). Architecture PL-3, PL-5.

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/releaseflow/ReleaseFlowService.java` (moves from current `domain/releaseflow/` under `platform/domain/releaseflow/` in Phase H)

**Method signature changes:**

| Before (v2) | After (v3) | Notes |
|---|---|---|
| `Page<ReleaseFlowListItemDto> listStitchedSummaries(String projectId, String releaseId, Stage stage, ..., String agent, Pageable pageable)` | **removed** — moves to `DeploymentStitchingService.listStitchedSummaries` (M12) | Only Deployment Agent's controllers called this |
| `ReleaseFlowDetailDto getStitchedDetail(String releaseFlowId, List<String> linkedFlowIds, ...)` | **removed** — moves to `DeploymentStitchingService.getStitchedDetail` | Same |
| *(new)* | `Page<ReleaseFlow> listByAgent(String agentId, ReleaseFlowFilter filter, Pageable pageable)` | Agent-scoped list, no stitching |
| `ReleaseFlow getById(String id, boolean includeArchived)` | unchanged | |
| `List<Request> findRequestsForFlow(String releaseFlowId, boolean includeArchived)` | unchanged | |
| `ReleaseFlow create(String projectId, String projectName, String releaseId, String normalizedReleaseId, Stage stage)` | `ReleaseFlow create(String projectId, String projectName, String releaseId, String normalizedReleaseId, String stage)` | `Stage` → `String` |
| any other method that accepted `Stage stage` | now accepts `String stage` | Mechanical |

**`ReleaseFlowFilter` value class (new):**

```java
public record ReleaseFlowFilter(
    String projectId,
    String releaseId,
    String stage,          // String, not Stage enum
    FlowStatus flowStatus,
    boolean includeArchived
) {}
```

**Call-site impact:** All Deployment Agent, Testing Agent, and Build Agent controllers that previously called `listStitchedSummaries(...)` must choose:
- Deployment Agent: call `deploymentStitchingService.listStitchedSummaries(...)` (wraps `listByAgent` and applies family-key grouping).
- Testing Agent and Build Agent: call `releaseFlowService.listByAgent(...)` directly.

**Test matrix additions:**

| Test | Purpose |
|---|---|
| `ReleaseFlowServiceTest.listByAgent_scopesByAgentColumn` | `listByAgent("deployment-agent")` returns only deployment-agent flows |
| `ReleaseFlowServiceTest.listByAgent_excludesNullAgent` | Legacy `agent IS NULL` rows are invisible |
| `ReleaseFlowServiceTest.listByAgent_filtersByStageString` | `filter.stage = "UAT"` restricts by String column value |
| `ReleaseFlowServiceTest.getById_worksForAnyAgent` | `getById` is agent-agnostic; controllers layer on the guard |

---

### M4. `ReleaseFlowProgressionService` — Registry Lookup (Body Change, Signature Unchanged)

**Purpose:** Replace the hardcoded `Stage.next()` call with a `StagePipelineRegistry` lookup. Architecture PL-4.

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/decision/ReleaseFlowProgressionService.java` (current location: `domain/decision/ReleaseFlowProgressionService.java`; moves under `platform/domain/decision/` in Phase H of the implementation sequence).

**Current real signature (verified against `ReleaseFlowProgressionService.java:49`):**

```java
@Transactional
public void progressAfterDecision(String taskId) { ... }
```

The method takes a single `taskId` and loads `task → request → releaseFlow` internally. There is no `releaseFlowId`, `DecisionOutcome`, or `UserContext` parameter; an earlier v3 draft misrepresented the signature.

**Signature in v3: unchanged.** Five call sites — `DecisionController.java:41`, `TestingAgentTaskController.java:133`, `RecordResultService.java:98`, `AutoExecutionService.java:159`, `ExternalExecutionMonitorService.java:207` — stay exactly as they are. None of them need to know about `StagePipeline`.

**Constructor change (adds `StagePipelineRegistry`):**

```java
// Before
public ReleaseFlowProgressionService(
        TaskRepository taskRepository,
        RequestRepository requestRepository,
        ReleaseFlowService releaseFlowService,
        ReleaseFlowRepository releaseFlowRepository) { ... }

// After
public ReleaseFlowProgressionService(
        TaskRepository taskRepository,
        RequestRepository requestRepository,
        ReleaseFlowService releaseFlowService,
        ReleaseFlowRepository releaseFlowRepository,
        StagePipelineRegistry stagePipelineRegistry) { ... }   // ← new dependency
```

**Method body diff (only the terminal-stage check at `ReleaseFlowProgressionService.java:72` changes):**

```java
// Before (v2 — real code as of 2026-04-11)
if (allTasksTerminal) {
    request.setRequestStatus(RequestStatus.Completed);
    requestRepository.save(request);

    if (releaseFlow.getCurrentStage().next() == null) {
        // PROD completed – mark flow as Completed
        releaseFlowService.recomputeAndPersistStatus(releaseFlow.getId());
    } else {
        // Advance to next stage
        releaseFlowService.advanceStage(releaseFlow.getId());
    }
}
```

```java
// After (v3)
if (allTasksTerminal) {
    request.setRequestStatus(RequestStatus.Completed);
    requestRepository.save(request);

    // Resolve the correct pipeline from the request's agent column.
    // Registry lookup is fail-loud: unknown agent throws IllegalStateException.
    // Unknown current stage (i.e. pipeline does not recognize the flow's stage)
    // throws IllegalArgumentException from the pipeline itself.
    String agentId = request.getAgent();
    StagePipeline pipeline = stagePipelineRegistry.forAgent(agentId);
    String currentStage = releaseFlow.getCurrentStage();   // String, not Stage enum (PL-3)

    if (pipeline.isTerminal(currentStage)) {
        // Terminal stage in this agent's pipeline — mark flow Completed
        releaseFlowService.recomputeAndPersistStatus(releaseFlow.getId());
    } else {
        // Advance to the pipeline's next stage
        releaseFlowService.advanceStage(releaseFlow.getId());
    }
}
```

The `advanceStage(...)` call itself also internally needs the pipeline — it currently relies on `Stage.next()` somewhere in `releaseFlowService`. That call chain also migrates to the registry lookup; see M3 for `ReleaseFlowService` changes.

**Why the method signature does not change:** The alternative — passing `StagePipeline stagePipeline` as a method parameter — was rejected because `progressAfterDecision` is called from three Platform Core services (`RecordResultService`, `AutoExecutionService`, `ExternalExecutionMonitorService`) in addition to two controllers. Threading a per-agent pipeline through `RecordResultService.recordResult(...)` signatures would force RecordResultService and its callers to become agent-aware, which violates PL-2. The registry resolves the pipeline at exactly one point (inside `progressAfterDecision`), from the data that is already loaded (`request.getAgent()`), and leaves every other service agent-agnostic.

**Call-site audit (verified against the current codebase):**

| Call site | File:line | Context | v3 change required |
|---|---|---|---|
| `DecisionController.applyDecision` | `DecisionController.java:41` | HTTP decision apply | **None** (signature unchanged) — controller moves to `agents/deployment/web/` in Phase H but the call itself is unchanged |
| `TestingAgentTaskController` (decision endpoint) | `TestingAgentTaskController.java:133` | HTTP decision apply | **None** — controller moves to `agents/testing/web/` in Phase H |
| `RecordResultService.recordResult` | `RecordResultService.java:98` | Manual result recording completes a task → triggers progression | **None** |
| `AutoExecutionService.submitAutoExecution` | `AutoExecutionService.java:159` | Auto execution terminal sync | **None** |
| `ExternalExecutionMonitorService.processCallback` | `ExternalExecutionMonitorService.java:207` | Jenkins/Ansible callback thread | **None** — critical: this path has no HTTP context, which is why the registry approach is mandatory rather than parameter-threading |

**Test matrix:**

| Test | Assertion |
|---|---|
| `ReleaseFlowProgressionServiceTest.terminalStage_marksCompleted_perAgent` | Parameterized over all 3 agents: PROD terminal for `deployment-agent`, UAT terminal for `testing-agent`, DEV terminal for `build-agent` — each marks flow Completed via `recomputeAndPersistStatus` |
| `ReleaseFlowProgressionServiceTest.nonTerminalStage_advances` | `deployment-agent` flow at SIT → calls `advanceStage` |
| `ReleaseFlowProgressionServiceTest.unknownAgent_failsLoud` | Request row with `agent = "ghost-agent"` → `IllegalStateException` from registry lookup, flow state unchanged, transaction rolls back |
| `ReleaseFlowProgressionServiceTest.mismatchedStage_failsLoud` | Request with `agent = "build-agent"` but flow's `currentStage = "SIT"` (impossible under normal operation, but covered for data-integrity guarantees) → `IllegalArgumentException` from `pipeline.isTerminal("SIT")` because `BuildStagePipeline` does not declare `"SIT"` |
| `ReleaseFlowProgressionServiceTest.allFiveCallSitesWork` | Integration test exercising each of the 5 callers (decision controller, testing controller, record result, auto execution, monitor callback) and verifying progression completes without a `StagePipeline` parameter anywhere |

**Removed from this design:** The v3 draft's "caller update pattern" (Agent Module controllers threading their own pipeline into `progressAfterDecision`) is deleted. Controllers do NOT know about `StagePipeline`.

---

### M5. `ReleaseFlow` and `Request` Entity Attribute Changes

**Purpose:** JPA attribute type change from `Stage` enum to `String`. Architecture PL-3.

**Files:**
- `src/main/java/com/wwa/deploymentagent/platform/domain/releaseflow/ReleaseFlow.java`
- `src/main/java/com/wwa/deploymentagent/platform/domain/releaseflow/Request.java`

**`ReleaseFlow` diff:**

```java
// Before
@Enumerated(EnumType.STRING)
@Column(name = "current_stage", length = 10, nullable = false)
private Stage currentStage;

// After
@Column(name = "current_stage", length = 10, nullable = false)
private String currentStage;
```

**`Request` diff:**

```java
// Before
@Enumerated(EnumType.STRING)
@Column(name = "stage", length = 10, nullable = false)
private Stage stage;

// After
@Column(name = "stage", length = 10, nullable = false)
private String stage;
```

**Schema impact:** None. DB column is `VARCHAR(10)` on Oracle and H2 already; existing persisted values (`"SIT"`, `"UAT"`, `"PROD"`) remain valid.

**Import impact:** Any import statement referencing `com.wwa.deploymentagent.contracts.enums.Stage` is removed throughout Platform Core. Agent Modules import their own per-agent Stage enum at their controller layer only.

**Test regression:** The existing `ReleaseFlowRepositoryIntegrationTest` and `RequestRepositoryIntegrationTest` must continue to pass after changing `.setStage(Stage.SIT)` to `.setStage("SIT")`. A one-time mechanical refactor.

---

### M6. `ReleaseFlowListItemDto` Generic Stage Map

**Purpose:** Replace fixed positional stage fields with a generic Map. Architecture PL-7.

**File:** `src/main/java/com/wwa/deploymentagent/platform/contracts/dto/ReleaseFlowListItemDto.java`

**New record signature:**

```java
public record ReleaseFlowListItemDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        String currentStage,                        // ← was Stage enum
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        Instant archivedAt,
        String archivedBy,
        String application,
        String snowGroup,
        String agent,
        String owner,
        Map<String, RequestStatus> stageStatuses,   // ← new
        Set<String> stagesPresent,                  // ← new
        boolean stitched,
        int linkedReleaseCount,
        List<String> linkedReleaseIds,
        List<String> linkedReleaseFlowIds
) {
    public static final String ATTEMPT_VIEW_LATEST = "latest";
    public static final String ATTEMPT_VIEW_HISTORY = "history";

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests) {
        return from(rf, requests, ATTEMPT_VIEW_LATEST);
    }

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests, String attemptView) {
        Request scopeRequest = scopeRequestFor(rf, requests);
        Set<String> observedStages = requests.stream()
                .filter(r -> r.getArchivedAt() == null)
                .map(Request::getStage)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, RequestStatus> stageStatuses = observedStages.stream()
                .collect(Collectors.toUnmodifiableMap(
                        stage -> stage,
                        stage -> requestStatusFor(requests, stage, attemptView)));
        return new ReleaseFlowListItemDto(
                rf.getId(),
                rf.getProjectId(),
                rf.getProjectName(),
                rf.getReleaseId(),
                rf.getNormalizedReleaseId(),
                rf.getCurrentStage(),
                rf.getFlowStatus(),
                rf.getReviewStatus(),
                rf.getArchivedAt(),
                rf.getArchivedBy(),
                scopeRequest != null && scopeRequest.getApplication() != null
                        ? scopeRequest.getApplication()
                        : rf.getProjectName(),
                scopeRequest != null ? scopeRequest.getSnowGroup() : null,
                scopeRequest != null ? scopeRequest.getAgent() : null,
                scopeRequest != null ? scopeRequest.getOwner() : null,
                stageStatuses,
                observedStages,
                false,
                1,
                List.of(rf.getReleaseId()),
                List.of(rf.getId())
        );
    }

    // requestStatusFor(...) is parameterized on String stage instead of Stage enum.
    // scopeRequestFor(...) uses String.equals instead of enum reference equality.
    // Helper bodies otherwise identical to v2.
}
```

**Test matrix (`ReleaseFlowListItemDtoTest`):**

| Input flow | Assertions |
|---|---|
| DEV-only requests | `stageStatuses.keySet() == {"DEV"}`, `stagesPresent == {"DEV"}` |
| SIT + UAT requests | `stageStatuses.keySet() == {"SIT", "UAT"}`, `stagesPresent == {"SIT", "UAT"}` |
| Single PROD request | `stageStatuses == {"PROD" → derived status}`, `stagesPresent == {"PROD"}` |
| Empty requests | `stageStatuses.isEmpty()`, `stagesPresent.isEmpty()` |
| Flow with archived DEV requests only | `stagesPresent` excludes archived (current filter on `archivedAt == null`) |

**Breaking change traceability:** All v2 test assertions referencing `item.sitStatus()`, `item.devPresent()`, etc. must be migrated to `item.stageStatuses().get("SIT")` and `item.stagesPresent().contains("DEV")`.

---

### M7. `ReleaseFlowAggregation` with Observed-Stage Iteration

**Purpose:** Replace `Stage.values()` iteration with iteration over observed stage strings. Architecture PL-3 consequence.

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/releaseflow/ReleaseFlowAggregation.java`

**Algorithm diff (conceptual):**

```java
// Before
public static FlowStatus aggregateFlowStatus(List<Request> requests) {
    for (Stage stage : Stage.values()) {
        List<Request> stageRequests = requests.stream()
                .filter(r -> r.getStage() == stage)
                .toList();
        // ... aggregate per stage ...
    }
}

// After
public static FlowStatus aggregateFlowStatus(List<Request> requests) {
    Set<String> observedStages = requests.stream()
            .map(Request::getStage)
            .collect(Collectors.toUnmodifiableSet());
    for (String stage : observedStages) {
        List<Request> stageRequests = requests.stream()
                .filter(r -> stage.equals(r.getStage()))
                .toList();
        // ... aggregate per stage ...
    }
}
```

**Semantic equivalence proof:** The v2 loop iterated over `Stage.values()` and skipped empty buckets; the v3 loop iterates only over non-empty buckets. These are the same set. No observable behavior change.

**Test regression:** `ReleaseFlowAggregationTest` must pass unchanged after the method is rewritten.

---

### M8. `AuditLoggerService` Dynamic `agentName`

**Purpose:** Remove hardcoded `"deployment-agent"` literal and the v2 null fallback. Architecture PL-11.

**File:** `src/main/java/com/wwa/deploymentagent/platform/domain/audit/AuditLoggerService.java`

**Diff (line ~61):**

```java
// Before
entry.setAgent(scope.agent());
entry.setAgentName("deployment-agent");
entry.setSourceSystem("wwa-api");

// After
entry.setAgent(scope.agent());
entry.setAgentName(scope.agent());   // null fallback removed; PL-6 guarantees non-null
entry.setSourceSystem("wwa-api");
```

**Precondition:** Under PL-6, every write path flows through an Agent Module controller that has already forced an agent context. If a test or future path ever calls `AuditLoggerService.log(...)` with `scope.agent() == null`, it is a contract violation. Add an assertion:

```java
if (scope.agent() == null) {
    throw new IllegalStateException(
            "AuditLoggerService.log called with null scope.agent(); "
                    + "every write path must flow through an Agent Module controller. "
                    + "Check that the caller is not bypassing the agent module layer.");
}
```

**Test matrix (`AuditLoggerServiceTest`):**

| Scenario | Expected `agentName` |
|---|---|
| Build Agent request scope (`agent = "build-agent"`) | `"build-agent"` |
| Testing Agent request scope | `"testing-agent"` |
| Deployment Agent request scope | `"deployment-agent"` |
| Null scope agent | `IllegalStateException` |

---

### M9. Platform Capability Controllers at `/api/platform/*`

**Purpose:** Move 5 capability controllers out of the Deployment Agent prefix. Architecture §API Boundaries.

**Files moved to `src/main/java/com/wwa/deploymentagent/platform/web/shared/`:**

| Old location | New location | New class name (unchanged) |
|---|---|---|
| `web/controller/AuthController` | `platform/web/shared/AuthController` | `AuthController` |
| `web/controller/AuditLogController` | `platform/web/shared/AuditLogController` | `AuditLogController` |
| `web/controller/ConfigurationController` | `platform/web/shared/ConfigurationController` | `ConfigurationController` |
| `web/controller/AccessGrantController` | `platform/web/shared/AccessGrantController` | `AccessGrantController` |
| *(from `UploadController`'s template methods)* | `platform/web/shared/TemplateDownloadController` | **new class**, extracted from current Upload controller |

**`@RequestMapping` prefix changes:**

| Class | Old | New |
|---|---|---|
| `AuthController` | `/api/deployment-agent/auth` | `/api/platform/auth` |
| `AuditLogController` | `/api/deployment-agent/audit-logs` | `/api/platform/audit-logs` |
| `ConfigurationController` | `/api/deployment-agent/config` | `/api/platform/config` |
| `AccessGrantController` | `/api/deployment-agent/access-grants` | `/api/platform/access-grants` |
| `TemplateDownloadController` | *(currently lives under upload paths)* | `/api/platform/templates` |

**Required `SecurityConfig.java:36` edit:**

```java
// Before
.requestMatchers("/api/deployment-agent/auth/login").permitAll()

// After
.requestMatchers("/api/platform/auth/login").permitAll()
```

**Gate test (`PlatformRouteMigrationTest`):**

| # | Scenario | Expected |
|---|---|---|
| 1 | Unauthenticated `POST /api/platform/auth/login` with valid credentials | 2xx |
| 2 | Unauthenticated `POST /api/deployment-agent/auth/login` | 401 (old route no longer exists) |
| 3 | Log in at `/api/platform/auth/login`, then `GET /api/deployment-agent/release-flows` with the returned cookie | 2xx (cookie works across prefixes) |
| 4 | Log in at `/api/platform/auth/login`, then `GET /api/build-agent/release-flows` with the same cookie | 2xx |

---

### M10. Frontend Platform Core

**Location:** `frontend/src/platform/`

**New files:**

| File | Responsibility |
|---|---|
| `api/platformClient.ts` | Axios instance with `baseURL: '/api/platform'`; 401 interceptor |
| `api/auth.ts` | `login`, `logout`, `checkSession` bound to `platformClient` |
| `api/audit.ts` | `listAuditLogs` bound to `platformClient` |
| `api/config.ts` | `listConfig`, `listConfigComponents`, `updateConfig`, `updateConfigComponent`, `deleteConfigComponent` bound to `platformClient` |
| `api/accessGrants.ts` | All 6 access grant methods bound to `platformClient` |
| `api/templates.ts` | `downloadTemplate(agentKey)` → `GET /api/platform/templates/{templateId}` |
| `stores/user.ts` | Moved from `frontend/src/stores/` |
| `stores/audit.ts` | Moved from `frontend/src/stores/` |
| `stores/config.ts` | Moved from `frontend/src/stores/` |
| `stores/accessGrants.ts` | Moved from `frontend/src/stores/` |
| `components/UploadDialog.vue` | Moved from `frontend/src/components/` (already agent-agnostic) |
| `components/AgentSummaryView.vue` | **New** — generic summary view; reads `stageStatuses` from DTO |
| `components/AgentDetailView.vue` | **New** — generic detail view; passes through `?linked=` query param |
| `composables/createAgentWorkspace.ts` | **New** — factory (see M11) |
| `composables/createReleaseFlowStore.ts` | **New** — Pinia store factory |
| `composables/createReleaseFlowApi.ts` | **New** — Axios + CRUD factory |
| `config/agentRegistry.ts` | Moved from `frontend/src/config/` |
| `config/agentId.ts` | Moved from `frontend/src/config/` (add `BUILD_AGENT`) |
| `views/LoginView.vue` | Moved from `frontend/src/views/` |
| `views/WwaHomeView.vue` | Moved |
| `views/WorkspaceLayout.vue` | Moved |
| `views/AuditLogView.vue` | Moved |
| `views/ConfigAdminView.vue` | Moved |
| `views/AccessManagementView.vue` | Moved |
| `views/TemplateManagementView.vue` | Moved |

**`platformClient.ts` full content:**

```ts
import axios from 'axios'
import router from '@/router'

const platformClient = axios.create({
  baseURL: '/api/platform',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

platformClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname
      if (currentPath !== '/login') {
        router.push('/login')
      }
    }
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected error occurred'
    return Promise.reject(new Error(message))
  },
)

export default platformClient
```

---

### M11. `createAgentWorkspace` Factory

**Purpose:** Eliminate per-agent copy-paste of store/api/view boilerplate. Architecture PL-8.

**File:** `frontend/src/platform/composables/createAgentWorkspace.ts`

**Public signature:**

```ts
import axios, { type AxiosInstance } from 'axios'
import type { DefineStoreOptions, StoreDefinition } from 'pinia'
import type { Component, RouteRecordRaw } from 'vue-router'

export interface AgentWorkspaceConfig {
  key: string                     // e.g. 'build-agent'
  name: string                    // e.g. 'Build Agent'
  apiBase: string                 // e.g. '/api/build-agent'
  stages: string[]                // e.g. ['DEV']
  supportsStitching: boolean      // false for Build / Testing, true for Deployment
  stageFilter?: 'dropdown' | 'disabled-input'   // default 'dropdown'
}

export interface AgentWorkspace {
  key: string
  name: string
  client: AxiosInstance
  api: AgentReleaseFlowApi
  store: StoreDefinition
  SummaryView: Component
  DetailView: Component
  routes: RouteRecordRaw[]
}

export function createAgentWorkspace(config: AgentWorkspaceConfig): AgentWorkspace {
  const client = createClient(config)
  const api = createReleaseFlowApi(client, config)
  const store = createReleaseFlowStore(config, api)
  const SummaryView = createSummaryView(config, store)
  const DetailView = createDetailView(config, store)
  const routes: RouteRecordRaw[] = [
    { path: `/wwa/${config.key}`, component: SummaryView, name: `${config.key}-summary` },
    { path: `/wwa/${config.key}/release-flows/:id`, component: DetailView, name: `${config.key}-detail`, props: true },
  ]
  return { key: config.key, name: config.name, client, api, store, SummaryView, DetailView, routes }
}
```

**Sub-factory responsibilities:**

| Helper | Location | Job |
|---|---|---|
| `createClient` | inline in `createAgentWorkspace.ts` | Returns `axios.create({ baseURL: config.apiBase, withCredentials: true, ... })` with the same 401 interceptor as `platformClient` |
| `createReleaseFlowApi` | `composables/createReleaseFlowApi.ts` | Returns `{ list, getById, uploadExcel, downloadTemplate, listTasks, getTask, editInput, startManual, submitAuto, recordResult, getExecutions, applyDecision }` — each delegates to `client` with the appropriate URL |
| `createReleaseFlowStore` | `composables/createReleaseFlowStore.ts` | Returns `defineStore(\`${config.key}-release-flow\`, ...)` with state: `list`, `detail`, `loading`, `error`; actions: `fetchList`, `fetchDetail`, `uploadFile` |
| `createSummaryView` | inline | Returns a Vue component that wraps `AgentSummaryView` with config-bound props (`stages`, `stageFilter`, `api`, `store`) |
| `createDetailView` | inline | Returns a Vue component that wraps `AgentDetailView`; reads `route.params.id` and optionally `route.query.linked` if `supportsStitching` is true |

**Critical design note:** `supportsStitching` only affects *whether the DetailView reads `?linked=` and passes it through*. The backend decides what to do with the parameter. The frontend does not know what "stitching" means.

**Test matrix (`createAgentWorkspace.test.ts`):**

| Test | Assertion |
|---|---|
| Build Agent config → `workspace.client.defaults.baseURL` | `'/api/build-agent'` |
| Build Agent config → `workspace.routes.length` | `2` |
| Build Agent config → `workspace.routes[0].path` | `'/wwa/build-agent'` |
| Build Agent config → `workspace.SummaryView` props | `stages: ['DEV']`, `stageFilter: 'disabled-input'` |
| Build Agent DetailView with `?linked=abc` in route | query param **not** passed to API (because `supportsStitching: false`) |
| Deployment Agent DetailView with `?linked=abc` | query param **is** passed to API (because `supportsStitching: true`) |
| Two workspaces with different keys | `store` instances are distinct (Pinia store IDs differ) |

---

## 3. Module Design — Deployment Agent Module

**Location:** `com.wwa.deploymentagent.agents.deployment.*` (backend), `frontend/src/agents/deployment/` (frontend)

### D1. `DeploymentStage` + `DeploymentStagePipeline`

```java
// agents/deployment/domain/DeploymentStage.java
package com.wwa.deploymentagent.agents.deployment.domain;

public enum DeploymentStage {
    SIT, UAT, PROD;

    public static DeploymentStage fromString(String s) {
        return DeploymentStage.valueOf(s);
    }
}
```

```java
// agents/deployment/domain/DeploymentStagePipeline.java
package com.wwa.deploymentagent.agents.deployment.domain;

import com.wwa.deploymentagent.platform.domain.StagePipeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DeploymentStagePipeline implements StagePipeline {

    private static final List<String> ORDER = List.of("SIT", "UAT", "PROD");

    @Override
    public Optional<String> next(String currentStage) {
        int idx = ORDER.indexOf(currentStage);
        if (idx < 0 || idx == ORDER.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(ORDER.get(idx + 1));
    }

    @Override
    public boolean isTerminal(String stage) {
        int idx = ORDER.indexOf(stage);
        return idx < 0 || idx == ORDER.size() - 1;
    }

    @Override
    public List<String> orderedStages() {
        return ORDER;
    }
}
```

### D2. `ReleaseFlowFamilyKey` (Relocated, Regex Unchanged)

**File move:** current `domain/releaseflow/ReleaseFlowFamilyKey.java` (pre-refactor) → target `agents/deployment/domain/ReleaseFlowFamilyKey.java` (post Phase D). Note: this file never lives under `platform/domain/releaseflow/` — it moves directly from its pre-refactor location into the Deployment Agent module.

**Package declaration** updates. **No regex changes.** The existing `STAGE_PREFIX_WITH_SEPARATOR` pattern `^(sit|uat|prod)...` is kept as-is. No `dev` token added.

**Visibility:** The class becomes package-private; only `DeploymentStitchingService` in the same package calls it. Existing public methods become package-private.

### D3. `DeploymentStitchingService`

**File:** `agents/deployment/domain/DeploymentStitchingService.java`

**Purpose:** Owns the stitching algorithm that v2 `ReleaseFlowService.listStitchedSummaries` and `getStitchedDetail` implemented. Migrated verbatim with:
- Package path updated
- Stage parameters that were `Stage` enum → `String`
- Calls `ReleaseFlowService.listByAgent(...)` to fetch the base flows before grouping

**Public surface:**

```java
@Service
@RequiredArgsConstructor
public class DeploymentStitchingService {

    private final ReleaseFlowService releaseFlowService;
    private final RequestRepository requestRepository;

    public Page<ReleaseFlowListItemDto> listStitchedSummaries(
            ReleaseFlowFilter filter,
            String attemptView,
            Pageable pageable,
            UserContext user) { ... }

    public ReleaseFlowDetailDto getStitchedDetail(
            String releaseFlowId,
            List<String> linkedFlowIds,
            boolean includeArchived,
            UserContext user) { ... }
}
```

**Algorithm references:** The body of `listStitchedSummaries` and `getStitchedDetail` is copied from the current `ReleaseFlowService.java:172–300` with the refactor steps described in §4 Algorithms.

### D4. Deployment Agent Backend Controllers

**Files under `agents/deployment/web/`:**

| Class | `@RequestMapping` | Replaces |
|---|---|---|
| `DeploymentReleaseFlowController` | `/api/deployment-agent/release-flows` | `web/controller/ReleaseFlowController` |
| `DeploymentUploadController` | `/api/deployment-agent/upload` | `web/controller/UploadController` |
| `DeploymentTaskController` | `/api/deployment-agent/tasks` | `web/controller/TaskController` |
| `DeploymentDecisionController` | `/api/deployment-agent/tasks/{taskId}/decision` | `web/controller/DecisionController` |

**Important:** Per M4, decision controllers do NOT inject a `StagePipeline`. `ReleaseFlowProgressionService.progressAfterDecision(taskId)` resolves the pipeline from the task's request agent via `StagePipelineRegistry`. Controller constructors include `ReleaseFlowProgressionService` but not `DeploymentStagePipeline`.

**Controller skeleton (`DeploymentReleaseFlowController`):**

```java
@RestController
@RequestMapping("/api/deployment-agent/release-flows")
@RequiredArgsConstructor
public class DeploymentReleaseFlowController {

    private final ReleaseFlowService releaseFlowService;
    private final DeploymentStitchingService stitchingService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public Page<ReleaseFlowListItemDto> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) FlowStatus flowStatus,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "latest") String attemptView,
            Pageable pageable,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        ReleaseFlowFilter filter = new ReleaseFlowFilter(
                projectId, releaseId, stage, flowStatus, includeArchived);
        return stitchingService.listStitchedSummaries(filter, attemptView, pageable, auth.getUserContext());
    }

    @GetMapping("/{id}")
    public ReleaseFlowDetailDto getById(
            @PathVariable String id,
            @RequestParam(required = false) List<String> linked,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        boundaryGuard.assertFlowBelongsToAgent(id, AgentId.DEPLOYMENT_AGENT);
        if (linked != null && !linked.isEmpty()) {
            linked.forEach(linkedId -> boundaryGuard.assertFlowBelongsToAgent(linkedId, AgentId.DEPLOYMENT_AGENT));
            return stitchingService.getStitchedDetail(id, linked, includeArchived, auth.getUserContext());
        }
        ReleaseFlow flow = releaseFlowService.getById(id, includeArchived);
        List<Request> requests = releaseFlowService.findRequestsForFlow(id, includeArchived);
        return ReleaseFlowDetailDto.from(flow, requests);
    }
}
```

Testing Agent and Build Agent controller skeletons are presented in §4 and §5 with the same shape.

### D5. Deployment Agent Frontend `index.ts`

```ts
// frontend/src/agents/deployment/index.ts
import { createAgentWorkspace } from '@/platform/composables/createAgentWorkspace'

export const deploymentAgentWorkspace = createAgentWorkspace({
  key: 'deployment-agent',
  name: 'Deployment Agent',
  apiBase: '/api/deployment-agent',
  stages: ['SIT', 'UAT', 'PROD'],
  supportsStitching: true,
  stageFilter: 'dropdown',
})
```

---

## 4. Module Design — Testing Agent Module

### T1. `TestingStage` + `TestingStagePipeline`

```java
// agents/testing/domain/TestingStage.java
public enum TestingStage {
    UAT;
}
```

```java
// agents/testing/domain/TestingStagePipeline.java
@Component
public class TestingStagePipeline implements StagePipeline {
    @Override public Optional<String> next(String currentStage) { return Optional.empty(); }
    @Override public boolean isTerminal(String stage) { return true; }
    @Override public List<String> orderedStages() { return List.of("UAT"); }
}
```

### T2. Testing Agent Backend Controllers

| Old class | New class | Behavioral change |
|---|---|---|
| `web/controller/TestingAgentReleaseFlowController` | `agents/testing/web/TestingReleaseFlowController` | Calls `releaseFlowService.listByAgent("testing-agent", ...)` instead of `listStitchedSummaries`. Invokes `AgentBoundaryGuard` on `getById` |
| `web/controller/TestingAgentUploadController` | `agents/testing/web/TestingUploadController` | Unchanged behavior; forces `agent = "testing-agent"` |
| `web/controller/TestingAgentTaskController` | `agents/testing/web/TestingTaskController` | **New:** invokes `AgentBoundaryGuard` on every task ID (closes v2 R-08) |
| *(new)* | `agents/testing/web/TestingDecisionController` | Extracted from task controller; calls `progressAfterDecision(taskId)` unchanged; pipeline resolution happens inside the service via `StagePipelineRegistry` |

### T3. Testing Agent Frontend `index.ts`

```ts
// frontend/src/agents/testing/index.ts
import { createAgentWorkspace } from '@/platform/composables/createAgentWorkspace'

export const testingAgentWorkspace = createAgentWorkspace({
  key: 'testing-agent',
  name: 'Testing Agent',
  apiBase: '/api/testing-agent',
  stages: ['UAT'],
  supportsStitching: false,
  stageFilter: 'disabled-input',
})
```

---

## 5. Module Design — Build Agent Module

### B1. `BuildStage` + `BuildStagePipeline`

```java
// agents/build/domain/BuildStage.java
public enum BuildStage {
    DEV;
}
```

```java
// agents/build/domain/BuildStagePipeline.java
@Component
public class BuildStagePipeline implements StagePipeline {
    @Override public Optional<String> next(String currentStage) { return Optional.empty(); }
    @Override public boolean isTerminal(String stage) { return true; }
    @Override public List<String> orderedStages() { return List.of("DEV"); }
}
```

### B2. `BuildReleaseFlowController`

```java
@RestController
@RequestMapping("/api/build-agent/release-flows")
@RequiredArgsConstructor
public class BuildReleaseFlowController {

    private final ReleaseFlowService releaseFlowService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public Page<ReleaseFlowListItemDto> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "latest") String attemptView,
            Pageable pageable,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        // Stage is always "DEV" for Build Agent; do not accept a client-supplied stage filter.
        ReleaseFlowFilter filter = new ReleaseFlowFilter(
                projectId, releaseId, "DEV", null, includeArchived);
        Page<ReleaseFlow> flows = releaseFlowService.listByAgent(AgentId.BUILD_AGENT, filter, pageable);
        return flows.map(flow -> {
            List<Request> requests = releaseFlowService.findRequestsForFlow(flow.getId(), includeArchived);
            return ReleaseFlowListItemDto.from(flow, requests, attemptView);
        });
    }

    @GetMapping("/{id}")
    public ReleaseFlowDetailDto getById(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        // BA-2: Build Agent does not honor ?linked= — not even read from the request.
        boundaryGuard.assertFlowBelongsToAgent(id, AgentId.BUILD_AGENT);
        ReleaseFlow flow = releaseFlowService.getById(id, includeArchived);
        List<Request> requests = releaseFlowService.findRequestsForFlow(id, includeArchived);
        return ReleaseFlowDetailDto.from(flow, requests);
    }
}
```

### B3. `BuildUploadController`

```java
@RestController
@RequestMapping("/api/build-agent/upload")
@RequiredArgsConstructor
public class BuildUploadController {

    private final ImportService importService;

    @PostMapping
    public ImportResultDto upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        // Forced server-side: agent = build-agent, stage = DEV. Ignore any client params.
        // ImportService itself is agent-agnostic; it receives agent and stage as
        // String parameters. Stage pipeline resolution happens later inside
        // ReleaseFlowProgressionService via StagePipelineRegistry, not here.
        return importService.importExcel(
                file,
                AgentId.BUILD_AGENT,
                "DEV",
                auth.getUserContext());
    }
}
```

Template download is served by platform `TemplateDownloadController` at `/api/platform/templates/*`; Build Agent does not own its own template endpoint.

### B4. `BuildTaskController`

```java
@RestController
@RequestMapping("/api/build-agent/tasks")
@RequiredArgsConstructor
public class BuildTaskController {

    private final TaskService taskService;
    private final TaskExecutionHistoryService historyService;
    private final RecordResultService recordResultService;
    private final AutoExecutionService autoExecutionService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public List<TaskDto> listByRequest(
            @RequestParam String requestId,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        return taskService.findByRequestId(requestId).stream().map(TaskDto::from).toList();
    }

    @GetMapping("/{id}")
    public TaskDto getById(@PathVariable String id, @AuthenticationPrincipal UserContextAuthentication auth) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return TaskDto.from(taskService.findById(id));
    }

    @PutMapping("/{id}/input")
    public TaskDto editInput(@PathVariable String id, @RequestBody EditInputRequest body,
                             @AuthenticationPrincipal UserContextAuthentication auth) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return TaskDto.from(taskService.editInput(id, body, auth.getUserContext()));
    }

    // /executions, /start-manual, /submit-auto, /record-result — same pattern:
    // boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT) → delegate
}
```

### B5. `BuildDecisionController`

```java
@RestController
@RequestMapping("/api/build-agent/tasks/{taskId}/decision")
@RequiredArgsConstructor
public class BuildDecisionController {

    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final AgentBoundaryGuard boundaryGuard;

    @PostMapping
    public DecisionResultDto apply(
            @PathVariable String taskId,
            @RequestBody DecisionRequest body,
            @AuthenticationPrincipal UserContextAuthentication auth) {
        boundaryGuard.assertTaskBelongsToAgent(taskId, AgentId.BUILD_AGENT);
        DecisionOutcome outcome = decisionEngine.apply(taskId, body, auth.getUserContext());
        // progressAfterDecision(taskId) signature is unchanged from v2. It
        // internally resolves BuildStagePipeline via StagePipelineRegistry using
        // request.getAgent() = "build-agent". See design M4.
        progressionService.progressAfterDecision(taskId);
        return DecisionResultDto.from(outcome);
    }
}
```

**Note:** `DecisionEngine.apply(...)` continues to return a `DecisionOutcome` (or similar — exact type is whatever the current `DecisionController` uses). The v3 change does not alter its return shape. `ReleaseFlowProgressionService.progressAfterDecision(taskId)` is `void` in the current codebase (`ReleaseFlowProgressionService.java:49`); the controller does not need its return value.

### B6. Build Agent Frontend `index.ts`

```ts
// frontend/src/agents/build/index.ts
import { createAgentWorkspace } from '@/platform/composables/createAgentWorkspace'

export const buildAgentWorkspace = createAgentWorkspace({
  key: 'build-agent',
  name: 'Build Agent',
  apiBase: '/api/build-agent',
  stages: ['DEV'],
  supportsStitching: false,
  stageFilter: 'disabled-input',
})
```

Total Build Agent frontend code: this single ~20-line file plus a one-line import into the platform router and a one-entry addition to `agentRegistry.ts`.

---

## 6. Algorithm Specifications

### A1. `DeploymentStitchingService.listStitchedSummaries` (Migrated)

The body ports from `ReleaseFlowService.java:172–221` with three mechanical changes:

1. Replace `Stage stage` parameters with `String stage`.
2. Replace `Stage.values()` iterations inside `buildStitchedSummary` with the observed-stage-set iteration pattern from M7.
3. Populate `stageStatuses` map instead of positional stage fields (M6).

**Input → output:**

| Input | Behavior |
|---|---|
| `filter = {projectId: null, stage: null, ...}`, 5 SIT flows + 3 UAT flows in DB (all `agent = "deployment-agent"`) | Returns 1 page; each summary row is a stitched group by family key |
| `filter.stage = "UAT"` | Only base flows with `currentStage = "UAT"` are used as grouping roots; stitched rows may still span multiple stages via family key |
| No deployment-agent flows in DB | Empty page |

### A2. `ReleaseFlowAggregation.aggregateFlowStatus` Rewrite

See M7 §3. The algorithm is structurally unchanged; only the iteration source moves from `Stage.values()` to `observedStages`.

### A3. `ReleaseFlowProgressionService.progressAfterDecision` Rewrite

See M4 for the full body diff. The algorithm-level changes:

- **Method signature unchanged:** `progressAfterDecision(String taskId)`. All five existing call sites continue working without modification.
- **New constructor dependency:** `StagePipelineRegistry`.
- **Terminal check replaces `currentStage.next() == null`** with `pipeline.isTerminal(currentStage)`, where `pipeline` is resolved via `stagePipelineRegistry.forAgent(request.getAgent())`.
- **Fail-loud semantics on the resolution path:** if `request.getAgent()` is null or unknown → `IllegalStateException` from the registry; if the resolved pipeline does not recognize the flow's current stage → `IllegalArgumentException` from the pipeline. Both cases roll back the transaction and surface as HTTP 500 (not a silent data corruption).
- **No method-parameter threading:** prior draft proposed passing `StagePipeline` as a parameter through `progressAfterDecision` and up through its callers (`RecordResultService`, `AutoExecutionService`, `ExternalExecutionMonitorService`); this was rejected because the monitor service runs on a Jenkins/Ansible callback thread with no HTTP or agent context.

All other logic (flow status updates, bottom-up recompute, optimistic lock handling) is unchanged.

### A4. `createAgentWorkspace` Factory Internals

**Pseudocode for the 5 sub-factories:**

```
createClient(config):
  return axios.create(baseURL=config.apiBase, withCredentials=true)
    + 401-interceptor (redirect to /login if not already there)

createReleaseFlowApi(client, config):
  return {
    list(params): client.get('/release-flows', {params})
    getById(id, query={}): client.get('/release-flows/${id}', {params: query})
    uploadExcel(file): multipart POST '/upload'
    downloadTemplate(): platformClient.get('/templates/${config.key}')
    listTasks(requestId): client.get('/tasks', {params: {requestId}})
    getTask(taskId): client.get('/tasks/${taskId}')
    editInput(taskId, body): client.put('/tasks/${taskId}/input', body)
    startManual(taskId, body): client.post('/tasks/${taskId}/start-manual', body)
    submitAuto(taskId, body): client.post('/tasks/${taskId}/submit-auto', body)
    recordResult(taskId, body): client.post('/tasks/${taskId}/record-result', body)
    getExecutions(taskId): client.get('/tasks/${taskId}/executions')
    applyDecision(taskId, body): client.post('/tasks/${taskId}/decision', body)
  }

createReleaseFlowStore(config, api):
  return defineStore(`${config.key}-release-flow`, {
    state: () => ({ list: [], detail: null, loading: false, error: null, filters: {}, pagination: {page: 0, size: 20} }),
    actions: {
      async fetchList() { this.loading = true; try { ... api.list({...this.filters, ...this.pagination}) ... } finally { this.loading = false } }
      async fetchDetail(id, linkedIds) {
        const query = (config.supportsStitching && linkedIds?.length) ? {linked: linkedIds} : {}
        this.detail = await api.getById(id, query)
      }
      async uploadFile(file) { await api.uploadExcel(file); await this.fetchList() }
    }
  })

createSummaryView(config, store):
  return defineComponent({
    render: () => h(AgentSummaryView, {
      agentKey: config.key,
      agentName: config.name,
      stages: config.stages,
      stageFilter: config.stageFilter || 'dropdown',
      store,
    })
  })

createDetailView(config, store):
  return defineComponent({
    setup() {
      const route = useRoute()
      const id = route.params.id
      const linkedIds = config.supportsStitching ? parseLinkedQueryParam(route.query.linked) : []
      onMounted(() => store.fetchDetail(id, linkedIds))
    },
    render: () => h(AgentDetailView, { agentKey: config.key, agentName: config.name, stages: config.stages, store })
  })
```

---

## 7. ArchUnit Fitness Tests

**File:** `src/test/java/com/wwa/deploymentagent/architecture/AgentModuleBoundaryTest.java`

**Rules:**

```java
@AnalyzeClasses(packages = "com.wwa.deploymentagent")
public class AgentModuleBoundaryTest {

    @ArchTest
    static final ArchRule agents_do_not_depend_on_each_other =
            noClasses().that().resideInAPackage("..agents.deployment..")
                    .should().dependOnClassesThat().resideInAnyPackage("..agents.testing..", "..agents.build..")
                    .because("Agent Modules must not import each other (PL-2)")
            .andShould() /* same for testing and build */;

    @ArchTest
    static final ArchRule platform_does_not_import_agent_code =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().resideInAnyPackage("..agents..")
                    .because("Platform Core must not depend on any Agent Module (PL-2)");

    @ArchTest
    static final ArchRule platform_does_not_reference_stage_enums =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Stage")
                    .andShould().dependOnClassesThat().resideInAnyPackage("..agents..domain..")
                    .because("Platform Core must not bind to any per-agent Stage enum (PL-3)");

    @ArchTest
    static final ArchRule no_hardcoded_stage_literals_in_platform =
            noClasses().that().resideInAPackage("..platform..")
                    .should().containStringLiterals("\"SIT\"", "\"UAT\"", "\"PROD\"", "\"DEV\"")
                    .because("Stage string literals must live in Agent Modules, not Platform Core (PL-3 / R-06)");

    @ArchTest
    static final ArchRule platform_does_not_branch_on_agent_id =
            noClasses().that().resideInAPackage("..platform..")
                    .should().accessField(AgentId.class, "DEPLOYMENT_AGENT")
                    .orShould().accessField(AgentId.class, "TESTING_AGENT")
                    .orShould().accessField(AgentId.class, "BUILD_AGENT")
                    .because("Platform Core must not branch on specific agents (PL-2)");

    @ArchTest
    static final ArchRule controllers_in_agent_modules_only =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage("..platform.web.shared..", "..agents..web..")
                    .because("All REST controllers belong either to platform shared capabilities or an Agent Module");
}
```

---

## 8. Test Matrix Summary

### 8.1 Backend Unit / Integration

| Test file | Scope | Count (estimate) |
|---|---|---|
| `StagePipelineContractTest` (parameterized for 3 impls) | M1 contract | 8 × 3 = 24 |
| `AgentBoundaryGuardTest` | M2 | 12 |
| `ReleaseFlowServiceTest` | M3 (new `listByAgent`, String stage) | +8 |
| `ReleaseFlowProgressionServiceTest` | M4 (pipeline parameter threading) | +6 |
| `ReleaseFlowListItemDtoTest` | M6 (generic stageStatuses) | 8 |
| `ReleaseFlowAggregationTest` | M7 regression (must pass unchanged semantics) | existing |
| `AuditLoggerServiceTest` | M8 (dynamic agentName + null assertion) | 4 |
| `PlatformRouteMigrationTest` | M9 + SecurityConfig | 4 |
| `DeploymentReleaseFlowControllerTest` | D4 | existing + agent-scope assertions |
| `DeploymentStitchingServiceTest` | D3 migration | ported from v2 |
| `TestingReleaseFlowControllerTest` | T2 | existing + guard assertions |
| `TestingTaskControllerTest` | T2 (new guard invocations) | +4 |
| `BuildReleaseFlowControllerTest` | B2 (list scoped, detail guarded, no ?linked) | 6 |
| `BuildUploadControllerTest` | B3 | 5 |
| `BuildTaskControllerTest` | B4 (guard on every endpoint) | 10 |
| `BuildDecisionControllerTest` | B5 (guard + pipeline threading) | 4 |
| `BuildDataIsolationTest` (end-to-end) | Cross-agent scenarios 1–10 in §9 | 10 |
| `AgentModuleBoundaryTest` (ArchUnit) | §7 | 6 rules |

### 8.2 Frontend Unit

| Test file | Scope |
|---|---|
| `createAgentWorkspace.test.ts` | M11 factory (7 cases in §2.M11) |
| `createReleaseFlowStore.test.ts` | Pinia store factory state/actions |
| `createReleaseFlowApi.test.ts` | API factory URL correctness per config |
| `AgentSummaryView.test.ts` | Reads `stageStatuses[...]` correctly per config |
| `AgentDetailView.test.ts` | Passes `?linked=` only when `supportsStitching` is true |
| `platformClient.test.ts` | 401 interceptor redirects to /login |

---

## 9. Critical Integration Test Scenarios

These end-to-end scenarios anchor the `BuildDataIsolationTest` and cross-agent regression suites.

| # | Scenario | Expected |
|---|---|---|
| 1 | Upload via Build Agent → Build Agent summary | Row visible, `stageStatuses["DEV"]` populated |
| 2 | Same upload → Deployment Agent summary | Row **not** visible (PL-6) |
| 3 | Same upload → Testing Agent summary | Row not visible |
| 4 | Upload `DEV-1234` twice through Build Agent | Single row; second upload upserts into first (§5.8) |
| 5 | Build Agent `DEV-1234` + Deployment Agent `SIT-1234` | Two separate `DA_RELEASE_FLOW` rows; neither stitches into the other |
| 6 | `GET /api/build-agent/tasks/{deployment-agent-task-id}` | HTTP 404 |
| 7 | `POST /api/build-agent/tasks/{testing-agent-task-id}/decision` | HTTP 404; underlying task unmodified |
| 8 | Approve all tasks in a Build Agent DEV flow | Flow becomes `Completed`, does not auto-advance |
| 9 | `GET /api/build-agent/release-flows/{id}?linked=abc,def` | Build Agent ignores `linked`; returns single flow |
| 10 | Audit trail after Build Agent action | `agentName = "build-agent"` in `AUDIT_LOG` |
| 11 | Log in via `/api/platform/auth/login` → call any agent endpoint | Same JSESSIONID works across prefixes |
| 12 | Log in via `/api/platform/auth/login` → `GET /api/deployment-agent/auth/login` | 404 (old route removed) |
| 13 | Testing Agent cross-agent task probe (was R-08 in v2) | HTTP 404 (closed by PL-9) |

---

## 10. Implementation Sequence (Commit Order)

The commits must land in this order to keep `mvn test` and `cd frontend && npm run build` green after each step.

### Phase A — Platform scaffolding (no behavior change)
1. **A1.** Create `platform/domain/StagePipeline.java` interface. No implementations yet. No callers. Tests: compile only.
2. **A2.** Create `agents/deployment/`, `agents/testing/`, `agents/build/` empty package structures with package-info files. ArchUnit test `agents_do_not_depend_on_each_other` lands (trivially green since there is no code in these packages).

### Phase B — Stage vocabulary migration (backend)
3. **B1.** Create `DeploymentStage`, `TestingStage`, `BuildStage` enums in their respective agent domain packages. Not yet referenced. Tests: unit tests for each enum.
4. **B2.** Create `DeploymentStagePipeline`, `TestingStagePipeline`, `BuildStagePipeline` `@Component` beans. Each implements `String agentId()` returning its `AgentId` constant and `Optional<String> next(...)`/`isTerminal(...)` with fail-loud semantics for unknown stages. Also create `StagePipelineRegistry` @Component (Platform Core). Bind each pipeline to `StagePipelineContractTest` and add `StagePipelineRegistryTest`. Tests: green.
5. **B3.** Add `StagePipelineRegistry` dependency to `ReleaseFlowProgressionService` constructor. Rewrite the terminal-check at `ReleaseFlowProgressionService.java:72` to use `pipeline.isTerminal(currentStage)` resolved via `registry.forAgent(request.getAgent())`. **No caller changes** — the method signature `progressAfterDecision(String taskId)` is unchanged. Tests: update `ReleaseFlowProgressionServiceTest` to cover the registry lookup path across all 3 agents plus the fail-loud paths (unknown agent, unknown stage).
6. **B4.** Change `Request.stage` and `ReleaseFlow.currentStage` JPA attributes from `Stage` enum to `String`. Update all repository and service method signatures that passed `Stage`. This is the biggest mechanical commit; touch every file that imports `contracts.enums.Stage`. Tests: all existing JPA integration tests must continue to pass.
7. **B5.** Delete `contracts/enums/Stage.java`. At this point nothing imports it; if it does, B4 missed a site.

### Phase C — DTO and aggregation refactor
8. **C1.** Rewrite `ReleaseFlowListItemDto` to use `Map<String, RequestStatus> stageStatuses` and `Set<String> stagesPresent`. Update all usages in `ReleaseFlowService`, `ReleaseFlowAggregation`, and existing controller tests. Tests: `ReleaseFlowListItemDtoTest` new cases.
9. **C2.** Rewrite `ReleaseFlowAggregation` to iterate over observed stages. Tests: existing `ReleaseFlowAggregationTest` must pass unchanged semantics.

### Phase D — Stitching relocation
10. **D1.** Move `ReleaseFlowFamilyKey.java` from current `domain/releaseflow/` to target `agents/deployment/domain/`. Package declaration and imports change; regex body unchanged.
11. **D2.** Create `agents/deployment/domain/DeploymentStitchingService.java`. Copy `listStitchedSummaries` and `getStitchedDetail` method bodies from platform `ReleaseFlowService`. Callers (`ReleaseFlowController`) switch to the new service.
12. **D3.** Delete `listStitchedSummaries` and `getStitchedDetail` from platform `ReleaseFlowService`. Add the new `listByAgent(...)` method.

### Phase E — AgentBoundaryGuard and Audit
13. **E1.** Create `platform/web/security/AgentBoundaryGuard.java`. Unit tests per M2 matrix. No controllers call it yet.
14. **E2.** Update `AuditLoggerService.log` per M8. Unit test.

### Phase F — Platform capability route migration
15. **F1.** Move `AuthController`, `AuditLogController`, `ConfigurationController`, `AccessGrantController`, `TemplateDownloadController` to `platform/web/shared/` and update their `@RequestMapping` to `/api/platform/*`. Update `SecurityConfig.java:36` whitelist. Integration test: `PlatformRouteMigrationTest`.
16. **F2.** Frontend: create `platform/api/platformClient.ts` and migrate the 5 capability API modules. Update `LoginView.vue` to call the new path. Update `config/agentId.ts`. Smoke: manual login still works in local profile.

### Phase G — Frontend platform factory
17. **G1.** Create `platform/composables/createAgentWorkspace.ts` and sub-factories. Unit tests.
18. **G2.** Create `platform/components/AgentSummaryView.vue` and `AgentDetailView.vue`. Unit tests.

### Phase H — Agent Module migrations (backend)
19. **H1.** Create `agents/deployment/web/Deployment*Controller` files. Each invokes `AgentBoundaryGuard`. Update Deployment Agent tests to the new class names.
20. **H2.** Delete old `web/controller/ReleaseFlowController`, `UploadController`, `TaskController`, `DecisionController`.
21. **H3.** Same for Testing Agent (H1 + H2 replicated). Closes v2 R-08.
22. **H4.** Create `agents/build/web/Build*Controller` files. New integration tests.

### Phase I — Frontend migration
23. **I1.** Create `frontend/src/agents/testing/index.ts`. Wire into the platform router. Delete the 7 `testingAgent*.ts` files and 2 testing views.
24. **I2.** Create `frontend/src/agents/build/index.ts`. Wire into router and `agentRegistry.ts`.
25. **I3.** Create `frontend/src/agents/deployment/index.ts`. Migrate any remaining Deployment-specific view behaviors. Delete the old flat `api/*`, `stores/*`, `views/ReleaseFlowSummaryView.vue`, `views/ReleaseFlowDetailView.vue`.

### Phase J — Verification
26. **J1.** Full `mvn test`. All ArchUnit rules pass.
27. **J2.** `cd frontend && npm run build`.
28. **J3.** Manual smoke of all 13 critical scenarios in §9.

**Green-at-every-step invariant:** Each phase's commits must leave the build green. Phases B and H are the riskiest — if a phase cannot be decomposed into green-at-every-step commits, split the phase into smaller sub-commits.

---

## 11. LOC Estimates

| Area | New LOC | Modified LOC | Deleted LOC | Net |
|---|---|---|---|---|
| Platform Core refactor (M1–M8) | ~400 | ~600 | ~150 | +850 |
| Platform capability route move (M9) | ~20 | ~30 | 0 | +50 |
| Frontend platform core (M10–M11) | ~700 | ~100 | ~400 | +400 |
| Deployment Agent Module migration | ~600 | ~200 | ~1200 | −400 |
| Testing Agent Module migration | ~400 | ~100 | ~700 | −200 |
| Build Agent Module (backend) | ~400 | 0 | 0 | +400 |
| Build Agent Module (frontend) | ~25 | ~20 | 0 | +45 |
| ArchUnit tests | ~80 | 0 | 0 | +80 |
| Integration tests | ~600 | ~200 | 0 | +800 |
| **Totals** | **~3225** | **~1250** | **~2450** | **+2025** |

These are rough planning numbers, not contracts. The tasks document (`build-agent-tasks.md`) decomposes them into effort-sized task items.

---

## 12. Design Validation Checklist

Before implementation starts, verify:

- [ ] Every PL-*/BA- decision in `build-agent-architecture.md` has a corresponding module or sub-section in this document
- [ ] No module in this document contradicts an architectural decision
- [ ] Every new class has a full skeleton (not just a name)
- [ ] Every modified method has a signature diff (old → new)
- [ ] Every new test file has a matrix with explicit assertions
- [ ] `SecurityConfig.java:36` edit is explicit (M9)
- [ ] ArchUnit rules cover PL-2 (no cross-agent imports), PL-3 (no Stage enum in platform), and PL-2 (no AgentId branching in platform)
- [ ] Implementation sequence (§10) is decomposable into green-at-every-step commits
- [ ] LOC estimates exist per module

When all twelve checks pass, this design is ready for `build-agent-tasks.md` decomposition (Task #4 in the SDD pipeline).
