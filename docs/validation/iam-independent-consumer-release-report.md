# Independent Consumer Release Report

## Release candidate

- Branch: `codex/release-readiness`
- Candidate commit: `32cdef46632c4e1441c4a9c81020e77fbbc7b696`
- Intended artifact: `cloud.muer:muer-spring-boot-starter`
- Recommendation: ready for release review; retain the branch until an
  explicit merge or release decision.

## Independence boundary

`muer-example` is a host application. Its production code uses public IAM
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
| Consumer public API check | Pass | Pass in GitHub Actions run `33722777356` |
| MySQL 8.4 and Redis 7 Docker preflight | Pass; never skipped | Pass in both containerized jobs |
| `IamConsumerIntegrationTest` | Pass: authentication, RBAC, scope, update, revocation | Pass in `Independent Consumer acceptance` |
| Existing consumer showcase suite | Pass | Pass in `Docker/Testcontainers consumer showcase` |
| Starter package build | Pass | Pass in `verify` |

## Remote CI evidence

GitHub Actions [run 33722777356](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/33722777356)
completed successfully for the candidate commit on `codex/release-readiness`.
All required jobs passed: `verify`, `Docker/Testcontainers consumer showcase`,
and `Independent Consumer acceptance`. The latter completed its Docker
preflight, public API boundary check, and `IamConsumerIntegrationTest`.

## Local evidence

The following commands completed successfully on the release-readiness worktree
with Docker available. They are local evidence only and do not replace the
required GitHub Actions run.

| Command | Result |
| --- | --- |
| `bash scripts/test-consumer-public-api.sh` | Pass; rejects a synthetic internal import and accepts a public SPI import. |
| `bash scripts/test-showcase-readme.sh` | Pass. |
| `mvn -pl muer-example -am -Dtest=IamStarterAutoConfigurationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` | Pass: 3 tests. |
| `mvn -pl muer-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | Pass: 5 tests for RBAC, project scope, invalid token, Profile switch, and revocation. |
| `mvn -pl muer-example -am -Pintegration -Dtest=IamStarterConsumptionTest,IamSecurityIntegrationTest,IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | Pass at the time of execution: 6 + 4 + 3 tests; the Consumer suite has since grown to 5 focused tests. |

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
