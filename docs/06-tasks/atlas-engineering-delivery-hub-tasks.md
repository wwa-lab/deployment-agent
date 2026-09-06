# Atlas Engineering Delivery Hub Packaging Tasks

## Current revision — 2026-09-07, platform clarification
Prior tasks/results below describe historical packaging and do not authorize a commit in this revision.

| Task | Requirements | Work and acceptance | State |
|---|---|---|---|
| AEDH-TASK-010 | REQ-10,12,15 | Inspect code/tests/samples; identify defensible position and limits | Done |
| AEDH-TASK-011 | REQ-11,15 | Update bilingual entry points, narrative, submissions, contribution and indexes | Done |
| AEDH-TASK-012 | REQ-12,13 | Register cases and immutable hashes; retain new test evidence separately | Done; v1 capture limitation retained, v2 collected 84 passing tests |
| AEDH-TASK-013 | REQ-14 | Produce editable SVG/PNG pair and self-contained Chinese deck | Done; 16 slides, 144 viewport checks |
| AEDH-TASK-014 | REQ-16 | Check documentation/code alignment, links, visuals and focused tests; update handoff last | Done; see current verification report and handoff |

Order: 010 → 011/012 → 013 → 014. Completion requires a recorded verification report with limitations, not production acceptance. All work is local; no commit, push or merge.

[Prior revision verification report](../00-context/atlas-delivery-showcase-verification-2026-09-07.md): selected Maven tests and document/visual checks passed; full runtime suite, real external execution, manual business UAT and measured return were not verified.

### AEDH-TASK-015 — Platform-first three-step narrative (REQ-17 / US-13)

Update both READMEs, pitches, submission summaries and indexes to present Agentic SDLC first and Deployment Agent second. Produce versioned replacement visuals/deck, preserve prior evidence, and verify current links and rendering. No runtime code changes; do not rerun unchanged Java tests solely for editorial changes. Status: Done — 18 slides, 162 viewport checks, 235 Markdown files checked, prior evidence hashes unchanged. See the [current positioning review](../00-context/atlas-agentic-sdlc-positioning-review-2026-09-07.md).

## Historical packaging baseline (superseded where inconsistent)

**Date:** 2026-06-27
**Status:** Complete
**Source design:** [atlas-engineering-delivery-hub-design.md](../05-design/atlas-engineering-delivery-hub-design.md)

## 1. Overview

These tasks package the repository as Atlas Engineering Delivery Hub for the internal open collaboration competition. The work is documentation, diagrams, samples, validation, and commit hygiene only.

## 2. Task Details

### AEDH-TASK-001: Inspect Repository And Preserve Baseline

- **Objective:** Read current README, SDD profile, architecture/design docs, existing SDLC dashboard docs, changelog, and asset structure.
- **Status:** Complete
- **Artifacts:** `docs/wwa-agent-workspace-hub-current-baseline.md`

### AEDH-TASK-002: Create SDD Packaging Chain

- **Objective:** Create requirements, user stories, spec, architecture, design, tasks, and traceability for the packaging work.
- **Status:** Complete
- **Artifacts:** `docs/01-requirements/atlas-engineering-delivery-hub-requirement.md`, `docs/02-user-stories/atlas-engineering-delivery-hub-user-stories.md`, `docs/03-spec/atlas-engineering-delivery-hub-spec.md`, `docs/04-architecture/atlas-engineering-delivery-hub-architecture.md`, `docs/05-design/atlas-engineering-delivery-hub-design.md`, `docs/06-tasks/atlas-engineering-delivery-hub-tasks.md`, `docs/00-context/atlas-engineering-delivery-hub-traceability.md`

### AEDH-TASK-003: Add README And Open Collaboration Materials

- **Objective:** Rewrite English README, add Chinese README, docs index, submission docs, and pitch.
- **Status:** Complete
- **Artifacts:** `README.md`, `README.zh-CN.md`, `docs/atlas-engineering-delivery-hub-index.md`, `docs/open-collaboration-submission.md`, `docs/open-collaboration-submission.zh-CN.md`, `docs/atlas-engineering-delivery-hub-pitch.md`

### AEDH-TASK-004: Add Contribution Guidance

- **Objective:** Create or update `CONTRIBUTING.md` with contribution types, docs rules, module rules, validation expectations, safety guidance, PR checklist, and commit style.
- **Status:** Complete
- **Artifacts:** `CONTRIBUTING.md`

### AEDH-TASK-005: Add Visual Assets

- **Objective:** Add Mermaid source plus SVG diagrams for framework lifecycle, Seven Mountains SDLC, and Seven Gates / I-E-O-V.
- **Status:** Complete
- **Artifacts:** `docs/assets/*.mmd`, `docs/assets/*.svg`

### AEDH-TASK-006: Add Synthetic Adoption Sample

- **Objective:** Add a small framework adoption sample without sensitive data.
- **Status:** Complete
- **Artifacts:** `docs/samples/atlas-framework-adoption-sample.md`

### AEDH-TASK-007: Link Everything

- **Objective:** Ensure README, Chinese README, docs index, submission, pitch, contribution, diagrams, sample, and SDD docs are discoverable.
- **Status:** Complete

### AEDH-TASK-008: Validate And Commit

- **Objective:** Run lightweight checks, review diff, stage related files only, and commit.
- **Status:** Complete
- **Validation:** `git diff --check`; Markdown relative-link existence check; Mermaid SVG render verification.

### AEDH-TASK-009: Reposition Root Entry And Split Deployment Function Package

- **Objective:** Reposition the root README and framework docs as the Atlas Engineering Delivery Hub team framework, while preserving Deployment as a separate Tool / Function entry with its own submission links and IBM iSeries one-click release UTL design direction.
- **Status:** Complete
- **Artifacts:** `README.md`, `README.zh-CN.md`, `docs/atlas-engineering-delivery-hub-index.md`, `docs/open-collaboration-submission.md`, `docs/open-collaboration-submission.zh-CN.md`, `docs/atlas-engineering-delivery-hub-pitch.md`, `docs/atlas-engineering-delivery-hub-deployment-index.md`, `docs/open-collaboration-submission-deployment.md`, `docs/open-collaboration-submission-deployment.zh-CN.md`, `docs/atlas-engineering-delivery-hub-deployment-pitch.md`

## 3. Validation Results

| Check | Result |
|---|---|
| Mermaid SVG render | Pass - three SVG files rendered under `docs/assets/`. |
| `git diff --check` | Pass. |
| `node scripts/check-markdown-links.mjs` | Pass - 179 Markdown files checked. |

## 4. Open Questions

| # | Question | Status |
|---|---|---|
| OQ-1 | Should future Atlas Phoenix Lens packaging link to this parent framework docs index? | Open |
| OQ-2 | Should Discovery and Maintenance capabilities receive separate implementation SDD slices when work begins? | Open |
| OQ-3 | Should a future competition submission add screenshots after explicit redaction approval? | Open |
| OQ-4 | Should the IBM iSeries one-click release UTL design receive its own deeper SDD slice under the Deployment function? | Open |
