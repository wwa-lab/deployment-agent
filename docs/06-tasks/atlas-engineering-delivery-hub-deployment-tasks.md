# Tasks: Atlas Engineering Delivery Hub - Deployment Package

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled

## Definition Of Done

- Root README and Chinese README present the repo as the M6 Deployment Tool.
- Submission materials and pitch are updated for Tool category.
- Contribution guide covers release safety, rollback posture, secrets, testing, and adapter work.
- Diagrams and sanitized samples exist.
- Existing detailed docs are preserved through links.
- Validation is performed.
- Commit is created with the requested message.

## Task List

| ID | Task | Status |
|---|---|---|
| M6-T01 | Inspect repository docs, code surface, scripts, tests, and current changes. | Done |
| M6-T02 | Backfill SDD package chain for the Deployment Tool slice. | Done |
| M6-T03 | Rewrite English README for M6 Deployment Tool positioning. | Done |
| M6-T04 | Rewrite Chinese README naturally with matching positioning. | Done |
| M6-T05 | Update competition submission docs and add deployment pitch. | Done |
| M6-T06 | Update contribution guide for deployment adapters, validation, secrets, rollback, and PR checklist. | Done |
| M6-T07 | Add lifecycle, workflow, and upstream/downstream diagrams. | Done |
| M6-T08 | Add sanitized mini output sample package. | Done |
| M6-T09 | Update changelog for the user-facing documentation package. | Done |
| M6-T10 | Validate diff, links, diagrams, tests, and secret safety. | Done |
| M6-T11 | Commit with `docs: package atlas engineering delivery hub deployment tool`. | Done in final packaging commit |

## Verification Plan

1. `git diff --check`
2. `node scripts/check-markdown-links.mjs`
3. Render Mermaid diagrams to SVG when tooling is available.
4. Run discoverable cheap tests.
5. Inspect staged/working diff for secrets, credentials, kubeconfigs, customer data, and internal screenshots.
