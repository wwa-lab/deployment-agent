# Agent Contribute Dashboard Prompt Log

**Date:** 2026-05-26
**Status:** Backfilled

---

## Purpose

This prompt log captures the product steering inputs that shaped the Agent Contribute Dashboard MVP. It exists to preserve SDD traceability for future iteration.

---

## Key Product Inputs

1. Add a new `Agent Contribute Dashboard`.
2. Clarify different agent owners.
3. Encourage team co-build culture.
4. Help new joiners onboard.
5. Give management a factual basis for contribution review.
6. Do not make this an agent registration function.
7. Use the Qilianshan SDLC model with seven stages.
8. Do not use Chinese UI copy.
9. Do not use nested mountain terminology.
10. Use the previous design direction but make it simpler.
11. Discovery and Maintenance are not implemented.
12. Testing Agent is in progress.
13. Admin users should be able to change stage status.
14. Role model is Agent Owner, Sub-agent Owner, Process Owner, Technical Leader, and Co-Build.
15. Treat workstreams as contribution items.
16. Add Backlog as a status option.
17. Add Confluence links for guideline and feedback.
18. Remove Next Action because it made the page too complex.

---

## Derived Implementation Prompts

### Prompt A: Dashboard MVP

Build a WWA shared-control dashboard that maps the seven Qilianshan SDLC stages to implementation status, ownership, and contribution coverage. Keep the UI concise and English-only.

### Prompt B: Admin Status Control

Allow `DEVOPS_ADMIN` users to update selected stage status using allowed values Implemented, In Progress, Backlog, and Not Implemented. Persist the status through platform configuration.

### Prompt C: Role Attribution

Display each contribution item with Agent Owner, Sub-agent Owner, Process Owner, Technical Leader, Co-Build partners, and contribution description.

### Prompt D: Confluence Resources

Add stage-level links for Guideline and Feedback so users can open Confluence pages from the selected-stage detail panel.

---

## Prompt Guardrails for Future Changes

- Do not add scores without a separately approved scoring spec.
- Do not add agent registration workflow to this page.
- Do not add dense action guidance into the top map unless the product owner explicitly accepts the added complexity.
- Keep UI copy English-only.
- Keep real Confluence URLs data-driven.
