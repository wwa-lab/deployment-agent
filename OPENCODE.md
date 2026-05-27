# OPENCODE.md

This file gives OpenCode the same durable project-memory rules used by Codex and Claude Code.

## Context Engineering And ADRs

- Treat `docs/00-context/` as the long-lived project context layer.
- Treat `docs/00-context/decisions/` as the Architecture Decision Record (ADR) log.
- Before changing architecture, platform boundaries, security posture, data ownership, integrations, or shared agent conventions, read the relevant context documents and ADRs.
- When a change introduces or reverses a significant decision, create a new ADR instead of burying the rationale in chat, PR text, or implementation notes.
- Do not duplicate ADR rationale across SDD documents; link to the ADR from architecture or design docs.
- If code or implementation guidance conflicts with an accepted ADR, stop and propose either a code change or a superseding ADR.

## Portable Skill Source

The reusable workflow is mirrored in:

- `.agents/skills/context-engineering-adr/`
- `.claude/skills/context-engineering-adr/`
- Global SDD skills: `~/.config/opencode/skills/`
- Global SDD command: `~/.config/opencode/commands/sdd.md`
- GitHub Copilot bridge: `.github/copilot-instructions.md` and `.github/instructions/agentic-sdlc.instructions.md`

OpenCode should follow the same workflow even if it does not load those skill folders natively.

## Agentic SDLC Primitives

Use `/sdd` for both classic SDD stages and global Agentic SDLC primitives:

- `agentic-sdlc-orchestrator`
- `agentic-sdlc-doctor`
- `sdd-profile-manager`
- `sdd-slice-bootstrap`
- `execution-manifest`
- `freshness-gate`
- `cross-ide-skill-router`
