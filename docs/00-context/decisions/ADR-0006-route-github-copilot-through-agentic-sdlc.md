# ADR-0006: Route GitHub Copilot Through The Shared Agentic SDLC Workflow

## Status

Accepted

## Date

2026-05-27

## Context

The Agentic SDLC workflow is already available to Codex, shared OpenAI-style
agents, Claude Code, and OpenCode through project-local skills, mirrored global
skills, and tool-specific routers. GitHub Copilot has its own custom
instruction surfaces, including repository-wide instructions,
path-specific instruction files, and agent instruction files such as
`AGENTS.md`. Copilot does not necessarily load `.agents/skills/` as a native
skill system in every surface, so it needs a lightweight bridge into the same
workflow library.

## Decision

Add GitHub Copilot instruction files that route Copilot to the existing
Agentic SDLC sources instead of creating a separate Copilot workflow:

- `.github/copilot-instructions.md` as the repository-wide bridge.
- `.github/instructions/agentic-sdlc.instructions.md` as the path-specific and
  agent routing bridge.
- `scripts/agentic-sdlc-doctor.sh` checks to keep the Copilot bridge present
  and aligned with the registry.

The canonical workflow source remains `.agents/skills/`, with `AGENTS.md`,
`docs/00-context/sdd-profile.md`, ADRs, and the doctor script acting as the
shared control plane.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Rely on `AGENTS.md` only | Copilot surfaces explicitly support repository and path instruction files; using them makes the route more visible and reliable. |
| Copy all skill bodies into Copilot instructions | This would create another workflow fork and increase drift risk. |
| Create Copilot-specific SDD rules | The goal is one shared workflow across tools, not tool-specific process variants. |
| Wait until Copilot has native `SKILL.md` support everywhere | The current bridge is useful now and can be simplified later if native support appears. |

## Consequences

### Positive

- GitHub Copilot can see the same SDD, ADR, discipline, and verification rules.
- Copilot behavior is routed to the canonical `.agents/skills/` source.
- Doctor checks can catch missing or stale Copilot routing files.
- The repository now has a clearer cross-tool entry surface for Copilot Chat,
  Copilot coding agent, Copilot CLI, and Copilot code review.

### Negative

- Two additional `.github` instruction files must be maintained.
- Copilot may still differ by surface in how much instruction context it loads.

### Neutral / Operational

- Keep Copilot instruction files short and router-oriented.
- Update the registry when Copilot routing assets change.
- Run `scripts/agentic-sdlc-doctor.sh` after changing Copilot instructions.

## Review Triggers

Revisit this decision when:

- GitHub Copilot gains native support for the same skill layout used by Codex
  and Claude Code.
- Copilot custom instruction loading behavior changes materially.
- The shared Agentic SDLC workflow is packaged as an external reusable asset.

## Related Documents

- [Agentic SDLC registry](../agentic-sdlc-registry.md)
- [SDD profile](../sdd-profile.md)
- [Agent discipline profile](../agent-discipline-profile.md)
- [Global SDD skills playbook](../global-sdd-skills-playbook.md)
- [GitHub Copilot repository custom instructions](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions)
- [GitHub Copilot CLI custom instructions](https://docs.github.com/en/copilot/how-tos/copilot-cli/add-custom-instructions)
