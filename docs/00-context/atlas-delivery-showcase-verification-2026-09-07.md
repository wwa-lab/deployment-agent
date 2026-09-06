# Atlas Delivery Showcase Verification — 2026-09-07

## Scope and source

- Source commit: `abf3850dee78b13c597f7da2791dd06d201c1a66`; branch: `2026-codecup`.
- Documentation/assets only: no Java, Vue application, API, schema, credential, lockfile or runtime configuration changes.
- Existing six-stage Hub packaging artifacts were amended before the public rewrite. Deployment packaging received a supersession notice. This is not full SDD generation.
- Initial unrelated untracked `.codegraph/daemon.pid` was preserved.
- Names and technical routing directories were retained. No commit, push, merge or external submission was performed.

## Verification results

| Check | Actual result | Limit |
|---|---|---|
| Selected Java tests | **PASS: 84 tests, 10 classes, 0 failures/errors/skips**, v2 | Local synthetic fixtures and mocked external calls; not the full backend suite |
| Java compilation within Maven test | Maven build succeeded | Does not establish runtime deployment or business correctness |
| Historical sample/asset preservation | 21 tracked historical files match source commit bytes; final SHA-256 recheck passed | Hash equality establishes unchanged bytes, not case authenticity |
| Value and workflow SVGs | Chrome rendered both to PNG; no SVG text outside canvas; manually inspected exported images | Native SVG text remains editable; font rendering may vary by OS |
| HTML viewport checks | 16 slides × 9 viewport sizes: 144 checks, no detected overflow | Automated layout checks on macOS Chrome, not Windows hardware |
| Offline loading | Tested from local file with browser offline; zero HTTP requests and no page errors | Optional repository reference links still require adjacent files |
| Keyboard/notes | Home, End, arrows, PageUp/PageDown, Space, previous button, N and Escape passed; 16 notes available | Wheel and synthetic touch events also advanced slides; not a hardware touch-device test |
| Reduced motion | Computed transition duration 0s in reduced-motion mode | Default animation inspected separately after settling |
| Markdown links / diff | PASS: 234 Markdown files checked; diff whitespace check passed | Checks local targets; external sites are not reviewed |
| README rendering and language links | Both full READMEs rendered locally, both images loaded in each, reciprocal language links and referenced heading fragments checked | Local Markdown preview, not a hosted GitHub browser session |
| Documentation/code scanner | PASS for seven current entry documents: no broken links, missing file-like references or placeholder markers | Heuristic scan supplemented with manual code inspection |
| Language and privacy | Chinese default, full English, reciprocal links, legacy compatibility; personal form fields empty | Existing technical/historical repository files are not republished as cleared public evidence |

Viewport sizes: 1920×1080, 1440×900, 1280×720, 1024×768, 768×1024, 375×667, 414×896, 667×375, 896×414.

Local visual results: `target/atlas-visual-check-v2/results.json`. Settled preview screenshots and supplemental interactions: `target/atlas-preview-final-v2/`. The first visual harness attempt could not find its default bundled browser; the available Chrome installation was used successfully without installing packages. An initial synthetic touch test lacked a required Touch identifier; the harness was corrected, and the subsequent event-based test passed. Default animation reached full opacity and settled screenshots were manually reviewed.

[Shareable visual verification summary](../samples/evidence/2026-09-07-v2/visual-results.json) records viewports, controls, README image loading and SHA-256 hashes of the delivered SVG/PNG/HTML assets. A targeted pattern scan of 15 current public text artifacts found no executor-brand promotion, personal email, local absolute path, employee identifier or private-key patterns; application fields were checked empty. This is not a comprehensive security/privacy audit of repository history.

## Test evidence and capture correction

- [v1 summary](../samples/evidence/2026-09-07-v1/test-results.json): Maven actually reported 84 passing tests in raw stdout, but the collector's report-directory override was ineffective. The collector marked its summary incomplete with zero collected tests. That original summary and logs were not rewritten.
- [v2 summary](../samples/evidence/2026-09-07-v2/test-results.json): corrected capture copies only selected XML reports freshly written during the run; all ten classes were collected and 84 tests passed.
- [Historical source hashes](../samples/evidence/2026-09-07-v1/historical-manifest.json) pin original tracked samples and assets before any new visuals were created.
- The [capture script](../../scripts/capture-atlas-evidence.mjs) refuses existing version directories. New runs receive new locations.
- Raw stdout/stderr and v2 XML remain in `target/atlas-evidence/` with hashes in the public summaries. Raw logs are local-only because they can contain environment paths and synthetic identities. Archive them to an approved durable location before cleaning build output; the shared JSON is not a complete raw run package.
- Test time is not an estimate of human savings. Test count is not business coverage or accuracy.

## Documentation vs Code Review Report

### Review Scope

- **Documentation reviewed:** both full READMEs, compatibility entry, main/module indexes and pitches, four submission entries, CONTRIBUTING, case registry/template, SVGs and HTML slides; current Hub and Deployment packaging amendments.
- **Code / artifacts inspected:** ImportService, RecordResultService, TaskStateMachine, DecisionEngine, ReleaseFlowProgressionService, AutoExecutionAdapter, AutoExecutionService, JenkinsExecutionAdapter, AnsibleExecutionAdapter, ExternalExecutionMonitorService, stage pipelines, frontend AI preview config, build manifests and the ten selected tests.
- **Review objective:** constrain public claims to current implementation and the evidence actually available.

### Overall Assessment

- **Accuracy rating:** Not scored as a percentage; this is a scoped claim review, not a statistical sample of all documentation.
- **Verdict:** Mostly accurate.
- **Rationale:** Current entry materials now separate implemented mechanisms, synthetic illustrations, selected tests and future capabilities. Historical technical references are retained with explicit age/interpretation warnings; they were not comprehensively repaired.

### Confirmed Accurate Areas

- Local backend port is 8081 and matches the frontend proxy; JDK 21 and Maven/npm commands match manifests.
- MANUAL records human-provided results; AUTO success at submission leaves the task Executing.
- Polling defaults to disabled; successful terminal synchronization changes Executing to Awaiting_Review.
- Decision states and owner/admin checks match implementation; no independent second approver is enforced.
- Critical review blocks readiness of the next task. Approved/Skipped completion is not evidence that every task successfully executed.
- Agent Skills are development/documentation workflows; frontend AI Assist is a visual-only preview.
- The shared Build/Testing workspaces do not establish automated code generation, autonomous testing or all-stage operational readiness.

### Findings

#### Critical

None remaining in the reviewed current public claims. Real-environment and production readiness are explicitly unverified.

#### Major

**Overstated maturity and independence — corrected in current entries**
- **Document claim:** Prior README and pitches led with end-to-end lifecycle delivery and asserted independent Framework/Deployment competition entries.
- **Documentation evidence:** Historical sections preserved in the packaging spec and tasks.
- **Code / repo evidence:** Shared workflow implementations and the same synthetic samples; no separately validated iSeries executor or other project evidence inspected.
- **Why it matters:** Hierarchy or naming does not establish independently delivered solutions or business outcomes.
- **Recommended fix:** Applied: one concrete problem with module-level references, explicit shared-evidence boundary and unverified lifecycle extensions.

**Synthetic output differs from current state semantics — annotated, not overwritten**
- **Document claim:** Original sample-task-output.json associates submission success with Awaiting_Review.
- **Documentation evidence:** C01 original sample.
- **Code / repo evidence:** AutoExecutionService retains Executing after submission; ExternalExecutionMonitorService changes state on terminal success.
- **Why it matters:** Reviewers could mistake a hand-written sample for an actual execution trace.
- **Recommended fix:** Applied in the case index; original bytes and hashes retained.

#### Minor

**Historical operational baseline remains older than runtime configuration**
- **Document claim:** The retained implementation baseline mentions port 8080 for local startup/proxy.
- **Code / repo evidence:** application-local.properties and frontend/vite.config.ts use 8081.
- **Why it matters:** Copying the historical instructions without checking current setup can misdirect local diagnostics.
- **Recommended fix:** Current READMEs provide the correct setup; historical links explicitly warn that current configuration governs.

### Coverage Summary

| Area | Status | Notes |
|---|---|---|
| Setup / install / run / test | Partial | Commands and ports inspected; focused tests run; application startup not re-run |
| Paths / module names | Aligned for current entries | No migration; final local link gate recorded above |
| API / contracts | Partial | Selected code and tests; no live API acceptance run |
| Config / runtime | Partial | Local port, auth stub and polling default inspected |
| States / roles / workflow | Aligned for stated claims | Critical-review and skip nuances included |
| Architecture / boundaries | Aligned for package scope | Existing runtime boundaries unchanged |
| Examples / snippets | Partial by design | Historical synthetic examples annotated; not executable contracts |

### Automated Scan Highlights

- **Broken markdown links:** None identified in the full link check or seven-document scanner.
- **Missing file-like references:** None identified in the scoped scanner.
- **Placeholder markers:** None identified by the scanner; planned/unverified labels and empty personal form fields were manually checked as intentional.
- **Commands observed:** Maven, npm, Node link/evidence scripts; optional python3 scanner and Windows `py -3` equivalent.
- **API-like paths observed:** Existing local workspace path; no fabricated universal Agent CLI.
- **Environment variables observed:** No credentials added. Existing polling and AI-preview configuration inspected.

### Minimal Fix Path

1. Use the new README and current diagrams for presentation.
2. Cite C03 v2 for scoped test results and C01/C02 only as synthetic examples.
3. Collect authorized field evidence before claiming deployment reliability, business return or separate-project independence.

### Open Risks / Ambiguities

- Audit exists, but complete field correctness is not established: DecisionEngine's audit-context `previousStatus` currently reads the task state after update. No audit certification claim is made, and runtime repair was outside this documentation task.
- The monitor tests call `processSingleExecution` directly; they do not establish scheduler transaction isolation or real external polling reliability.
- The import re-upload test checks the original request's task count; it does not prove that no new request attempt was created.
- Full backend suite, frontend application build, application manual UAT, external Jenkins/AWX execution, Oracle deployment and Windows 11 hardware were not tested in this revision.
- No authorized real-world case, baseline effort study or measured financial benefit was found.

**Final verdict:** Mostly accurate within the explicitly reviewed scope; no production-readiness conclusion.

## Document Review Report

### Document Summary

- **Document type:** Existing packaging requirement/story/spec/architecture/design/task amendments.
- **Scope summary:** Evidence-led positioning, bilingual entries, immutable case registry and offline presentation.
- **Intended next stage:** Local document/asset handoff, not runtime deployment.

### Overall Assessment

- **Quality rating:** Good.
- **Readiness verdict:** Ready for local documentation handoff.
- **Rationale:** Scope, language, legacy links, maturity distinctions, evidence preservation and checks are explicit. The current revision supersedes contradictory old packaging requirements without altering historical sample bytes.

### Strengths

- User intent maps to AEDH-REQ-10–16, AEDH-US-09–12 and AEDH-TASK-010–014.
- Runtime/API/data scope is unchanged and historical assertions are clearly superseded.
- Visual branches, state limits and source evidence are cross-referenced.

### Issues Found

#### Critical
None for the local documentation handoff.

#### Major
No remaining current-scope blocker; field evidence is absent and explicitly labelled, not an implementation decision deferred to a downstream coder.

#### Minor
Historical packaging sections remain verbose; they are retained as context with supersession notices.

### Completeness Check

Actors, problem, objectives, scope, acceptance, constraints, dependencies, risks, artifact design, compatibility, failure cases, verification and traceability are present. Runtime data/API designs and production rollout plans are not introduced by this docs-only amendment.

### Consistency Check

- Internal contradictions: conflicting prior language/entry requirements are superseded by the current revision.
- Cross-section mismatches: C01 mismatch is explicitly annotated outside immutable evidence.
- Phase drift: no runtime code bodies added to architecture/design amendments.
- Traceability gaps: no current packaging requirement left unmapped.
- Grounding F1–F7: source paths checked; behavior inspected; scope separated; contradictions corrected; boundary examples traced (AUTO submit/success/unknown, approve/reject/rerun/skip); missing field evidence labelled; old claims not inherited as facts.

### Readiness for Next Stage

- **Target stage:** Local review/use of documentation and presentation.
- **Verdict:** Sufficient for that scope.
- **Blockers:** None; authorized field evidence is required only for stronger delivery/benefit claims.

### Recommended Revisions

Use future evidence versions to update results; do not overwrite history. Keep current narrative and translated entry claims synchronized.

### Minimal Fix Path

No runtime change is necessary for this handoff. Follow the case template when adding actual practice.

### Open Questions / Risks

Independent evidence for any separately registered Deployment entry and real pilot measurements remain unavailable.

**Final verdict: Ready for local documentation handoff.**

## Freshness Gate Report

### Verdict

Fresh for the inspected source baseline and new documentation revision; unknown for external deployment or field evidence.

### Evidence

| Artifact | Version / Time | Source | Status |
|---|---|---|---|
| Runtime code | abf3850dee78b13c597f7da2791dd06d201c1a66 | Working source unchanged in tested code paths | Fresh |
| Historical samples | SHA-256 manifests | Original tracked bytes | Fresh identity, not verified truth |
| Selected tests | 2026-09-07-v2 | Pinned code and captured reports | Fresh |
| Current packaging | 2026-09-07 amendment | User direction and source inspection | Fresh |
| Real environment / measured benefit | No evidence version | Not supplied | Unknown |

### Findings

Earlier full-lifecycle, separate-entry and English-default assumptions no longer govern the current package. The active handoff will link this review while retaining outstanding Resource Center UAT context.

### Minimal Repair Path

New code, changed sample evidence or new field outcomes require new versioned verification; do not reuse this report as a release approval.

### Open Risks

External state was not inspected. Documentation readiness is not production acceptance.

## Skills used

- `review-docs-against-code`: source-grounded maturity corrections and scoped review.
- `review-doc-quality`: existing SDD amendment consistency and local handoff gate.
- `freshness-gate`: source commit, historical hashes and fresh test-version classification.
- `frontend-slides`: self-contained deck, viewport constraints, keyboard/notes and reduced-motion behavior.

No full SDD generation, directory migration, executor adaptation, third-party publishing or runtime code implementation occurred.
