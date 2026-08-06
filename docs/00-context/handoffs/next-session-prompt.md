# Next Session Kickoff Prompt

Copy everything in the fenced block below into a new IDE / agent session.

User intent: **regenerate** Service Directory SDD via the mandatory skill chain (do not accept the draft as-is).

```text
Continue WWA Agent Hub work from durable handoff — do not use chat memory.

## Start (mandatory)
1. Read `docs/00-context/AGENT_HANDOFF.md` first.
2. Read `PROJECT_RULES.md`, `DEVELOPMENT_STANDARDS.md`, `docs/00-context/sdd-profile.md`, `docs/SDD-BOOTSTRAP.md`.
3. Read entry skill and run the full chain (do not hand-write SDD ad hoc):
   - `.agents/skills/wwa-sdd-generate-all/SKILL.md` (or `.claude/skills/wwa-sdd-generate-all/SKILL.md`)
   - Then read and follow in order:
     1. `req-to-user-story`
     2. `user-story-to-spec`
     3. `spec-to-architecture`
     4. `architecture-to-design`
     5. `design-to-tasks`
     6. `review-doc-quality`
   - Use `architecture-review` because this slice adds Platform API/persistence and catalog boundaries.
4. Use existing draft + prototype as input context (not final truth):
   - `docs/prototypes/wwa-service-directory.html`
   - Current draft under `docs/01-requirements/service-directory-*` … `docs/06-tasks/service-directory-*`
   - `docs/00-context/service-directory-traceability.md`

## Language / process rules
- English-only for project rules and SDD (ADR-0009). Do not create `.zh-CN.md` companions.
- Completion report must include: `SDD skill chain used: yes`, skill files read, `architecture-review` result, `review-doc-quality` result.
- Before ending the session with progress, update `docs/00-context/AGENT_HANDOFF.md` last.

## Current goal
Regenerate the full English SDD set for slice `service-directory`, then stop for my acceptance before implementation unless I explicitly ask to continue coding.

Service Directory = Platform catalog page: config-driven scopes → groups → links, filters, Recently used, DEVOPS_ADMIN manage + audit; catalog separate from Configuration Management.

## Do next (in order)
1. Establish slice contract from handoff + prototype + draft.
2. Regenerate/overwrite the full English document set via the skill chain:
   - `docs/01-requirements/service-directory-requirement.md`
   - `docs/02-user-stories/service-directory-user-stories.md`
   - `docs/03-spec/service-directory-spec.md`
   - `docs/04-architecture/service-directory-architecture.md`
   - `docs/04-architecture/service-directory-data-flow.md` (include; slice is stateful/config)
   - `docs/04-architecture/service-directory-data-model.md`
   - `docs/05-design/service-directory-design.md`
   - `docs/05-design/contracts/service-directory-API_IMPLEMENTATION_GUIDE.md` (API is in scope)
   - `docs/06-tasks/service-directory-tasks.md`
   - `docs/00-context/service-directory-traceability.md`
3. In regenerated docs, close or explicitly propose defaults for:
   - SD-T00 persistence: recommend Option A (JSON document) unless evidence says otherwise
   - SD-T01 Guest: propose read-only allow vs redirect; leave as Open Question if I must choose
   - SD-T02 ARCAD/GitHub Enterprise URLs: placeholders OK
   - SD-T03 Config Management separation: capture as ADR recommendation if needed
4. Run `review-doc-quality` against the full set; fix blockers.
5. Update `AGENT_HANDOFF.md` with regenerate complete + waiting for user acceptance.
6. **Do not start implementation** until I accept the regenerated SDD.

## Constraints
- Do not store Service Directory in Configuration Management entities.
- No mock role switch for production auth.
- Respect HITL safety rails in `CLAUDE.md`.
- Do not expand scope beyond Service Directory.
- Do not commit unless I explicitly ask.

## First reply expected from you
- Confirm you read the handoff and will regenerate (not accept draft as-is)
- List the skill files you will read
- Then start the skill chain and produce the SDD set
```
