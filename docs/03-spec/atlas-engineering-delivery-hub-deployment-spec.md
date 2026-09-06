# Specification: Atlas Engineering Delivery Hub - Deployment Package

> **Historical packaging baseline — 2026-09-07 notice.** Current presentation scope is governed by the [Hub specification, current revision](../03-spec/atlas-engineering-delivery-hub-spec.md). Deployment remains an implemented module with its existing name; the evidence does not establish a second independent competition solution. Earlier English-default, separate-entry and commit requirements below are superseded for this documentation revision. Original samples remain unchanged; runtime contracts are not modified.

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Category:** Tool
**Lifecycle stage:** M6 Deployment

## Scope

This spec governs the documentation and sample package that presents the existing Deployment Agent as **Atlas Engineering Delivery Hub - Deployment**, the M6 Deployment Tool / Function inside the parent Atlas Engineering Delivery Hub framework.

## Actors

- **Competition reviewer:** needs a fast, accurate understanding of the tool.
- **Release contributor:** wants to reuse or extend deployment workflow patterns.
- **Maintainer:** needs traceability, validation, and safety boundaries.
- **Internal team adopter:** wants to understand inputs, outputs, and rollout workflow.

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-01 | Deployment-specific entry points shall identify Deployment as a Tool / Function-category M6 Deployment capability inside the parent Hub. |
| FR-02 | Docs shall list implemented scope, planned/TBD scope, inputs, outputs, runtime assumptions, and maturity. |
| FR-03 | English and Chinese materials shall present the same positioning naturally. |
| FR-04 | Submission materials shall explain the release problem, reusable value, delivered assets, contribution opportunities, and demo story. |
| FR-05 | Diagrams shall cover lifecycle positioning, internal workflow, and upstream/downstream relationships. |
| FR-06 | Samples shall be safe, synthetic, and representative of release inputs/outputs. |
| FR-07 | Existing detailed technical references shall remain linked. |
| FR-08 | Validation and commit hygiene shall be completed before handoff. |
| FR-09 | Deployment materials shall mention the IBM iSeries one-click release UTL design direction without exposing real internal or production details. |

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-01 | Accuracy: no capability may be described as implemented unless grounded in existing code/docs. |
| NFR-02 | Safety: no secrets, customer data, kubeconfigs, production endpoints, or confidential screenshots may be added. |
| NFR-03 | Clarity: reviewer entry points must be readable without deep codebase knowledge. |
| NFR-04 | Traceability: package docs must link to SDD and runtime references. |
| NFR-05 | Maintainability: new docs must use relative links that pass repository link validation. |

## Key Workflow

1. Inspect the current repository implementation and docs.
2. Backfill SDD package documents for the M6 Deployment Tool slice.
3. Update root README and Chinese README so they lead with the parent Hub and link Deployment as a function.
4. Update Deployment submission, pitch, contribution guide, and index docs.
5. Add lifecycle/workflow/upstream diagrams.
6. Add sanitized mini output sample package.
7. Run diff, link, diagram, test, and secret-safety checks.
8. Commit with the requested message.

## Constraints

- No runtime code changes are required for this package.
- Do not push.
- Do not add lockfile or credential changes.
- Do not present Atlas Phoenix Lens as the main project.
- Do not present Deployment as the whole Atlas Engineering Delivery Hub framework.

## Risks And Mitigations

| Risk | Mitigation |
|---|---|
| Reviewer confuses Deployment with the whole framework | Framework README leads with the Hub, while Deployment docs explicitly say Tool / Function. |
| Framework and Deployment docs create mixed messaging | Keep separate framework and Deployment indexes, submissions, and pitches. |
| Samples accidentally expose real details | Use synthetic names and `example.invalid` URLs only. |
| Diagrams fail to render | Keep Mermaid source in the repo and render SVGs when tooling is available. |
