---
name: cross-ide-skill-router
description: Use this skill when making Codex, Claude Code, OpenCode, GitHub Copilot, Gemini, or other coding agents share the same workflow skills. Creates or updates tool-specific instruction files, skill indexes, command shims, and global skill sync rules without duplicating workflow content.
---

# Cross-IDE Skill Router

Use this skill to expose one workflow library across multiple coding tools.

## Purpose

Keep the workflow source portable and tool-neutral. Tool-specific files should route to the shared skill, not copy the whole workflow.

## Preferred Source Order

1. Project-local canonical skill directory, usually `.agents/skills/`
2. Mirrored tool-native skill directory, such as `.claude/skills/`
3. Global skill directory:
   - Codex: `~/.codex/skills/`
   - Shared agents: `~/.agents/skills/`
   - Claude Code: `~/.claude/skills/`
   - OpenCode: `~/.config/opencode/skills/`
4. Tool command/index shim:
   - GitHub Copilot: `.github/copilot-instructions.md`, `.github/instructions/*.instructions.md`
   - OpenCode: `~/.config/opencode/commands/*.md`
   - Gemini-style router: `.gemini/skills-index.md`

## Workflow

1. Inventory available tools:
   - `AGENTS.md`
   - `CLAUDE.md`
   - `OPENCODE.md`
   - `GEMINI.md`
   - `.github/copilot-instructions.md`
   - `.github/instructions/*.instructions.md`
   - local and global skill directories
2. Choose the canonical workflow source.
3. Mirror only when a tool needs a native directory.
4. For tools without native skill loading, create a short routing file or command.
5. Keep tool-specific instructions short:
   - where to find the skill
   - when to use it
   - how to route requests
6. Verify identical mirrored skill content with `diff -qr`.

## Router Template

```markdown
# Tool Skill Router

When the user asks for [workflow family]:

1. Read the skill index.
2. Select the matching skill.
3. Open the referenced `SKILL.md`.
4. Follow that skill as the source workflow.
5. Preserve repository-local instructions.
```

## Rules

- Do not duplicate large skill bodies inside root instruction files.
- Do not fork skill behavior per tool unless a compatibility issue requires it.
- Record durable routing decisions in ADRs.
- Re-sync global skill directories after changing canonical skills.
