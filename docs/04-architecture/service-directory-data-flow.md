# Data Flow: Resource Center

> **Slice:** `service-directory`
> **Status:** Regenerated via `spec-to-architecture`; **Amended 2026-07-25** — product renamed to **Resource Center**; optional link `iconKey` whitelist (SD-FR-71)
> **Product name:** Resource Center · **Slice id:** `service-directory`
> **Last updated:** 2026-07-25
> **Source spec:** `docs/03-spec/service-directory-spec.md`
> **Companions:** `service-directory-architecture.md`, `service-directory-data-model.md`

This document describes how directory data moves through the system: which flows exist, what each one
carries, where data changes shape, and what is deliberately never transmitted. Diagrams are plain
text, matching the architecture document's style.

---

## 1. Data Objects In Play

| Object | Lives in | Lifetime | Crosses the network? |
|---|---|---|---|
| **Stored catalog document** | One row in the Resource Center catalog table | Durable; one per environment | No — server-side only |
| **Seed catalog** | Application package | Immutable, ships with the build | No |
| **Catalog read projection** | Response body of the read endpoint | Per request | Server → browser |
| **Mutation request** | Request body of a mutation endpoint | Per request | Browser → server |
| **Mutation response** | Response body of a mutation endpoint (the updated projection) | Per request | Server → browser |
| **Client catalog state** | Frontend store | Per page session | No |
| **View model** (filtered, grouped) | Derived in the browser | Per keystroke / click | No |
| **Recently used list** | Browser local storage | Durable per browser | **Never** — deliberately not sent to the server |
| **Audit context map** | Audit record written by the audit capability | Durable | No (written in-process) |

Two properties are architectural, not incidental:

1. The **stored document is never exposed raw**. Every response is a projection filtered by the caller's role.
2. The **Recently used list never leaves the browser**. There is no endpoint that accepts it, so it cannot leak by accident.

---

## 2. Flow A — Catalog Read (Page Load)

```
Browser                          Hub API                        Domain module              Database
   │                                │                                │                        │
   │  GET catalog (session cookie)  │                                │                        │
   ├───────────────────────────────►│                                │                        │
   │                                │ resolve user context           │                        │
   │                                │ (roles from session)           │                        │
   │                                │                                │                        │
   │                                │  read(includeDisabled?)        │                        │
   │                                ├───────────────────────────────►│                        │
   │                                │                                │  load single catalog row│
   │                                │                                ├───────────────────────►│
   │                                │                                │◄───────────────────────┤
   │                                │                                │  row or empty          │
   │                                │                                │                        │
   │                                │                    ┌───────────┴───────────┐            │
   │                                │                    │ row missing?          │            │
   │                                │                    │  → install seed once  │            │
   │                                │                    │  → insert row ────────┼───────────►│
   │                                │                    └───────────┬───────────┘            │
   │                                │                                │                        │
   │                                │                    ┌───────────┴───────────┐            │
   │                                │                    │ project for caller:   │            │
   │                                │                    │  reader → enabled only│            │
   │                                │                    │  admin  → all, marked │            │
   │                                │                    └───────────┬───────────┘            │
   │                                │◄───────────────────────────────┤                        │
   │◄───────────────────────────────┤  catalog projection            │                        │
   │  one JSON payload              │                                │                        │
   │                                │                                │                        │
   ├── store in client catalog state                                 │                        │
   ├── resolve Recently used ids against the loaded catalog          │                        │
   └── derive view model (filters + stage focus + search)            │                        │
```

**Carried on the wire:** scope / group / link structure and display text, kinds, URLs, ordering, enabled
flags, and the catalog version. Nothing else — no user data, no audit data, no configuration data.

**Not carried:** disabled entries for non-administrators (removed server-side, not hidden client-side),
and anything about other users.

---

## 3. Flow B — Administrator Mutation

```
Browser (manage mode)            Hub API                     Domain module            DB        Audit
   │                                │                             │                   │          │
   │ submit create/update/delete    │                             │                   │          │
   │ (update+delete carry           │                             │                   │          │
   │  expectedVersion; create       │                             │                   │          │
   │  carries none)                 │                             │                   │          │
   ├───────────────────────────────►│                             │                   │          │
   │                                │ guest write already blocked │                   │          │
   │                                │ upstream by auth chain      │                   │          │
   │                                │                             │                   │          │
   │                                │ role check: DEVOPS_ADMIN?   │                   │          │
   │                                │   no → 403, stop ───────────┼───────────────────┼──────────┤ (no audit)
   │                                │   yes ↓                     │                   │          │
   │                                ├────────────────────────────►│                   │          │
   │                                │                             │ load catalog row  │          │
   │                                │                             ├──────────────────►│          │
   │                                │                             │◄──────────────────┤          │
   │                                │                 ┌───────────┴───────────┐       │          │
   │                                │                 │ update/delete only:   │       │          │
   │                                │                 │ expectedVersion ==    │       │          │
   │                                │                 │ loaded version?       │       │          │
   │                                │                 │  no → 409 ────────────┼───────┼──────────┤ (no audit)
   │                                │                 └───────────┬───────────┘       │          │
   │                                │                 ┌───────────┴───────────┐       │          │
   │                                │                 │ validate structure    │       │          │
   │                                │                 │ + URL shape           │       │          │
   │                                │                 │  fail → 400 ──────────┼───────┼──────────┤ (no audit)
   │                                │                 └───────────┬───────────┘       │          │
   │                                │                 ┌───────────┴───────────┐       │          │
   │                                │                 │ apply change to doc   │       │          │
   │                                │                 │  (cascade for scope   │       │          │
   │                                │                 │   or group delete)    │       │          │
   │                                │                 └───────────┬───────────┘       │          │
   │                                │                             │ save (JPA @Version)          │
   │                                │                             ├──────────────────►│          │
   │                                │                             │  in-flight clash? │          │
   │                                │                             │   → 409, no change│          │
   │                                │                             │◄──────────────────┤          │
   │                                │                             │  committed, v→v+1 │          │
   │                                │                             │                   │          │
   │                                │                             │ emit one audit entry         │
   │                                │                             ├─────────────────────────────►│
   │                                │                             │ (own transaction; failure    │
   │                                │                             │  logged, mutation stands)    │
   │                                │◄────────────────────────────┤                   │          │
   │◄───────────────────────────────┤ updated catalog projection   │                   │          │
   │ replace client catalog state    │                             │                   │          │
   └── re-derive view model          │                             │                   │          │
```

**Ordering guarantee that matters:** authorization → version precondition → validation → persistence →
audit. No audit entry can exist for a change that was not persisted, and no persisted change can be lost
because audit failed.

**Two conflict checks, not one.** The precondition compares the client's `expectedVersion` against the
row just loaded, which is the only way to detect a page that went stale minutes ago; `@Version` at save
time catches the much narrower case of two requests overlapping in flight. Both surface as 409. Creates
skip the precondition entirely, because appending cannot overwrite anyone. Spec: SD-FR-44, SD-FR-67,
SD-FR-68.

---

## 4. Flow C — Link Activation And Recently Used

This flow is entirely client-side. It is drawn because it is where the only client-owned durable data
is written.

```
User clicks a link card
        │
        ▼
┌───────────────────────────────┐
│ URL host ends in ".invalid"?  │
└───────────┬───────────────────┘
      yes   │   no
   ┌────────┘   └────────────────────────┐
   ▼                                     ▼
Blocked: render as             ┌──────────────────────┐
"URL pending", no              │ kind = workspace?    │
navigation, NOT recorded       └───────┬──────────────┘
                                  yes  │  no
                            ┌──────────┘  └───────────────┐
                            ▼                             ▼
                   in-app router navigation      new browser tab (noopener)
                            └──────────────┬──────────────┘
                                           ▼
                        ┌─────────────────────────────────────┐
                        │ Recently used write:                │
                        │  1. put this link id at the front   │
                        │  2. remove any earlier duplicate    │
                        │  3. truncate to 8 entries           │
                        │  4. persist to versioned local key  │
                        └─────────────────────────────────────┘
                                           │
                                    no network call
                                    no audit entry
```

On the next page load the stored ids are resolved against the freshly loaded catalog; ids that no
longer resolve (link deleted or disabled) are dropped silently, which is how the list self-heals.

---

## 5. Flow D — Seed Provisioning (First Run Only)

```
Application package                Domain module                    Database
┌──────────────────┐    read on first catalog access    ┌──────────────────────┐
│  Seed catalog    ├───────────────────────────────────►│  catalog table       │
│  · 7 SDLC stage  │                                    │                      │
│    groups        │   only when the table has no row   │  after insert: one   │
│  · Common:       │   ──────────────────────────────►  │  row, version 0      │
│    Platform +    │                                    │                      │
│    Engineering   │   validated by the SAME rules as   └──────────────────────┘
│  · External      │   administrator input
│  · pending URLs  │
│    as .invalid   │   never overwrites an existing row —
└──────────────────┘   including a deliberately emptied catalog
```

The seed is packaged content, not a database configuration key. That distinction is what keeps it
outside Configuration Management's ownership.

---

## 6. Field Mapping

### 6.1 Prototype shape → domain shape

The prototype is the accepted UX baseline, so its data shape is the starting point. This mapping records
every rename and every field that is deliberately **not** persisted.

| Prototype field | Domain field | Disposition |
|---|---|---|
| `scopes[].key` | scope `key` | Kept; normalised to lower case and pattern-validated |
| `scopes[].label` | scope `title` | Renamed for consistency with the rest of the model |
| `scopes[].layout` (`stage-strip` / `buckets`) | scope `layout` | Kept — drives stage-rail versus bucket rendering |
| `scopes[].system` | scope `system` | Kept — protects the three seeded scopes from deletion |
| `scopes[].enabled`, `sortOrder` | same | Kept |
| `groups[].scopeKey` | containment inside the scope's group list | Structural: the document nests groups inside scopes, so no foreign key field is stored |
| `groups[].type` (`stage` / `bucket`) | group `type` | Kept |
| `groups[].name` | group `title` | Renamed |
| `groups[].order` **and** `sortOrder` | group `stageOrder` (display number) + `sortOrder` (ordering) | Split: the prototype conflated the stage's visible number with its ordering key |
| `groups[].agent` | group `agentName` | Renamed for clarity; it is display text, not a reference to an `AgentId` |
| `groups[].agentOwner` | — | **Dropped.** Team ownership belongs to the Agent Contribute Dashboard, not the link catalog |
| (implicit in the prototype) | group `stageKey` | **Added** so stage identity is explicit and validatable against the seven stage keys |
| `groups[].badge`, `badgeClass`, `numClass` | — | **Dropped.** Pure presentation; derived in the frontend from scope and group type |
| `links[].id` | link `id` | Kept as a stable identifier |
| `links[].kind` | link `kind` | Kept: `docs` / `tool` / `workspace` / `repo` |
| `links[].label` | link `kindLabel` | Renamed — it is the short pill caption, not the title |
| `links[].title` | link `title` | Kept |
| `links[].desc` | link `description` | Renamed |
| `links[].url` | link `url` | Kept; validated by shape and scheme on write |
| `links[].tone`, `links[].mark` | — | **Dropped from storage as authoritative presentation.** Still used as the **fallback** badge when `iconKey` is absent; derived from kind and title |
| (amended 2026-07-25) | link `iconKey` | **Added.** Optional whitelist key for a local card icon (SD-FR-71). Not an image URL |
| `links[].enabled`, `sortOrder` | same | Kept |
| (draft SDD proposal) `openInNewTab` | — | **Not stored.** Open behavior is derived from kind, so the two can never disagree |
| (draft SDD proposal) `iconUrl` / uploaded icon | — | **Not stored in MVP.** Icons are local assets selected by `iconKey` only |
| (draft SDD proposal) `tags`, `audience`, `environments`, `source` | — | **Not stored in MVP.** No requirement consumes them; speculative fields would need validation and UI they do not have |

### 6.2 Mutation request → stored document

| Request input | Transformation before storage |
|---|---|
| Scope or group `key` | Trim, lower-case, pattern check, uniqueness check within its level |
| Any `title` / `description` | Trim; length-checked; stored as given otherwise |
| Link `url` | Trim; scheme and shape validated per kind; stored verbatim (no normalisation that could change the target) |
| Link `iconKey` | Trim; blank → `null`; non-blank must be a member of the platform whitelist enum; never interpreted as a URL |
| `sortOrder` | Range-checked integer; defaults to the end of the sibling list when omitted on create |
| Link `id` | Server-assigned on create; immutable afterwards |
| Catalog `version` | Never stored from the request. On an update or delete it arrives as `expectedVersion` and is used only as a precondition, then discarded; the stored version is always the server's own counter |
| Audit attribution | Taken from the server-side user context, never from the request body |

### 6.3 Mutation → audit context map

| Audit context key | Value source |
|---|---|
| entity type | `scope` / `group` / `link`, from the endpoint used |
| entity id or key | The affected entity's identifier or key |
| entity title | The affected entity's title at the time of the change |
| operation | `create` / `update` / `delete` |
| removed descendants | For a cascade delete only: counts of removed groups and links |
| link move source and destination | For a link update **that changed parent** only: `from_scope_key`, `from_group_key`, `to_scope_key`, `to_group_key`. All four together, or none |
| operator identity, role, actor kind, timestamp | Supplied by the audit capability from the user context |

The move keys are the one place where the audited fact is not visible in the entity's own fields. A link
carries no parent reference — it is positioned by where it sits in the document — so an audit entry
without them cannot distinguish "moved the production runbook out of Deployment" from "fixed a typo in
its title". They are omitted rather than written as equal values when nothing moved, so their presence is
itself the signal that a move happened.

---

## 7. End-To-End Data Path Summary

```
Seed catalog (packaged)
        │  once, into an empty store, same validation as admin input
        ▼
Stored catalog document ── single row, single version ──┐
        ▲                                              │
        │ read-modify-write inside one transaction      │ role-based projection
        │                                              ▼
Administrator mutation                        Catalog read projection
        │  (403 / 400 / 404 / 409 stop here)            │
        │                                              ▼
        │                                   Client catalog state
        │                                              │
        ├── one audit entry (HUMAN actor) ──► Audit store    │ derive
        │                                                    ▼
        └── updated projection returned ──────────►  View model (filter · stage focus · search)
                                                             │
                                                             ▼
                                                    Rendered page ──► link activation
                                                                            │
                                                                            ▼
                                                            Recently used (browser only, max 8)
```

**Invariants this path guarantees:**

1. Every byte a reader receives passed through the role projection, so hidden content cannot leak.
2. Every persisted change has exactly one audit entry, and every audit entry corresponds to a persisted change.
3. No catalog data reaches Configuration Management stores, because no flow in this document touches them.
4. No personal usage data reaches the server, because no flow carries the Recently used list outward.
5. A stale administrator write cannot overwrite a newer one: the version check rejects it before the document changes.
