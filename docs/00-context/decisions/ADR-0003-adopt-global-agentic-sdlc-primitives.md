# ADR-0003: Adopt Global Agentic SDLC Primitives From Control Tower Experience

## Status

Accepted

## Date

2026-05-26

## Context

The SDD skill family is already installed globally across Codex, Claude Code, OpenCode, and shared agent skill locations. The next reusable layer is not another project-specific document chain, but a small set of Agentic SDLC primitives that make the workflow portable across project types and tools.

The Agentic SDLC Control Tower repository demonstrates several reusable patterns:

- SDD slices should produce a complete document set before implementation starts.
- Project workflows should be profile-driven instead of hard-coded to one repository shape.
- Agents should consume pinned execution context instead of guessing from "latest" files.
- Freshness should be checked before approvals, reviews, and implementation.
- Tools without native skill loading can use a short skill router/index instead of duplicating skill bodies.

## Decision

Adopt five global Agentic SDLC primitives as reusable skills:

- `sdd-profile-manager`
- `sdd-slice-bootstrap`
- `execution-manifest`
- `freshness-gate`
- `cross-ide-skill-router`

Install these skills alongside the existing global SDD skills for Codex, shared OpenAI-style agents, Claude Code, and OpenCode. Keep `.agents/skills/` as the canonical project-local source and mirror compatible skills into `.claude/skills/` and global skill directories.

## Alternatives Considered

| Alternative | Why Not |
|---|---|
| Add more rules to root instruction files only | Large root instructions are harder to reuse and easier to ignore. |
| Build a full Control Tower app first | The reusable workflow primitives are valuable immediately without waiting for product implementation. |
| Keep all patterns Control-Tower-specific | These patterns apply to many repositories and tools, not only the Control Tower product. |
| Create one large "agentic-sdlc" skill | Smaller focused skills route better and keep context loading lighter. |

## Consequences

### Positive

- New projects can choose an SDD profile before generating documents.
- Feature slices can be bootstrapped or audited consistently.
- Agent handoffs can be pinned with explicit manifests.
- Stale documents, code, tests, and approvals can be detected before work proceeds.
- Codex, Claude Code, and OpenCode can share one workflow source through routers and global skill sync.

### Negative

- The global skill set is larger and needs periodic synchronization.
- OpenCode still relies on a command/index bridge rather than native `SKILL.md` discovery.

### Neutral / Operational

- Update `scripts/sync-global-agent-assets.sh` when adding global skills.
- Keep `.opencode/commands/sdd.md` aligned with the global skill list.
- Record further reusable primitives as ADRs instead of scattering them across chats.

## Review Triggers

Revisit this decision when:

- A dedicated package/plugin is created for global Agentic SDLC skills.
- OpenCode or another tool changes its native skill discovery model.
- Control Tower adds a materially better primitive that should replace one of these.
- The global skill set becomes too broad and needs splitting into profiles or bundles.

## Related Documents

- [Global SDD skills playbook](../global-sdd-skills-playbook.md)
- [Context engineering and ADR playbook](../context-engineering-adr-playbook.md)
- [ADR-0002](ADR-0002-install-sdd-skills-globally.md)
