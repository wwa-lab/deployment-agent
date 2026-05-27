---
description: Run the global Spec Driven Development skill workflow
agent: build
subtask: false
---

# SDD Command

Use the global SDD skill library for: $ARGUMENTS

## Skill Location

Read the relevant skill from:

```text
~/.config/opencode/skills/
```

## Routing

Choose the matching skill based on the user's requested stage:

| Intent | Skill |
|---|---|
| propose a change | `agentic-sdlc-orchestrator` |
| apply an accepted change | `agentic-sdlc-orchestrator` |
| verify a change | `agentic-sdlc-orchestrator` |
| archive a completed change | `agentic-sdlc-orchestrator` |
| requirements to user stories | `req-to-user-story` |
| user stories to specification | `user-story-to-spec` |
| specification to architecture | `spec-to-architecture` |
| architecture to detailed design | `architecture-to-design` |
| design to task breakdown | `design-to-tasks` |
| tasks to implementation | `tasks-to-implementation` |
| tasks to code | `tasks-to-code` |
| review document quality | `review-doc-quality` |
| review code against design | `review-code-against-design` |
| review docs against code | `review-docs-against-code` |
| context engineering or ADRs | `context-engineering-adr` |
| choose or define SDD profile | `sdd-profile-manager` |
| bootstrap a new SDD slice | `sdd-slice-bootstrap` |
| prepare pinned agent handoff | `execution-manifest` |
| check stale docs/code/tests | `freshness-gate` |
| route skills across IDEs | `cross-ide-skill-router` |
| doctor or health check | `agentic-sdlc-doctor` |

For `/sdd propose`, `/sdd apply`, `/sdd verify`, and `/sdd archive`, route through `agentic-sdlc-orchestrator` first. It may delegate to the narrower skills.

## Required Behavior

1. Open the selected `SKILL.md`.
2. Follow its workflow before producing output.
3. Load files under `_shared/` or the skill's `references/` only when the selected skill asks for them.
4. Preserve repository-local instructions such as `AGENTS.md`, `CLAUDE.md`, `OPENCODE.md`, and SDD gates.
5. For non-trivial or user-facing changes, keep requirements, stories, spec, architecture, design, tasks, code, tests, and ADRs traceable.

## Output

State which SDD skill was used and why, then produce the requested artifact, review, or implementation result.
