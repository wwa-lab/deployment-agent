# ADR-0002: Install SDD Skills Globally Across Coding Tools

## Status

Accepted

## Date

2026-05-26

## Context

The repository contains a mature Spec Driven Development skill family under `.agents/skills/` and `.claude/skills/`. These skills are useful beyond this repository because they define reusable workflows for requirements, user stories, specs, architecture, design, task breakdown, implementation, document review, code/design review, docs/code consistency, and ADR capture.

The user wants these workflows available globally across Codex, Claude Code, and OpenCode so every project can use the same SDD practices without copying them manually each time.

## Decision

Install the SDD skill family into the global skill locations for Codex, shared OpenAI-style agents, Claude Code, and OpenCode:

- `~/.codex/skills/`
- `~/.agents/skills/`
- `~/.claude/skills/`
- `~/.config/opencode/skills/`

Also provide an OpenCode global `/sdd` command at `~/.config/opencode/commands/sdd.md` that routes a request to the relevant global SDD skill.

Within this repository, treat `.agents/skills/` as the canonical source for the SDD skill family and mirror compatible skills into `.claude/skills/`.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Keep SDD skills project-local only | Other projects would not automatically benefit from the mature workflow library. |
| Copy only into Claude Code | The user explicitly wants Codex, Claude Code, and OpenCode support. |
| Rely only on global prose instructions | Prose instructions are less reusable than concrete skill folders with references and templates. |
| Build a package manager immediately | A simple global sync is enough for now and easier to inspect. |

## Consequences

### Positive

- New projects can use the same SDD workflows immediately.
- Codex and Claude Code can discover `SKILL.md`-based workflows from their global locations.
- OpenCode gets a practical `/sdd` command even if native skill-folder loading is unavailable.
- SDD practice becomes a workstation-level capability instead of a single-project habit.

### Negative

- Global copies can drift from the repository source if skills change and are not re-synced.
- OpenCode's command-based bridge is less native than Codex/Claude skill discovery.

### Neutral / Operational

- Re-sync global skills after editing any SDD skill.
- Keep `.opencode/commands/sdd.md` aligned with the installed skill list.
- Consider a dedicated sync script if this pattern is used often.

## Review Triggers

Revisit this decision when:

- OpenCode adds or standardizes native `SKILL.md` folder loading.
- The SDD skill family is packaged as a plugin or external repository.
- Global skill drift becomes frequent enough to justify automation.
- A project needs a different SDD workflow and should intentionally override the global default.

## Related Documents

- [Global SDD skills playbook](../global-sdd-skills-playbook.md)
- [Context engineering and ADR playbook](../context-engineering-adr-playbook.md)
- [ADR-0001](ADR-0001-use-context-engineering-and-adrs.md)
