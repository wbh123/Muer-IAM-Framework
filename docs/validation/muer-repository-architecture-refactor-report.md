# Muer Repository Architecture Refactor Verification Report

Date: 2026-09-09
Branch: `codex/repository-architecture-refactor`
Base: `origin/main` at `ca9788b`

## Decision

**READY FOR POST-REFACTOR ARCHITECTURE REVIEW.**

Docker Desktop WSL integration is available and the MySQL and Redis
Testcontainers suites now run successfully. No test was weakened or disabled
to make the WSL run pass.

## Delivered architecture

- Published Java modules are under `modules/`; the HTTP module is now
  `modules/muer-http-api` and publishes `cloud.muer:muer-http-api`.
- `contracts/openapi/iam.yaml` is the sole OpenAPI source used by Java and the
  Admin Console generators.
- `examples/quickstart` and `examples/showcase` are independent Spring Boot
  consumers and are not root Reactor modules.
- `test-apps/consumer-acceptance` is an independent CI consumer that verifies
  the public Starter boundary.
- `apps/muer-admin-console` and `apps/muer-docs-site` are independent frontend
  applications; Pages workflow settings were preserved while paths were
  updated.
- `tests/architecture` contains non-published repository boundary tests.
- `metadata/project-metadata.yaml`, README, contributing guidance, active docs,
  and CI workflows use the new layout.

## Compatibility evidence

- OpenAPI contract SHA-256 is unchanged from the baseline:
  `48ca623dda277817dad07f6bee903dd338548b1041c12489de962141f8755955`.
- Six existing Flyway migrations remain byte-for-byte unchanged; no migration
  was added or edited.
- `/iam/**` route inventory remains 34 paths, including authentication,
  sessions, profiles, diagnostics, administration, and audit endpoints.
- No old root module directories or legacy Maven artifact IDs remain.
- `bash scripts/verify-repository-layout.sh` reports
  `REPOSITORY_LAYOUT_VERIFIED`.

## Verification results

Passed:

- `mvn -B install -DskipTests`
- `mvn -B clean verify` — all 12 Reactor modules passed, including the MySQL
  and Redis Testcontainers suites (persistence: 14 tests; HTTP API: 48 tests;
  autoconfigure: 38 tests; architecture: 1 test)
- `mvn -B -f examples/quickstart/pom.xml test` — 3 tests, 0 failures
- `mvn -B -f examples/showcase/pom.xml -Pintegration test` — 23 tests, 0
  failures, using MySQL and Redis Testcontainers
- `mvn -B -f test-apps/consumer-acceptance/pom.xml test` — 2 tests, 0
  failures, using MySQL and Redis Testcontainers
- `bash scripts/verify-consumer-public-api.sh test-apps/consumer-acceptance/src/main/java`
- `bash scripts/verify-repository-layout.sh`
- `bash scripts/test-docs-workflow.sh`
- `bash scripts/verify-documentation-contract.sh`
- `bash scripts/test-showcase-readme.sh`
- `bash scripts/test-muer-identity.sh`
- Admin Console: `npm ci`, `npm run api:generate`, `npm run type-check`,
  `npm test` (14 tests), and `npm run build` in the local development environment
- Docs Site: `npm ci`, `npm run check` (0 errors, 0 warnings, 0 hints), and
  `npm run build` (66 pages)
- `git diff --check`

The Docs Site build retains its pre-existing non-fatal warnings about the
optional i18n directory and the 404 content entry. The Admin Console build
retains its pre-existing chunk-size warning and test-time router injection
warnings.

The Consumer Acceptance `verify` invocation completed its test phase
successfully, then its package phase waited on an external Maven mirror while
fetching packaging-plugin dependencies. The clean `test` invocation above is
the recorded consumer acceptance result; the root Reactor's `clean verify` and
`install` both completed successfully.

## Next review step

1. Review the isolated branch against `origin/main` and make the normal
   integration decision.
2. If a separately packaged Consumer Acceptance JAR is required, rerun its
   `verify` goal with a responsive Maven mirror or configured proxy.

This branch has not been merged or pushed.
