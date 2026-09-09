# Muer Repository Architecture Refactor Verification Report

Date: 2026-09-09
Branch: `codex/repository-architecture-refactor`
Base: `origin/main` at `ca9788b`

## Decision

**NOT READY FOR FINAL INTEGRATION REVIEW: environment-only Docker blocker.**

The repository reorganization and non-container verification are complete. The
remaining release gate is to rerun the MySQL and Redis Testcontainers suites in
an environment exposing a working Docker socket. No test was weakened or
disabled to make the current WSL run pass.

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
- `mvn -B -f examples/quickstart/pom.xml test` — 3 tests, 0 failures
- `mvn -B -f examples/showcase/pom.xml test` — 10 tests, 0 failures
- `mvn -B -f test-apps/consumer-acceptance/pom.xml test -DskipTests` — compile
- `bash scripts/verify-consumer-public-api.sh test-apps/consumer-acceptance/src/main/java`
- `bash scripts/verify-repository-layout.sh`
- `bash scripts/test-docs-workflow.sh`
- `bash scripts/verify-documentation-contract.sh`
- `bash scripts/test-showcase-readme.sh`
- `bash scripts/test-muer-identity.sh`
- Admin Console: `npm ci`, `npm run api:generate`, `npm run type-check`,
  `npm test` (14 tests), and `npm run build` under the `wust` environment
- Docs Site: `npm ci`, `npm run check` (0 errors, 0 warnings, 0 hints), and
  `npm run build` (66 pages)
- `git diff --check`

Blocked:

- `mvn -B clean verify` reached the persistence integration tests, then failed
  because Testcontainers could not find `/var/run/docker.sock`.
- `mvn -B -f test-apps/consumer-acceptance/pom.xml verify` has the same
  Docker/Testcontainers prerequisite and must be rerun with Docker available.

The Docs Site build retains its pre-existing non-fatal warnings about the
optional i18n directory and the 404 content entry. The Admin Console build
retains its pre-existing chunk-size warning and test-time router injection
warnings.

## Required follow-up

1. Enable Docker Desktop WSL integration or expose a valid Docker socket to
   this WSL distribution.
2. Rerun `mvn -B clean verify` from the repository root.
3. Rerun `mvn -B -f test-apps/consumer-acceptance/pom.xml verify` and the
   Showcase integration profile.
4. Record the Docker-backed results, then perform the normal branch review and
   integration decision. This branch has not been merged or pushed.
