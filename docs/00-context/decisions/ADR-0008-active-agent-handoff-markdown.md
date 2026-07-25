# ADR-0008: Active agent handoff markdown for IDE/agent switches

## Status

Accepted

## Date

2026-07-25

## Context

Agents and IDEs (Cursor, Claude Code, Codex, Copilot, OpenCode) frequently switch mid-slice. Chat history is not durable resume state. WWA already has:

- Slice traceability files
- Execution manifests (machine-pinned handoffs)
- Bilingual SDD after ADR-0007

What was missing is a single, always-current, human-readable **active handoff** that every new session reads first and every outgoing session updates last.

## Decision

1. Maintain an active English handoff at `docs/00-context/AGENT_HANDOFF.md` (ADR-0009: no Chinese companion required).
2. **Incoming session rule:** before product/SDD/implementation work, read the active handoff, then follow its sources-of-truth order.
3. **Outgoing session rule:** before ending a session with meaningful progress, update the handoff (status, done, next, blockers, verification, branch/commit if known).
4. Optional archives go under `docs/00-context/handoffs/` when a major phase completes; templates live there too.
5. Relationship to other artifacts:
   - Handoff = resume narrative across tools
   - Traceability = slice document/ID map
   - Execution manifest = pinned machine contract for remote/async agents
   - Chat = not a resume source

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Rely on chat / IDE composer memory only | Lost across tools and sessions |
| Traceability only | Too static; does not capture “what next right now” |
| Execution manifest only | Too heavy for every IDE switch; YAML less readable for humans |
| Per-slice handoff only (no active pointer) | New sessions cannot find the current work without guessing |

## Consequences

### Positive

- Any agent/IDE can continue with the same next steps
- Forces explicit closeout of progress and blockers
- Complements manifests without replacing them

### Negative

- Extra maintenance on every meaningful session end
- Stale handoff risk if agents forget the closeout rule

### Neutral / Operational

- Wire the rule into `PROJECT_RULES.md`, `AGENTS.md`, and `CLAUDE.md`
- Doctor/sync scripts do not need to validate handoff content initially

## Review Triggers

Revisit when:

- Handoffs repeatedly go stale
- A lighter or automated handoff generator is introduced
- Goal-loop tooling (like Atlas manifests) is fully ported

## Related Documents

- `docs/00-context/AGENT_HANDOFF.md`
- `docs/00-context/handoffs/README.md`
- `.agents/skills/execution-manifest/SKILL.md`
- ADR-0007
