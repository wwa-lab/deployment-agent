# Atlas Hub Branding Traceability

**Date:** 2026-06-29
**Status:** Active
**Slice key:** `atlas-hub-branding`

This traceability index covers the brand and UI naming change that presents the repository as **WWA-Atlas Engineering Delivery Hub** while preserving **Deployment Agent** as the M6 Deployment agent inside the Hub.

## Scope

This slice changes visible naming, documentation, package metadata, and the WWA-Atlas Hub home page's responsive presentation. It does not change API routes, Vue router paths, Java package names, database tables, migration history, or backend route prefixes.

## Artifact Chain

| Stage | Artifact |
|---|---|
| Requirements | [atlas-hub-branding-requirement.md](../01-requirements/atlas-hub-branding-requirement.md) |
| User stories | [atlas-hub-branding-user-stories.md](../02-user-stories/atlas-hub-branding-user-stories.md) |
| Specification | [atlas-hub-branding-spec.md](../03-spec/atlas-hub-branding-spec.md) |
| Architecture | [atlas-hub-branding-architecture.md](../04-architecture/atlas-hub-branding-architecture.md) |
| Design | [atlas-hub-branding-design.md](../05-design/atlas-hub-branding-design.md) |
| Tasks | [atlas-hub-branding-tasks.md](../06-tasks/atlas-hub-branding-tasks.md) |

## Requirement Coverage

| Requirement | Story | Spec Section | Design Section | Task |
|---|---|---|---|---|
| AHB-REQ-01 Product brand | AHB-US-01 | 3.1 | 2 | AHB-TASK-001, AHB-TASK-003 |
| AHB-REQ-02 Preserve Deployment Agent | AHB-US-02 | 3.2 | 3 | AHB-TASK-003 |
| AHB-REQ-03 Keep technical compatibility | AHB-US-03 | 3.3 | 4 | AHB-TASK-004 |
| AHB-REQ-04 Update docs and metadata | AHB-US-04 | 3.4 | 3 | AHB-TASK-002, AHB-TASK-005 |
| AHB-REQ-05 Validate change | AHB-US-05 | 4 | 5 | AHB-TASK-006 |
| AHB-REQ-06 Wide-screen responsive home | AHB-US-06 | 3.5 | 4 | AHB-TASK-007 |

## Non-Goals

- Do not rename `/wwa/*` routes in this slice.
- Do not rename `/api/deployment-agent/*` routes in this slice.
- Do not rename `com.wwa.agenthub`, Maven `artifactId`, or persisted schema identifiers.
- Do not remove `Deployment Agent` as an agent name.
