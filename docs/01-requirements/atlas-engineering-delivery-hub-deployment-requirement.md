# Requirements: Atlas Engineering Delivery Hub - Deployment

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Category:** Tool
**Lifecycle stage:** M6 Deployment

## Background

The repository already implements a WWA Agent Workspace Hub baseline with a Deployment Agent workspace. For the internal open collaboration competition, the repository must now be presented accurately as **Atlas Engineering Delivery Hub - Deployment**, the M6 Deployment-stage Tool in the Atlas Engineering Delivery Hub / Seven Mountains SDLC narrative.

The packaging must not overstate the repo as the whole framework. It must explain how the tool consumes M4 Build and M5 Testing evidence and produces controlled release operations for M7 Maintenance handoff.

## Goals

- Provide clear English and Chinese entry points for reviewers.
- Make M6 Deployment visually and textually obvious.
- Explain current capabilities, limits, inputs, outputs, and workflow based on the implemented codebase.
- Provide competition submission materials and a reviewer-ready pitch.
- Add repo-safe diagrams and sanitized sample outputs.
- Preserve existing technical references and SDD traceability.
- Validate documentation and commit the finished package.

## Requirements

| ID | Requirement |
|---|---|
| M6-REQ-01 | The root README shall position the repository as the M6 Deployment Tool, not the whole Atlas Engineering Delivery Hub framework. |
| M6-REQ-02 | Public docs shall distinguish implemented capabilities from planned, placeholder, or TBD capabilities. |
| M6-REQ-03 | The package shall include natural English and Chinese README/submission materials. |
| M6-REQ-04 | The package shall include reviewer-facing competition docs: deployment index, submission, Chinese submission, and pitch. |
| M6-REQ-05 | The package shall include repo-safe diagrams for lifecycle positioning, internal deployment workflow, and upstream/downstream relationships. |
| M6-REQ-06 | The package shall include a sanitized sample or template package with representative inputs and outputs. |
| M6-REQ-07 | The package shall preserve existing detailed technical docs by linking them instead of deleting useful content. |
| M6-REQ-08 | The package shall pass documentation validation, avoid adding secrets or customer data, and be committed with the requested commit message. |

## In Scope

- Documentation, diagrams, sample JSON/Markdown, SDD packaging artifacts, changelog, and contribution guide updates.
- Accurate description of existing Deployment Agent release-flow behavior.
- Explicit notes on approval, traceability, rollback posture, validation, and human review.

## Out Of Scope

- Runtime code changes.
- API contract changes.
- Real deployment credentials, screenshots, customer data, kubeconfigs, or production environment names.
- New autonomous approval or rollback features.
- Presenting Atlas Phoenix Lens as the main project.

## Acceptance Criteria

- `README.md` and `README.zh-CN.md` open with Tool/M6 Deployment positioning.
- `docs/open-collaboration-submission.md` and Chinese counterpart list category as Tool.
- `docs/atlas-engineering-delivery-hub-deployment-index.md` links all reviewer materials.
- Diagrams and sample package exist under `docs/assets/` and `docs/samples/`.
- New samples contain only synthetic values.
- Existing deeper docs remain available through links.
- Validation commands are run before commit.
