# Muer Repository Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Reorganize the Muer repository into explicit framework modules, shared contracts, applications, examples, external consumer tests, and architecture tests without changing runtime HTTP, database, authentication, authorization, or session semantics.

**Architecture:** The root Maven Reactor will contain only publishable Java modules under `modules/` plus non-published architecture tests under `tests/architecture`. `contracts/openapi/iam.yaml` will be the sole HTTP contract source. Examples and CI acceptance applications will build outside the Reactor against the installed public Starter artifact.

**Tech Stack:** Java 21, Maven, Spring Boot, OpenAPI Generator, Vue 3/TypeScript/Vite, Astro/Starlight, Testcontainers, Bash, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-09-muer-repository-architecture-refactor-design.md`

## Global Constraints

- All changes stay on `codex/repository-architecture-refactor`; do not modify `main`, merge, or push.
- Preserve every `/iam/**` route, OpenAPI operation semantics, `iam_*` database meaning, Flyway migration content, authentication, authorization, Session, First Admin, and `https://muer.cloud` URL slug.
- Do not add compatibility shells, deprecated Artifact aliases, database migrations, new authorization models, OAuth/OIDC, tenants, Gradle, pnpm, Nx, Turborepo, or Maven replacement.
- Use `git mv` for pure moves and explicitly classify files when splitting `muer-example`.
- Keep generated Java and TypeScript clients build-time generated and ignored according to the existing convention.
- Keep GitHub Pages settings unchanged; update only workflow source paths.

### Task 1: Capture the migration baseline

**Files:** Create `docs/validation/repository-architecture-baseline.md`; read the current OpenAPI contract, migrations, POMs, workflows, package manifests, and all `muer-example` files.

**Interfaces:** Produces the contract hash, migration hash, route inventory, Reactor inventory, and example file classification used by later tasks.

- [ ] Run `git status --short --branch`, `git log -1 --oneline`, and record the clean branch state.
- [ ] Hash `muer-management-web/src/main/resources/openapi/iam.yaml`; record all paths, methods, operationIds, schemas, status codes, and security declarations.
- [ ] Hash every `src/main/resources/db/migration` file and record the list; any final migration diff is a blocker.
- [ ] Classify each `muer-example` file as Showcase, Acceptance, Quick Start overlap, framework test, or obsolete.
- [ ] Commit with `git add docs/validation/repository-architecture-baseline.md && git commit -m "docs: capture repository refactor baseline"`.

### Task 2: Extract the shared OpenAPI contract

**Files:** Move `muer-management-web/src/main/resources/openapi/iam.yaml` to `contracts/openapi/iam.yaml`; modify the HTTP POM, Admin Console generator, docs, and contract scripts.

**Interfaces:** Java generation reads `${maven.multiModuleProjectDirectory}/contracts/openapi/iam.yaml`; frontend generation reads the same contract through a stable repository-relative path.

- [ ] Run `mkdir -p contracts/openapi` and `git mv muer-management-web/src/main/resources/openapi/iam.yaml contracts/openapi/iam.yaml`.
- [ ] Change the OpenAPI Generator input to the Maven multi-module root path; remove fragile `../../../../` paths.
- [ ] Change Admin Console `api:generate` to reference `../../contracts/openapi/iam.yaml`, after its later move to `apps/muer-admin-console`.
- [ ] Verify `find . -name 'iam.yaml' -print` reports only `contracts/openapi/iam.yaml` and no old source-path references remain.
- [ ] Compare the hash to Task 1, run Java and TypeScript generation, then commit `refactor: extract shared OpenAPI contract`.

### Task 3: Organize Java framework modules

**Files:** Move the nine existing publishable modules and `muer-management-web` under `modules/`; rename the latter to `modules/muer-http-api`; modify all POMs, packages, imports, and generator configuration.

**Interfaces:** The renamed module publishes `cloud.muer:muer-http-api`; the Starter dependency chain remains `starter -> autoconfigure -> http-api`.

- [ ] Create `modules/` and use `git mv` for `muer-core`, `muer-authentication`, `muer-authorization`, `muer-session`, `muer-audit`, `muer-diagnostics`, `muer-persistence-mybatis`, `muer-spring-boot-autoconfigure`, `muer-spring-boot-starter`, and `muer-management-web` to the new paths.
- [ ] Rename the HTTP Artifact to `muer-http-api` and migrate HTTP implementation/generated packages from `cloud.muer.web` to `cloud.muer.http`; retain `cloud.muer.autoconfigure.web` where it accurately names auto-configuration.
- [ ] Update root `<modules>` to list only `modules/muer-*` and the future `tests/architecture`; replace all `muer-management-web` dependencies with `muer-http-api`.
- [ ] Compile the framework with `mvn -B -pl modules/muer-spring-boot-autoconfigure -am test -DskipTests`.
- [ ] Commit `refactor: organize Muer framework modules`.

### Task 4: Split Showcase and external Consumer Acceptance

**Files:** Create `examples/showcase/**` and `test-apps/consumer-acceptance/**`; classify and move `muer-example` files; modify Quick Start, public API checks, README files, identity checks, and CI.

**Interfaces:** Both applications consume only `cloud.muer:muer-spring-boot-starter`, do not inherit `muer-parent`, do not enter the Reactor, and do not import implementation packages.

- [ ] Create independent POMs using an ordinary Spring Boot parent or explicit properties; direct Muer dependency is only the public Starter.
- [ ] Move readable document, order, permission, seeder, identity, and showcase integration fixtures to `examples/showcase`, with package `cloud.muer.showcase`.
- [ ] Create minimal Acceptance fixtures for authentication, authorization, scope, Session/Profile behavior, management API, and MySQL/Redis Testcontainers; do not mechanically duplicate Showcase classes.
- [ ] Remove `muer-example` from the Reactor and make `scripts/verify-consumer-public-api.sh` default to `test-apps/consumer-acceptance/src/main/java` while accepting an explicit argument.
- [ ] Run `mvn -B install -DskipTests`, then Quick Start, Showcase, Acceptance, and public API boundary verification as independent Maven projects.
- [ ] Commit `refactor: separate showcase and consumer acceptance`.

### Task 5: Organize frontend and documentation applications

**Files:** Move `muer-admin-web` to `apps/muer-admin-console` and `muer-docs` to `apps/muer-docs-site`; modify package names, generator scripts, Vite/Astro config, docs links, and workflow paths.

**Interfaces:** Neither app is in Maven Reactor; Admin Console reads only `contracts/openapi/iam.yaml`; Docs Site retains `https://muer.cloud`, CNAME, base path, and content slugs.

- [ ] Move both directories with `git mv` and set package names to `muer-admin-console` and `muer-docs-site`.
- [ ] Remove all Admin Console references to Java module source paths and update its API generator to the shared contract.
- [ ] Update Docs Site source links without changing public slugs or Pages settings.
- [ ] In `mamba wust`, run `npm ci`, API generation/type-check/tests/build for Admin Console, then `npm ci`, `npm run check`, and `npm run build` for Docs Site.
- [ ] Commit `refactor: organize Muer web applications`.

### Task 6: Move architecture tests and align governance

**Files:** Move `muer-tests` to `tests/architecture`; modify its POM, root POM, `.gitignore`, README, CONTRIBUTING, PUBLIC_API, release docs, metadata, and scripts; create `scripts/verify-repository-layout.sh`.

**Interfaces:** Architecture tests remain in Reactor with `<maven.deploy.skip>true</maven.deploy.skip>`; layout validation checks only stable boundaries.

- [ ] Move and rename the test Artifact to `muer-architecture-tests`; add deploy skip.
- [ ] Add a layout script checking the single OpenAPI contract, absent legacy directories/Artifacts, Reactor exclusion of apps/examples/test-apps, external POM independence, and Admin generator path.
- [ ] Update ignore rules for all new `target`, `node_modules`, `dist`, and `.astro` locations.
- [ ] Add the repository map and code-placement table to README and CONTRIBUTING; recommend only `cloud.muer:muer-spring-boot-starter`.
- [ ] Run the layout script, architecture tests, and legacy reference audit; commit `refactor: organize architecture tests`.

### Task 7: Align CI workflows

**Files:** `.github/workflows/verify.yml`, `admin-web.yml`, `docs.yml`, `docs-pages.yml`, and their scripts.

**Interfaces:** CI exposes Framework, External Consumers, Admin Console, Documentation, and Pages responsibilities without changing Pages settings.

- [ ] Make Framework run `mvn -B verify` for Reactor modules and architecture tests.
- [ ] Make External Consumers install the framework then run independent Quick Start, Showcase, and Acceptance commands; no `-pl` against examples.
- [ ] Update Admin and Docs working directories, cache paths, shared contract generation, and Pages artifact path to `apps/muer-*`.
- [ ] Make workflow checks fail on legacy paths or accidental Reactor inclusion; commit `ci: align workflows with repository architecture`.

### Task 8: Final compatibility and architecture report

**Files:** Create `docs/validation/muer-repository-architecture-refactor-report.md`.

**Interfaces:** The report records final tree, Reactor, Artifact renames, applications, examples, Acceptance boundary, contract uniqueness, compatibility matrix, tests, CI, cleanliness, and deferred HTTP split review.

- [ ] Run `mvn -B clean verify`, `mvn -B install -DskipTests`, all three independent Maven consumers, public API boundary, layout validation, Admin Console verification, and Docs Site verification.
- [ ] Compare final OpenAPI inventory/hash and migration hashes with Task 1; run login, current principal, Permission allow/deny, Scope allow/deny, READ/WRITE, profile switch, session revoke, authorization version, First Admin, management authorization, diagnostics, and token-safety tests.
- [ ] Verify `find . -name 'iam.yaml' -print`, old Artifact search, `git diff --check`, and clean status.
- [ ] Write `READY FOR POST-REFACTOR ARCHITECTURE REVIEW` only when all required checks pass; otherwise write `NOT READY` with exact blockers.
- [ ] Commit `docs: record repository architecture refactor verification`.
