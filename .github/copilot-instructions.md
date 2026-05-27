# GitHub Copilot Instructions

This repository uses a shared Agentic SDLC workflow. Treat this file as a
GitHub Copilot bridge into the same rules used by Codex, Claude Code, and
OpenCode.

## Primary Contract

- Read and follow `AGENTS.md` first. It is the primary repository contract.
- Keep repository-local instructions stricter than global preferences.
- For non-trivial or user-facing changes, follow `docs/00-context/sdd-profile.md`
  and keep the SDD artifact chain aligned under `docs/01-requirements`,
  `docs/02-user-stories`, `docs/03-spec`, `docs/04-architecture`,
  `docs/05-design`, and `docs/06-tasks`.
- Do not add user-facing features as code-only work.

## Skill Routing

The canonical workflow source is `.agents/skills/`. When a workflow is needed,
read the matching `SKILL.md` and follow it instead of inventing a new process.

Use these routes by default:

- Lifecycle work: `.agents/skills/agentic-sdlc-orchestrator/SKILL.md`
- Context and ADR changes: `.agents/skills/context-engineering-adr/SKILL.md`
- SDD slice creation or audit: `.agents/skills/sdd-slice-bootstrap/SKILL.md`
- Agent handoff setup: `.agents/skills/execution-manifest/SKILL.md`
- Staleness checks: `.agents/skills/freshness-gate/SKILL.md`
- Cross-tool routing changes: `.agents/skills/cross-ide-skill-router/SKILL.md`
- Workflow health checks: `.agents/skills/agentic-sdlc-doctor/SKILL.md`

Also apply `.github/instructions/agentic-sdlc.instructions.md` when GitHub
Copilot supports path-specific or agent instructions for the current surface.

## Verification

- Run the relevant build and test command from `AGENTS.md` for code changes.
- Run `scripts/agentic-sdlc-doctor.sh` after workflow, instruction, skill,
  ADR, registry, or routing changes.
- Never modify `.env`, lockfiles, or CI secrets unless the user explicitly asks.
