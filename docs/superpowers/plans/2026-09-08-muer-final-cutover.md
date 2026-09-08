# Muer Final Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the existing Muer starter, consumer, admin-console, bootstrap, documentation and CI regressions without expanding IAM scope.

**Architecture:** Keep core-only starter contexts free of persistence-bound management commands. Enable lifecycle commands only when MyBatis persistence is available; expose all browser actions through the generated OpenAPI client. Bootstrap remains an explicit Java service rather than a public HTTP endpoint.

**Tech Stack:** Java 21, Spring Boot, MyBatis, OpenAPI Generator, Vue 3/TypeScript, Vitest, Astro.

**Spec:** `/mnt/c/Users/wangb/.codex/attachments/9002580d-9ace-4d83-a6a5-f454b3c3da82/pasted-text.txt`

## Global Constraints

- Use branch `codex/muer-cloud-final-cutover` and PR #5 only.
- Do not modify `release/0.1.0` before PR merge and green main CI.
- Do not add new public bootstrap endpoints or direct browser HTTP clients.
- Preserve `Muer`, `cloud.muer`, `/iam/**`, `muer.*`, `iam_*`, and `iam.admin.*` contracts.
- Do not edit generated TypeScript client files.

---

### Task 1: Starter auto-configuration regression

**Files:** `muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/MuerAutoConfiguration.java`, `muer-spring-boot-autoconfigure/src/test/java/cloud/muer/autoconfigure/MuerAutoConfigurationTest.java`

- [ ] Write core-only context tests proving command/lifecycle beans are absent without `SqlSessionFactory` and custom command ports suppress defaults.
- [ ] Add `@ConditionalOnBean(SqlSessionFactory.class)` to MyBatis command ports and require both command/version ports for lifecycle service.
- [ ] Inject lifecycle services through `ObjectProvider` or condition controller beans, not null sentinels.
- [ ] Run `mvn -B -pl muer-spring-boot-autoconfigure -am test` and commit.

### Task 2: Quick Start consumer and seeder safety

**Files:** `examples/quickstart/**`, associated tests and docs.

- [ ] Reproduce the standalone Maven consumer failure using the exact two commands in the spec.
- [ ] Keep `muer-spring-boot-starter` as the sole Muer dependency and make ordinary smoke tests Docker/MySQL/Redis free.
- [ ] Add persistence safety tests for unrelated records and conflicting demo IDs.
- [ ] Run consumer tests and commit.

### Task 3: Admin API client and lifecycle UI

**Files:** `muer-admin-web/src/**`, OpenAPI source, Vitest tests.

- [ ] Generate the client, write failing UI tests for template creation, draft creation, publish, profile creation/version switch and published read-only state.
- [ ] Fix business-source types/wrappers only; route all calls through generated client methods.
- [ ] Run API generation, type check, tests and production build; commit.

### Task 4: Explicit first-admin bootstrap

**Files:** public bootstrap service/API, MyBatis ports, unit tests, `muer-docs/src/content/docs/management/bootstrap-first-admin.md`.

- [ ] Write failing tests for existing-host-user input, registry-derived `iam.admin.*` permissions, idempotency, and no automatic execution.
- [ ] Implement explicit Java bootstrap service with no password/account creation and no HTTP endpoint.
- [ ] Document host-controlled invocation and run module tests; commit.

### Task 5: Documentation consolidation and blockers

**Files:** `muer-docs/**`, `docs/QUICK_START.md`, `docs/IAM_INTEGRATION_GUIDE.md`, deployment doc, `docs/validation/muer-0.1.0-final-blockers.md`.

- [ ] Move full hand-written tutorial material to From Zero; leave Quick Start a 200-350-line success path including Bash and PowerShell.
- [ ] Replace duplicate docs with compatibility entrypoints, brand OpenAPI as Muer, remove active `IAM_EXAMPLE_` references, and update admin/permission tutorials.
- [ ] Mark only evidence-backed blockers resolved; retain release synchronization as pending until main merge.
- [ ] Run docs contract checks, Astro check/build, and commit.

### Task 6: Final verification and CI

- [ ] Run CI-equivalent Java, Quick Start, admin-console and docs checks specified in the task.
- [ ] Push one final branch SHA, verify it remotely, then wait for all three CI workflows on that same SHA.
