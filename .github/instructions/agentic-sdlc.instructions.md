---
applyTo: "**"
---

# Agentic SDLC Routing

Use these instructions for repository work in GitHub Copilot Chat, Copilot
coding agent, Copilot CLI, and Copilot code review when this file is included
as context.

## Routing Order

1. Read `AGENTS.md` and preserve the repository contract.
2. For non-trivial or user-facing work, read `docs/00-context/sdd-profile.md`
   and update the relevant SDD artifacts before implementation.
3. Select the smallest matching workflow from `.agents/skills/` and read its
   `SKILL.md`.
4. Keep ADR-worthy decisions in `docs/00-context/decisions/`.
5. Verify the change using the repository commands and the Agentic SDLC doctor
   when workflow assets are touched.

## Required Skill Routes

- Use `agentic-sdlc-orchestrator` for propose, apply, verify, and archive work.
- Use `context-engineering-adr` when changing architecture, durable project
  context, shared agent conventions, integrations, security posture, or data
  ownership.
- Use `freshness-gate` before implementing from documents that may be stale.
- Use `cross-ide-skill-router` before changing Codex, Claude Code, OpenCode, or
  GitHub Copilot instruction surfaces.
- Use `agentic-sdlc-doctor` after changing skills, routers, ADR indexes,
  registry entries, execution manifest assets, or CI workflow gates.

## Copilot Boundary

GitHub Copilot may not load `.agents/skills/` as native skills in every surface.
When that happens, treat the referenced `SKILL.md` files as repository
instructions and follow their workflows manually.
