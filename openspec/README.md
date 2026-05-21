# OpenSpec Integration

OpenSpec is connected to this repository as a lightweight change-entry layer.

- Use `openspec/changes/<change-id>/` for active change proposals, scope, acceptance, and implementation checklists.
- Keep the existing `docs/02-user-stories` through `docs/06-tasks` workflow as the deeper implementation layer.
- Do not duplicate the same requirement or design decision in both places. For an active change, OpenSpec is the source of truth for the change scope; local SDLC docs are updated only when their durable content changes.
- Preserve the project rules in `AGENTS.md`, `CLAUDE.md`, and `.agents/skills`.

Generated assistant integrations live in `.codex/skills`, `.claude/commands`, `.claude/skills`, and `.opencode`.
