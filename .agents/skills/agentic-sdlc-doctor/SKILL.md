---
name: agentic-sdlc-doctor
description: Use this skill when checking whether the global Agentic SDLC setup is healthy across Codex, shared agents, Claude Code, OpenCode, GitHub Copilot, project-local skills, OpenCode /sdd routing, ADR indexes, SDD profile, and sync script configuration.
---

# Agentic SDLC Doctor

Use this skill to verify that the Agentic SDLC toolchain is installed and internally consistent.

## Purpose

Catch workflow drift before it causes tool-specific behavior differences.

## Checks

- Project-local `.agents/skills/` and `.claude/skills/` contain the same global skills.
- Global targets contain the same skills:
  - `~/.codex/skills/`
  - `~/.agents/skills/`
  - `~/.claude/skills/`
  - `~/.config/opencode/skills/`
- OpenCode `/sdd` route exists and names all global skills.
- GitHub Copilot bridge exists and routes to `AGENTS.md`, `.agents/skills/`,
  the active SDD profile, and the doctor script.
- `docs/00-context/sdd-profile.md` exists.
- `docs/00-context/agent-discipline-profile.md` exists.
- `docs/00-context/agentic-sdlc-registry.md` exists and lists every global skill.
- `docs/00-context/execution-manifest.schema.json` exists and is valid JSON.
- The execution manifest template references the schema and contains required top-level keys.
- `.github/workflows/agentic-sdlc.yml` exists and includes doctor, secret scan, backend test, and frontend build checks.
- `.github/copilot-instructions.md` exists and points Copilot to the shared Agentic SDLC workflow.
- `.github/instructions/agentic-sdlc.instructions.md` exists for Copilot path-specific or agent instruction surfaces.
- ADR index includes every `ADR-*.md` file.
- Sync script includes every global skill.

## Workflow

1. Run `scripts/agentic-sdlc-doctor.sh` if it exists.
2. If the script is absent, perform the checks manually with `find`, `rg`, and `diff -qr`.
3. In CI, the script intentionally skips workstation-global directories unless `AGENTIC_SDLC_CHECK_GLOBALS=1`.
4. Report:
   - PASS checks
   - FAIL checks
   - WARN checks
   - minimal repair commands

## Output Format

```markdown
# Agentic SDLC Doctor Report

## Verdict

PASS | WARN | FAIL

## Checks

| Check | Result | Evidence |
|---|---|---|

## Repairs

1. [Step]
```
