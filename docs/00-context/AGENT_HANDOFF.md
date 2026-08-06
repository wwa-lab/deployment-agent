# Agent Handoff (Active)

> **Read this file first** in every new IDE / agent / chat session before doing product or SDD work.  
> **Update this file last** before ending a session that made meaningful progress.

Do not resume from chat memory. Durable resume state is: **this handoff** + linked SDD/traceability + optional execution manifest + `git status`.

Language: English-only for project rules and SDD (ADR-0009).

---

## Status Snapshot

| Field | Value |
|---|---|
| Last updated | 2026-07-25 |
| Updated by | Cursor agent (Resource Center P0 layout + detail polish) |
| Active slice | `service-directory` (artifact id) · **product name: Resource Center** |
| Phase | **P0 layout + detail polish done — manual UAT pending** |
| Overall status | `implemented_pending_uat` |
| Implementation started? | Yes — W10/W9 + P0 UI densify + detail polish complete in code |
| Branch (if known) | check with `git status` / `git branch --show-current` |

---

## Goal (current)

1. Manual UAT walkthrough (SD-T62) as `emp-001`, `emp-003`, and guest — include new denser Resource Center layout.
2. Optional P1 polish (radius/layering/stage hues) if user wants after UAT.
3. Collect production ARCAD / GitHub Enterprise URLs (SD-T02) when available.
4. Commit only if the user explicitly asks.

---

## Done (do not redo)

- Full Resource Center vertical slice: backend (`domain/resourcecenter`), API `/api/platform/resource-center`, frontend `/wwa/resource-center`, registry `resource-center`
- **W10** — Product rename in code: route, registry, modules (`ResourceCenter*`), seed path `resource-center/seed-catalog.json`, audit `resource_center_update` / `resource_center_delete`, redirect `/wwa/service-directory` → `/wwa/resource-center`, recent key `wwa.resourceCenter.recent.v1`. Table `DA_SERVICE_DIRECTORY_CATALOG` and Flyway V20 unchanged.
- **W9** — Optional `iconKey` whitelist enum, validator, DTO, seed keys, local SVG assets, admin picker, contract test row 17
- **P0 layout** — `ResourceCenterView.vue` only: sticky toolbar + `/` search, compact Recent chips (no empty panel), left sticky nav + scroll-spy, de-glassed groups, compact 32px cards. Did **not** change `linkPresentation.ts` or `assets/resource-center/icons/*`.
- **Detail polish** — muted pending flags, no redundant kind labels, display-only strip of `Common ·` / `External ·` prefixes, 2-line description clamp, roomier card padding, stronger sidebar active inset.
- SDD chain accepted 2026-07-25; ADR-0010 Accepted
- Prototype file remains `docs/prototypes/wwa-service-directory.html`

---

## Next actions (ordered)

1. Manual UAT (SD-T62) — walkthrough in tasks doc; capture screenshots of new layout
2. P1 visual refinement only if user asks
3. SD-T02 — production URLs for ARCAD / GitHub Enterprise when ops provides them
4. Commit only if user explicitly asks

---

## Sources of truth (read in order)

1. This file — `docs/00-context/AGENT_HANDOFF.md`
2. `PROJECT_RULES.md` / `DEVELOPMENT_STANDARDS.md`
3. `docs/00-context/service-directory-traceability.md`
4. `docs/03-spec/service-directory-spec.md`
5. `docs/06-tasks/service-directory-tasks.md`
6. `docs/05-design/service-directory-design.md` + API guide v1.2
7. `docs/00-context/decisions/ADR-0010-service-directory-owns-its-catalog-store.md`

---

## Constraints

- Catalog **not** in Configuration Management
- Package root `com.wwa.agenthub`
- Do **not** rename `DA_SERVICE_DIRECTORY_CATALOG` / Flyway V20
- Icons: whitelist keys + local assets only — no icon URLs/uploads; do not regress W9 brand icons
- Do not commit unless the user explicitly asks
- HITL safety rails unchanged
- SDD artifact filenames stay `service-directory-*`

---

## Blockers / open questions

| # | Status |
|---|---|
| SD-OQ-01 Guest read | Closed |
| SD-OQ-02 Production URLs | Open (release only) |
| SD-OQ-06 / SD-OQ-07 SDD acceptance | **Closed — user accepted 2026-07-25** |

---

## Verification

| Check | Result |
|---|---|
| `mvn test` | **445/445 pass** (2026-07-25, post W10/W9) |
| `mvn test -Dtest=ResourceCenterControllerTest` | **17/17 pass** (includes checklist row 17 iconKey) |
| `cd frontend && npm run build` | **pass** (2026-07-25, post P0 layout + detail polish) |
| Grep gate (user-facing stale paths) | Only allowed `/wwa/service-directory` redirect in router |
| Manual UAT (SD-T62) | **Not run** |

---

## Session close checklist

- [x] W10 rename complete
- [x] W9 iconKey complete
- [x] P0 Resource Center layout restructure
- [x] Resource Center detail polish (pending/kind/nav titles/desc)
- [x] CHANGELOG + handoff updated
- [ ] Manual UAT when user ready
