# Handoff Archives

## Active handoff

The always-current resume file is:

- `docs/00-context/AGENT_HANDOFF.md`

New sessions start there. Outgoing sessions update that file last.

English-only (ADR-0009).

## When to archive

When a major phase completes (for example SDD accepted, Phase 1 shipped), copy the active handoff into:

```text
docs/00-context/handoffs/{slice}-{YYYYMMDD}.md
```

Then reset/update the active `AGENT_HANDOFF` for the next goal.

## Template

Use `_template.md` when starting a fresh active handoff for a new slice.

## Relation to other artifacts

| Artifact | Role |
|---|---|
| `AGENT_HANDOFF.md` | Human/agent readable resume narrative across IDE/agent switches |
| `{slice}-traceability.md` | Slice document map and ID trace |
| `execution-manifests/*.yaml` | Machine-pinned inputs/outputs for remote/async agents |
| Chat history | Not a resume source |
