# Change Proposal: Atlas CLI Platform Integration

## Status

Accepted

The user explicitly requested implementation on 2026-08-07. This proposal records that accepted scope; it does not add a second product definition beyond the pinned Atlas CLI contract and the slice SDD.

## Change ID

`atlas-cli-platform-integration`

## Why

Atlas CLI needs a versioned, platform-level Control Plane contract for Task discovery, Execution lifecycle reporting, bounded Artifact exchange, human review, audit, and capability usage telemetry. Atlas must remain the only authority for Task and Execution business state while local clients and Agent Modules remain execution adapters rather than competing state owners.

## What Changes

- Add the logical `/api/v1/integration` Platform Core API required by the pinned Atlas CLI contract.
- Add idempotent and fenced Execution lifecycle mutations, Artifact metadata/upload/download, progress/audit events, review submission and retrieval, and capability usage aggregation.
- Enforce Access Grants, Task write ownership/delegation, and Agent Module boundaries on every read and write.
- Add Web views for Task refresh, Execution history, Artifacts, Awaiting Review, pending synchronization/failure details, and capability/Skill usage metrics.
- Add Oracle/H2-compatible persistence migrations, integration tests, domain tests, and ArchUnit boundary tests.
- Update SDD traceability, API documentation, architecture decision records, changelog, and session handoff.

## Scope

### In Scope

- Platform Core Task, Execution, Artifact, progress event, review, idempotency, audit/correlation, and usage-metrics capabilities.
- Capability metrics by capability or Skill identity, version, technical result, user, team, project, Agent Module, client type, and date.
- Client types `COPILOT`, `OPENCODE`, `KIRO`, `MANUAL`, and `PIPELINE`.
- Polling-based Web refresh for the first release.
- Safe bounded Artifact persistence and download without displaying tokens or complete source content.
- Reuse of existing `TaskStateMachine`, `TaskExecutionHistory`, audit, access, Artifact-adjacent, and Agent Module structures where they fit the contract.

### Out Of Scope

- LangGraph or any second workflow state machine.
- Running local Skills, model inference, an IDE, a terminal, or repository scanning on Atlas Server.
- Build-Agent-specific Platform Core behavior.
- Client-controlled Task status mutation or client-supplied authoritative timestamps, actors, attempt numbers, durations, or client identity.
- Implicit whole-repository or whole-source-tree upload.

## Source Artifacts

| Artifact | Path | Status |
|---|---|---|
| Atlas CLI API contract | `/Users/leo/wwa-lab/GitHub/atlas-cli/docs/api-contract.md` | Pinned at blob `bdc5854e15083335f83a89b1a6916dc23c745912` |
| Atlas CLI OpenAPI | `/Users/leo/wwa-lab/GitHub/atlas-cli/docs/openapi/atlas-execution-api.yaml` | Pinned at blob `169a3663b03d4409c6bf93a600cce5d374a6a8a7` |
| Atlas CLI architecture decisions | `/Users/leo/wwa-lab/GitHub/atlas-cli/docs/architecture-decisions.md` | Pinned at blob `3adc8e24c0f52f4eb7d637936c2098e2f60e726e` |
| Requirements | `docs/01-requirements/atlas-cli-platform-integration-requirement.md` | Accepted |
| User Stories | `docs/02-user-stories/atlas-cli-platform-integration-user-stories.md` | Accepted |
| Spec | `docs/03-spec/atlas-cli-platform-integration-spec.md` | Accepted |
| Architecture | `docs/04-architecture/atlas-cli-platform-integration-architecture.md` | Accepted |
| Design | `docs/05-design/atlas-cli-platform-integration-design.md` | Accepted |
| Tasks | `docs/06-tasks/atlas-cli-platform-integration-tasks.md` | Complete and verified |
| ADR | `docs/00-context/decisions/ADR-0011-atlas-integration-is-platform-control-plane.md` | Accepted |

## Open Questions

None blocking. Storage limits, polling cadence, review permissions, and delegation rules are implementation-impacting defaults that will be committed in the slice design and tested; they must remain configurable where operational variation is expected.

## Acceptance Gate

- [x] Required SDD artifacts exist.
- [x] Open questions triaged.
- [x] Relevant ADR identified.
- [x] User accepted the implementation scope by direct request.
