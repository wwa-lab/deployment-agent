# Change Archive: Atlas CLI Platform Integration

## Status

Archived as complete on 2026-08-07.

## Delivered Outcome

Atlas now exposes an Agent-neutral Platform Core control plane for Atlas CLI and Web clients. Execution,
Artifact, Telemetry, idempotency, correlation, Event/Audit, and Review are platform capabilities. Agent Modules
retain only scoped adapters, and Atlas remains the sole authority for Task and Execution business state.

The delivery includes:

- `/api/v1/integration` Task discovery and fenced Execution lifecycle APIs.
- Immutable Event/Audit evidence with server-owned identity and correlation.
- Bounded Artifact metadata/upload/download, approved inputs, retention, legal hold, scanner/DLP, and
  least-disclosure controls.
- Exact-attempt human Review and capability/Skill usage aggregation across team, project, Agent, UTC date,
  client type, user, capability/Skill ID, and Skill version.
- Digest-only Bearer authentication, Access Grant revalidation, ownership/delegation checks, replay
  reauthorization, rate/transfer limits, and Agent-boundary enforcement.
- A polling Platform Execution Center for Tasks, history, Artifacts, Awaiting Review, failure/pending-sync state,
  and usage dashboards, with responsive desktop/mobile behavior and no sensitive source/token rendering.
- Oracle V21 migration/current schema, integration/security/migration tests, ArchUnit boundary tests, and the
  completed English SDD traceability chain.

## Non-Goals Preserved

- Atlas Server does not run local Skills, repository scans, model inference, terminals, or LangGraph.
- The implementation is not Build Agent-specific.
- Clients cannot assign Task status, attempt numbers, authoritative timestamps, actors, duration, or lifecycle
  outcomes outside legal server-side transitions.

## Verification Record

See `docs/00-context/atlas-cli-platform-integration-traceability.md` and `review.md` for exact command results,
review findings, browser evidence, and the single environment-gated Oracle test.

## Follow-Up

Deployment owners must configure production Integration client digests and an external fail-closed Artifact
scanner, then execute the real-Oracle V20-to-V21 test in a disposable rollout environment. No product-scope
follow-up is required for this archived change.
