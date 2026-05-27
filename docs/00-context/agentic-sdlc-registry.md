# Agentic SDLC Registry

## Status

Accepted

## Last Synced

2026-05-27

## Purpose

This registry tracks the reusable Agentic SDLC assets installed globally or bridged locally across Codex, shared OpenAI-style agents, Claude Code, OpenCode, and GitHub Copilot.

## Global Installation Targets

| Tool | Path | Status |
|---|---|---|
| Codex | `~/.codex/skills/` | Active |
| Shared OpenAI-style agents | `~/.agents/skills/` | Active |
| Claude Code | `~/.claude/skills/` | Active |
| OpenCode | `~/.config/opencode/skills/` | Active |
| OpenCode command router | `~/.config/opencode/commands/sdd.md` | Active |
| GitHub Copilot repository bridge | `.github/copilot-instructions.md` | Active |
| GitHub Copilot Agentic SDLC instructions | `.github/instructions/agentic-sdlc.instructions.md` | Active |

## Skill Registry

| Skill | Version | Canonical Source | Purpose |
|---|---|---|---|
| `agentic-sdlc-doctor` | 0.2.0 | `.agents/skills/agentic-sdlc-doctor/` | Check global Agentic SDLC installation and routing health |
| `agentic-sdlc-orchestrator` | 0.1.0 | `.agents/skills/agentic-sdlc-orchestrator/` | Coordinate propose/apply/verify/archive lifecycle work |
| `architecture-to-design` | 0.1.0 | `.agents/skills/architecture-to-design/` | Generate design artifacts from architecture/spec inputs |
| `context-engineering-adr` | 0.1.0 | `.agents/skills/context-engineering-adr/` | Capture durable context and architecture decisions |
| `cross-ide-skill-router` | 0.2.0 | `.agents/skills/cross-ide-skill-router/` | Route shared skills across coding tools |
| `design-to-tasks` | 0.1.0 | `.agents/skills/design-to-tasks/` | Convert design into implementation tasks |
| `execution-manifest` | 0.1.0 | `.agents/skills/execution-manifest/` | Pin agent handoff inputs, outputs, constraints, and verification |
| `freshness-gate` | 0.1.0 | `.agents/skills/freshness-gate/` | Check whether docs, code, tests, and approvals are stale |
| `req-to-user-story` | 0.1.0 | `.agents/skills/req-to-user-story/` | Convert raw requirements into user stories |
| `review-code-against-design` | 0.1.0 | `.agents/skills/review-code-against-design/` | Review code changes against intended design |
| `review-doc-quality` | 0.1.0 | `.agents/skills/review-doc-quality/` | Review SDD artifact quality and readiness |
| `review-docs-against-code` | 0.1.0 | `.agents/skills/review-docs-against-code/` | Review documentation against actual code |
| `sdd-profile-manager` | 0.1.0 | `.agents/skills/sdd-profile-manager/` | Define or choose project SDD profiles |
| `sdd-slice-bootstrap` | 0.1.0 | `.agents/skills/sdd-slice-bootstrap/` | Create or audit complete SDD slice document sets |
| `spec-to-architecture` | 0.1.0 | `.agents/skills/spec-to-architecture/` | Convert specifications into architecture |
| `tasks-to-code` | 0.1.0 | `.agents/skills/tasks-to-code/` | Convert task breakdowns into code-oriented implementation guidance |
| `tasks-to-implementation` | 0.1.0 | `.agents/skills/tasks-to-implementation/` | Implement from structured tasks |
| `user-story-to-spec` | 0.1.0 | `.agents/skills/user-story-to-spec/` | Convert user stories into specifications |

## Supporting Assets

| Asset | Path | Purpose |
|---|---|---|
| SDD profile | `docs/00-context/sdd-profile.md` | Active project SDD chain and gates |
| Discipline profile | `docs/00-context/agent-discipline-profile.md` | AI behavior vs programmatic gate policy |
| Execution manifest schema | `docs/00-context/execution-manifest.schema.json` | Machine-checkable manifest contract |
| Doctor script | `scripts/agentic-sdlc-doctor.sh` | Local/global health check |
| Sync script | `scripts/sync-global-agent-assets.sh` | Push canonical skills to global locations |
| OpenCode router | `.opencode/commands/sdd.md` | OpenCode entry point for skill routing |
| GitHub Copilot repository instructions | `.github/copilot-instructions.md` | Repository-wide Copilot bridge into Agentic SDLC |
| GitHub Copilot Agentic SDLC instructions | `.github/instructions/agentic-sdlc.instructions.md` | Copilot path-specific and agent routing for shared skills |
| Agentic SDLC CI | `.github/workflows/agentic-sdlc.yml` | CI template for doctor, secret scan, backend test, and frontend build |

## Maintenance Rules

- Update this registry whenever a global skill or supporting asset is added, renamed, removed, or materially changed.
- Run `scripts/sync-global-agent-assets.sh` after changing canonical skills.
- Run `scripts/agentic-sdlc-doctor.sh` after sync and before relying on global tool behavior.
- Increment a skill version when its workflow contract changes, not for typo-only edits.
