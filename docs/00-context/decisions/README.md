# Architecture Decision Records

This directory stores durable Architecture Decision Records (ADRs) for decisions that shape system architecture, cross-agent conventions, security posture, data ownership, integrations, or AI-agent working context.

## Rules

- One ADR records one decision.
- Accepted ADRs are immutable except for typo fixes, link repairs, or status changes.
- Reversing a decision requires a new ADR that supersedes the old one.
- Architecture and design documents should link to ADRs instead of duplicating their rationale.
- Coding agents should read relevant ADRs before changing cross-cutting behavior.

## Index

| ADR | Status | Decision |
|---|---|---|
| [ADR-0001](ADR-0001-use-context-engineering-and-adrs.md) | Accepted | Use context engineering and ADRs for durable project memory |
| [ADR-0002](ADR-0002-install-sdd-skills-globally.md) | Accepted | Install SDD skills globally across coding tools |
| [ADR-0003](ADR-0003-adopt-global-agentic-sdlc-primitives.md) | Accepted | Adopt global Agentic SDLC primitives from Control Tower experience |
| [ADR-0004](ADR-0004-add-agentic-sdlc-orchestrator-and-discipline-profile.md) | Accepted | Add orchestrator, discipline profile, and doctor for predictable AI engineering workflow |
| [ADR-0005](ADR-0005-add-registry-schema-and-ci-gates.md) | Accepted | Add registry, execution manifest schema, and CI gates for enforceable workflow checks |
| [ADR-0006](ADR-0006-route-github-copilot-through-agentic-sdlc.md) | Accepted | Route GitHub Copilot through the shared Agentic SDLC workflow |
