# Requirements: Atlas Engineering Delivery Hub - Deployment

> **Historical packaging baseline — 2026-09-07 notice.** Current presentation scope is governed by the [Hub specification, current revision](../03-spec/atlas-engineering-delivery-hub-spec.md). Deployment remains an implemented module with its existing name; the evidence does not establish a second independent competition solution. Earlier English-default, separate-entry and commit requirements below are superseded for this documentation revision. Original samples remain unchanged; runtime contracts are not modified.

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Category:** Tool
**Lifecycle stage:** M6 Deployment

## Background

The repository already implements a WWA Agent Workspace Hub baseline with a Deployment Agent workspace. For the internal open collaboration competition, the Deployment function must be presented accurately as **Atlas Engineering Delivery Hub - Deployment**, the M6 Deployment-stage Tool / Function inside the Atlas Engineering Delivery Hub / Seven Mountains SDLC framework.

The packaging must not overstate Deployment as the whole framework. It must explain how the function consumes M4 Build and M5 Testing evidence and produces controlled release operations for M7 Maintenance handoff. It must also preserve the IBM iSeries one-click release UTL design direction without exposing sensitive implementation details.

## Goals

- Provide clear English and Chinese entry points for reviewers.
- Make M6 Deployment visually and textually obvious.
- Explain current capabilities, limits, inputs, outputs, and workflow based on the implemented codebase.
- Provide competition submission materials and a reviewer-ready pitch.
- Provide separate function-level submission materials so the parent Hub can remain the Framework entry.
- Mention the IBM iSeries one-click release UTL direction as a deployment design focus.
- Add repo-safe diagrams and sanitized sample outputs.
- Preserve existing technical references and SDD traceability.
- Validate documentation and commit the finished package.

## Requirements

| ID | Requirement |
|---|---|
| M6-REQ-01 | Deployment-specific docs shall position Deployment as the M6 Tool / Function inside the Atlas Engineering Delivery Hub framework, not as the whole Hub. |
| M6-REQ-02 | Public docs shall distinguish implemented capabilities from planned, placeholder, or TBD capabilities. |
| M6-REQ-03 | The package shall include natural English and Chinese README/submission materials. |
| M6-REQ-04 | The package shall include reviewer-facing competition docs: deployment index, submission, Chinese submission, and pitch. |
| M6-REQ-05 | The package shall include repo-safe diagrams for lifecycle positioning, internal deployment workflow, and upstream/downstream relationships. |
| M6-REQ-06 | The package shall include a sanitized sample or template package with representative inputs and outputs. |
| M6-REQ-07 | The package shall preserve existing detailed technical docs by linking them instead of deleting useful content. |
| M6-REQ-08 | The package shall pass documentation validation, avoid adding secrets or customer data, and be committed with the requested commit message. |
| M6-REQ-09 | The package shall mention the IBM iSeries one-click release UTL design direction as a sanitized future/deepening focus for the Deployment function. |

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

- `README.md` and `README.zh-CN.md` open with the parent Framework positioning and link to Deployment as a function entry.
- `docs/open-collaboration-submission-deployment.md` and Chinese counterpart list category as Tool / Function.
- `docs/atlas-engineering-delivery-hub-deployment-index.md` links all reviewer materials.
- Deployment materials mention the IBM iSeries one-click release UTL direction without real internal details.
- Diagrams and sample package exist under `docs/assets/` and `docs/samples/`.
- New samples contain only synthetic values.
- Existing deeper docs remain available through links.
- Validation commands are run before commit.
