# ADR-0001: Use Context Engineering And ADRs For Durable Project Memory

## Status

Accepted

## Date

2026-05-26

## Context

This repository already has a strict SDD workflow and a durable context area under `docs/00-context/`. It is also operated by multiple coding agents and tools, including Codex, Claude Code, and OpenCode. Important project knowledge currently appears in a mix of SDD documents, architecture decision sections, root agent instructions, and conversational history.

The project needs a lightweight way to preserve reusable engineering experience across tools and future projects without adding a competing planning system.

## Decision

Adopt context engineering and ADRs as a reusable documentation layer:

- Long-lived project background, terms, boundaries, and agent working rules live under `docs/00-context/`.
- Architecture Decision Records live under `docs/00-context/decisions/`.
- Non-trivial architectural or cross-cutting decisions must be captured as ADRs.
- Codex, Claude Code, and OpenCode should use the same durable context and ADRs before changing architecture or shared conventions.
- Project-specific SDD artifacts remain the primary source for feature scope and implementation traceability.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Keep decisions only inside architecture documents | Important rationale becomes hard to find and easy to duplicate across documents. |
| Store decisions only in chat logs or PR comments | Chat and PR context is not durable enough for future agents or maintainers. |
| Adopt a heavy ADR web platform immediately | The current need is lightweight, repo-local, and portable across projects. |
| Replace SDD with ADRs | ADRs record decisions; they do not replace requirements, specs, designs, or tasks. |

## Consequences

### Positive

- Future maintainers can find the reason behind important decisions.
- AI agents can share the same durable context instead of relying on tool-specific memory.
- ADRs provide a clean place for reusable experience that applies across projects.
- Existing SDD flow remains intact and gains better rationale traceability.

### Negative

- Contributors must decide when a change is important enough to deserve an ADR.
- ADRs can become stale if accepted records are not superseded when decisions change.

### Neutral / Operational

- Root agent instruction files should point to the ADR workflow instead of copying all details.
- ADRs should be reviewed with architecture and design changes.
- Tool-specific skill folders may mirror the same workflow so Codex and Claude Code can discover it natively.

## Review Triggers

Revisit this decision when:

- The repository adopts a dedicated ADR tool such as `adr-tools`, `log4brains`, or another decision-management platform.
- ADRs become numerous enough to need generated indexes or static-site publication.
- A project chooses a different primary spec or decision framework.
- OpenCode, Codex, or Claude Code standardizes a different native skill format that removes the need for mirrored instructions.

## Related Documents

- [Context documents](../)
- [Context engineering and ADR playbook](../context-engineering-adr-playbook.md)
- [Project architecture](../../04-architecture/architecture.md)
- [Codex agent instructions](../../../AGENTS.md)
- [Claude Code instructions](../../../CLAUDE.md)
- [OpenCode instructions](../../../OPENCODE.md)
