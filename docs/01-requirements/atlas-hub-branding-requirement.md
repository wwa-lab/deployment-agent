# Atlas Hub Branding Requirement

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Slice key:** `atlas-hub-branding`

## 1. Background

The repository is now positioned for the open-collaboration competition as **Atlas Engineering Delivery Hub**, a team delivery framework. The current UI and metadata still show older top-level labels such as `WWA Platform`, `WWA Control Center`, and `Deployment Agent` as the browser title. That can make reviewers think the whole product is still WWA or only Deployment Agent.

The desired naming is:

- Root product / repo display / main UI: **Atlas Engineering Delivery Hub**.
- Short UI label when space is tight: **Atlas Hub**.
- Agent name: **Deployment Agent** remains valid.
- Lifecycle stage label: **M6 Deployment** remains a stage, not the agent name.

## 2. Goals

- Make the first UI impression match the README: Atlas Engineering Delivery Hub is the root product.
- Preserve Deployment Agent as the M6 Deployment agent inside the Hub.
- Keep current technical paths stable for this slice.
- Update docs and metadata enough that the naming decision is traceable.

## 3. Functional Requirements

| ID | Requirement |
|---|---|
| AHB-REQ-01 | Browser title, login hero, home page, sidebar, topbar, and primary navigation shall present Atlas Engineering Delivery Hub / Atlas Hub as the root product. |
| AHB-REQ-02 | Deployment Agent shall remain the display name for the deployment agent/workspace. |
| AHB-REQ-03 | Current route/API/package identifiers shall remain stable in this slice, including `/wwa/deployment-agent`, `/api/deployment-agent`, Maven `artifactId`, Java package names, and database names. |
| AHB-REQ-04 | README, architecture notes, changelog, and package metadata shall describe Atlas Engineering Delivery Hub as the visible product brand. |
| AHB-REQ-05 | Frontend build and documentation checks shall pass after the branding update. |

## 4. Out Of Scope

- GitHub remote repository rename.
- Local directory rename.
- API route migration or aliasing.
- Java package namespace migration.
- Database table or migration renames.
- Replacing historical WWA references in dated design archives.

## 5. Acceptance Criteria

1. The browser tab title reads `Atlas Engineering Delivery Hub`.
2. Login and home page hero copy present Atlas Engineering Delivery Hub as the framework.
3. Sidebar/topbar use Atlas Hub as the product shell label.
4. Deployment Agent still appears as a workspace/agent label.
5. Docs explain the compatibility boundary between visible brand and technical identifiers.
6. `cd frontend && npm run build`, `git diff --check`, and `node scripts/check-markdown-links.mjs` pass.
