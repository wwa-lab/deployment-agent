# Agent Discipline Profile: deployment-agent

## Status

Accepted

## Purpose

This profile defines the quality disciplines that AI agents should follow in this repository and which disciplines require script, hook, CI, or human-review backing.

## Discipline Layers

| Layer | Strength | Examples | Owner |
|---|---|---|---|
| Programmatic gate | Strong | build, tests, static scans, CI checks | tool / CI |
| AI behavior constraint | Medium | skills, root instructions, review prompts | agent |
| Team convention | Weak | review norms, naming preferences, release habits | humans |

Critical quality rules should exist in at least two layers: AI behavior plus a programmatic or human gate.

## Active Disciplines

| Discipline | AI Skill / Rule | Programmatic Gate | Status |
|---|---|---|---|
| SDD before non-trivial work | `agentic-sdlc-orchestrator`, `sdd-profile-manager` | Manual review of SDD docs | Active |
| Durable decisions | `context-engineering-adr` | ADR index review | Active |
| Pinned agent handoff | `execution-manifest` | Manifest review | Active |
| Freshness before approval/release | `freshness-gate` | Doctor/freshness report | Active |
| TDD for code changes | Superpowers `test-driven-development` or local TDD skill | `mvn test`, frontend build/tests where present | Active |
| Code/design traceability | `review-code-against-design` | PR review | Active |
| Docs/code consistency | `review-docs-against-code` | Review before handoff | Active |
| Secret hygiene | security review skill | Recommended CI scan (`gitleaks` or equivalent) | Recommended |
| Dependency security | security review skill | Recommended dependency scan | Recommended |
| Coverage expectations | SDD task/test sections | Backend tests; frontend coverage still gap | Partial |

## Always Human-Gated

The following must not be auto-approved by an AI-only workflow:

- Production data deletion, masking, or cross-environment migration
- Access grant changes, especially admin grants
- Audit logging bypass or disablement
- Financial, regulatory, or external-audit-impacting actions
- Cross-environment data writeback
- First execution of a new risky task type before policy baseline exists

## Minimum Completion Discipline

Before claiming completion, an agent should provide:

- changed files summary
- SDD/ADR traceability notes
- verification commands and results
- freshness or staleness caveats
- open risks and follow-ups

## Related Documents

- [SDD profile](sdd-profile.md)
- [Global SDD skills playbook](global-sdd-skills-playbook.md)
- [ADR index](decisions/README.md)
