# Atlas Hub Branding Specification

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Source stories:** AHB-US-01 through AHB-US-06

## 1. Overview

This specification updates visible product naming from the legacy WWA-centered shell toward the joint brand **WWA-Atlas Engineering Delivery Hub** while preserving **Deployment Agent** as the M6 Deployment agent/workspace.

The change is intentionally presentation-layer first. It is not a technical rename.

## 2. Actors

| Actor | Need |
|---|---|
| Competition reviewer | See WWA-Atlas Engineering Delivery Hub as the top-level product immediately. |
| Release contributor | Continue finding Deployment Agent as the M6 deployment workspace. |
| Maintainer | Preserve route/API/package stability. |
| Future contributor | Understand the naming boundary before making deeper technical changes. |

## 3. Functional Scope

### 3.1 Product Brand

Visible root-product surfaces must use:

- Full visible brand: `WWA-Atlas Engineering Delivery Hub`
- Short shell label: `WWA-Atlas Hub`
- Framework name: `Atlas Engineering Delivery Hub`

Surfaces include browser title, login hero, home hero, sidebar logo, sidebar primary nav, flyout home link, topbar kicker, topbar home link, and shared capability eyebrow copy.

### 3.2 Deployment Agent Naming

Deployment Agent remains the agent/workspace name. It must not be replaced by `Deployment Function` in the UI.

Approved relationship wording:

```text
Deployment Agent is the M6 Deployment capability inside WWA-Atlas Engineering Delivery Hub.
```

### 3.3 Compatibility Boundary

The following identifiers remain unchanged:

- `/wwa/*` Vue routes.
- `/wwa/deployment-agent` route.
- `/api/deployment-agent/*` backend routes.
- Maven `artifactId`.
- Java package namespace.
- Database tables and migration names.

### 3.4 Documentation And Metadata

The package should update:

- README / Chinese README wording where the implementation baseline is described.
- Architecture naming note.
- Changelog.
- `frontend/index.html` title.
- Maven display `name` and `description`, without changing `artifactId`.

### 3.5 Responsive Home Layout

The WWA-Atlas Hub home page should not be constrained to the previous narrow fixed-width shell on wide desktop screens.

Required behavior:

- The top-level home content area expands with the workspace viewport.
- The hero keeps a primary content column and a control-summary column on wide screens.
- Workspace cards and shared-control cards use responsive grids so additional width creates useful columns instead of empty margins.
- The layout collapses back to a single readable column on narrower screens.

## 4. Validation

Required checks:

```bash
git diff --check
node scripts/check-markdown-links.mjs
cd frontend && npm run build
```

Backend tests are not required for this presentation-layer change unless backend code changes are introduced.

## 5. Risks

| Risk | Mitigation |
|---|---|
| Reviewer does not connect the old WWA name to the new Atlas framework | Use WWA-Atlas Engineering Delivery Hub / WWA-Atlas Hub on top-level UI surfaces. |
| Contributor thinks Deployment Agent was renamed away | Keep agent cards, page titles, and route metadata as Deployment Agent. |
| Technical rename accidentally breaks links | Keep routes/API/package identifiers unchanged and document the boundary. |
