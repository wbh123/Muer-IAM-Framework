# Independent Consumer Release Report

## Release candidate

- Branch: `codex/release-readiness`
- Candidate commit: pending CI execution
- Intended artifact: `io.github.iamstarter:iam-spring-boot-starter`
- Recommendation: pending the required CI run and review of any findings.

## Independence boundary

`iam-example` is a host application. Its production code uses public IAM
contracts only: `IdentityAuthenticator`, `IamPrincipal`,
`ResourceHierarchyProvider`, `ResourceDescriptor`, `ResourceScope`,
`AuthorizationEngine`, and `AuthorizationRequest`. It does not import IAM
`internal`, `impl`, or `persistence` packages. The enforced check is:

```bash
bash scripts/verify-consumer-public-api.sh
```

## Acceptance evidence to record

| Check | Required result | Actual result |
| --- | --- | --- |
| Consumer public API check | Pass | Pending CI |
| MySQL 8.4 and Redis 7 Docker preflight | Pass; never skipped | Pending CI |
| `IamConsumerIntegrationTest` | Pass: authentication, RBAC, scope, update, revocation | Pending CI |
| Existing consumer showcase suite | Pass | Pending CI |
| Starter package build | Pass | Pending CI |

Record the GitHub Actions run URL, commit SHA, job conclusion, and any output
needed to reproduce a failed check. Do not include opaque tokens, passwords,
database contents, or environment secrets.

## Findings

| Priority | Finding | Owner | Resolution |
| --- | --- | --- | --- |
| P0 | None recorded | — | — |
| P1 | None recorded | — | — |
| P2 | None recorded | — | — |
| P3 | None recorded | — | — |
