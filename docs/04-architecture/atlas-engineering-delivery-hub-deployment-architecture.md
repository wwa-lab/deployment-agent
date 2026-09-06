# Architecture: Atlas Engineering Delivery Hub - Deployment Package

> **Historical packaging baseline — 2026-09-07 notice.** Current presentation scope is governed by the [Hub specification, current revision](../03-spec/atlas-engineering-delivery-hub-spec.md). Deployment remains an implemented module with its existing name; the evidence does not establish a second independent competition solution. Earlier English-default, separate-entry and commit requirements below are superseded for this documentation revision. Original samples remain unchanged; runtime contracts are not modified.

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled
**Lifecycle stage:** M6 Deployment

## System Context

Atlas Engineering Delivery Hub - Deployment sits in the Seven Mountains SDLC as the M6 Deployment tool. It receives delivery evidence from M4 Build and M5 Testing, runs controlled release operations, and emits release evidence for M7 Maintenance feedback.

## Component View

| Component | Responsibility |
|---|---|
| Public package docs | Explain positioning, current scope, workflow, contribution path, and safety boundaries. |
| SDD package chain | Preserve traceability from requirements through tasks for the packaging change. |
| Visual assets | Show M6 lifecycle position, internal workflow, and upstream/downstream relationships. |
| Sample output package | Demonstrate representative release input/output without sensitive data. |
| Runtime reference docs | Preserve detailed implementation, architecture, design, and operational baselines. |

## Runtime Boundary Summary

The existing runtime system is a Spring Boot + Vue workspace with shared platform services and agent-specific workspaces. For this package, the Deployment workspace is the relevant M6 capability. Shared platform capabilities provide authentication, access grants, configuration, audit, template download, task state, and release-flow services.

## Data And State Strategy

The documentation package does not introduce new runtime state. It describes existing runtime state at a conceptual level:

- Release Flow for top-level release tracking.
- Request for stage-scoped rundown context.
- Task for executable release steps.
- Task execution history for attempts and external execution metadata.
- Audit log for human and system traceability.
- Access Grant and configuration records for governance.

## Integration Strategy

The package describes existing integrations without adding new ones:

- Upstream handoff from Build and Testing evidence is a narrative contract in this package.
- Jenkins and Ansible/AWX execution adapters are current runtime integration paths.
- Maintenance feedback is a downstream target and remains planned/TBD for this package.

## Security Considerations

- Samples must be synthetic.
- Credentials must not appear in docs, diagrams, JSON samples, or screenshots.
- The contribution guide must require environment and secret safety.
- Public materials must preserve human approval as the current release-control model.

## Tradeoffs

- The package keeps older umbrella framework docs as context instead of deleting them, which preserves history but requires clear linking to avoid mixed messaging.
- The package favors Mermaid diagrams and synthetic data over screenshots because screenshots may expose internal context.
