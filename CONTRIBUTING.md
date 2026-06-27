# Contributing To Atlas Engineering Delivery Hub - Deployment

Thank you for contributing. This repository is packaged for the internal open collaboration competition as the **Atlas Engineering Delivery Hub - Deployment** Tool entry, the M6 Deployment-stage capability in the Atlas Engineering Delivery Hub / Seven Mountains SDLC narrative.

Contributions should strengthen controlled release operations: traceability, validation, approval safety, rollout repeatability, rollback readiness, and reusable deployment adapters/templates.

## Who Should Contribute

- Release engineers and DevOps practitioners who operate SIT / UAT / PROD releases.
- Build and testing owners who want cleaner handoff evidence into deployment.
- Platform engineers improving shared auth, audit, access, configuration, and task services.
- Frontend contributors improving the release-flow workspace experience.
- Documentation contributors who can make release workflows easier to understand and reuse.
- Security and compliance reviewers who can improve credential safety, auditability, and approval controls.

## Good First Contribution Areas

- Improve sanitized sample release inputs and outputs under `docs/samples/`.
- Add or refine Mermaid diagrams that explain M6 Deployment workflows.
- Improve README, submission, pitch, or contribution-guide clarity.
- Add documentation links from the deployment index to existing verified docs.
- Add focused tests around validation, state transitions, or adapter edge cases.
- Improve error messages for upload, task action, and decision failures.

## Deployment Adapter Contribution Areas

Deployment adapters and execution integrations must be scoped carefully. Good areas include:

- Jenkins target resolution and submission hardening.
- Ansible/AWX target handling and status mapping.
- External execution polling behavior, when explicitly enabled.
- Adapter tests with mocked HTTP clients and sanitized URLs.
- Configuration validation for endpoint/credential readiness.
- Clear user-facing failure messages without leaking secrets.

Adapter contributions must not hardcode endpoints, tokens, kubeconfigs, environment names, or customer-specific assumptions.

## Documentation Rules

- Keep `README.md` reviewer-friendly and focused on the M6 Deployment Tool.
- Keep the larger Atlas Engineering Delivery Hub narrative as context, not as the repo's competition category.
- Put deeper detail under `docs/` and link it from `docs/atlas-engineering-delivery-hub-deployment-index.md`.
- Preserve useful technical detail by linking existing reference docs instead of deleting it.
- For non-trivial or user-facing changes, update or backfill the relevant SDD artifacts before implementation.
- Mark backfilled SDD documents as `Backfilled` when they describe existing code.
- Use synthetic names and sanitized sample data.
- Prefer Mermaid source plus rendered SVG when diagram tooling is available.

## Testing And Validation Expectations

Use the smallest validation set that safely matches the change:

- Documentation-only package changes: `git diff --check` and `node scripts/check-markdown-links.mjs`.
- Diagram changes: render Mermaid to SVG when `mmdc` or `npx @mermaid-js/mermaid-cli` is available.
- Backend changes: `mvn test`.
- API changes: update controller or contract tests under `src/test/java/`.
- Frontend changes: `cd frontend && npm run build`; run frontend tests when relevant.
- UI changes: capture before/after screenshots when practical and safe.
- Release workflow changes: include at least one happy path and one blocked/failure path in tests or docs.

## Secret Handling And Environment Safety

- Never commit secrets, passwords, API tokens, private keys, kubeconfigs, cloud credentials, or real production endpoint values.
- Never include customer data, internal confidential screenshots, or real environment names in samples.
- Use environment variables, configuration management, or a secret manager for runtime credentials.
- Redact credential values in docs, logs, samples, and screenshots.
- If a secret is exposed, stop, rotate it, remove it through the approved history-cleanup process, and inspect for similar leaks.
- Samples should use values such as `SAMPLE_APP`, `SAMPLE_GROUP`, and `https://example.invalid/...`.

## Release Safety And Rollback Expectations

- Human review remains mandatory for release progression in the current baseline.
- Do not introduce autonomous approval without a documented SDD slice and security review.
- New task-state behavior must preserve audit history and execution attempts.
- Rerun, reject, skip, fail, archive, restore, and purge semantics must be documented and tested when changed.
- Do not claim one-click rollback unless the code implements it. Current rollback support is traceability, failure marking, rerun/reject flows, and documented recovery handoff.
- User-facing release changes must update `CHANGELOG.md`.

## PR Checklist

- [ ] Scope is clear and tied to an SDD artifact when required.
- [ ] M6 Deployment positioning remains accurate and does not present this repo as the whole Atlas framework.
- [ ] README and deployment docs index links are updated when discoverability changes.
- [ ] Diagrams include Mermaid source and rendered output when tooling is available.
- [ ] Samples are synthetic and contain no credentials or customer data.
- [ ] Backend/API changes include focused tests.
- [ ] Frontend/UI changes build successfully and screenshots are safe when included.
- [ ] Validation commands and results are included in the PR.
- [ ] `CHANGELOG.md` is updated for user-facing changes.
- [ ] No unrelated files, lockfiles, `.env` files, or credentials are included.

## Commit Message Style

Use conventional commits:

```text
docs: package atlas engineering delivery hub deployment tool
feat: add deployment adapter validation
fix: correct release decision audit metadata
test: add auto execution adapter coverage
chore: refresh deployment diagrams
```

Keep commits focused. Stage only files related to the change.
