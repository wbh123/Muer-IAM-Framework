# Non-Maven Release Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining repository and ordinary CI issues without changing Maven Central publication, runtime IAM behavior, public contracts, or release credentials.

**Architecture:** Keep the change isolated to GitHub Actions workflow maintenance and CI hygiene. Upgrade non-Maven workflows to current official action majors, make the Admin Console validation run on release branches, remove a duplicate integration execution, and add a guard script plus GitHub Actions-only Dependabot configuration so the same drift is caught automatically.

**Tech Stack:** GitHub Actions, Bash, Dependabot YAML.

**Spec:** User request on 2026-09-11: resolve current project issues except Maven publication-related work.

## Global Constraints

- Do not modify `pom.xml`, Maven Central profiles, GPG configuration, Central namespace/token setup, or `.github/workflows/release-maven.yml`.
- Do not change IAM runtime behavior, OpenAPI, Flyway migrations, HTTP routes, database schema, or frontend application behavior.
- Work only on `codex/non-maven-closeout` until verification is complete.
- Preserve Java 21 and Node 22 application toolchain versions.

---

### Task 1: Add CI workflow hygiene guard

**Files:**
- Create: `scripts/test-ci-workflows.sh`
- Modify: `.github/workflows/verify.yml`

**Interfaces:**
- Consumes: repository workflow YAML files.
- Produces: a deterministic shell check that fails when ordinary workflows regress to deprecated action majors, omit release-branch Admin Console validation, or reintroduce duplicate Showcase integration execution.

- [ ] **Step 1:** Create `scripts/test-ci-workflows.sh` with explicit checks for the four non-Maven workflows.
- [ ] **Step 2:** Run it conceptually against the current baseline and confirm it would fail on old action majors / missing Admin release trigger / duplicate Showcase command.
- [ ] **Step 3:** Add the guard to the ordinary Verify workflow.

### Task 2: Modernize ordinary GitHub Actions

**Files:**
- Modify: `.github/workflows/verify.yml`
- Modify: `.github/workflows/admin-web.yml`
- Modify: `.github/workflows/docs.yml`
- Modify: `.github/workflows/docs-pages.yml`

**Interfaces:**
- Consumes: current workflow behavior and Java 21 / Node 22 toolchain settings.
- Produces: the same jobs on current official GitHub Action major versions.

- [ ] **Step 1:** Upgrade `actions/checkout` to v7 in ordinary workflows.
- [ ] **Step 2:** Upgrade `actions/setup-node` to v7 where used.
- [ ] **Step 3:** Upgrade `actions/setup-java` to v6 where used.
- [ ] **Step 4:** Upgrade Pages actions to `configure-pages@v6`, `upload-pages-artifact@v5`, and `deploy-pages@v5`.
- [ ] **Step 5:** Keep Node 22 and Java 21 unchanged.

### Task 3: Close ordinary CI coverage and duplication gaps

**Files:**
- Modify: `.github/workflows/admin-web.yml`
- Modify: `.github/workflows/verify.yml`

**Interfaces:**
- Produces: Admin Console verification on `release/**` and one, not two, identical Showcase integration executions.

- [ ] **Step 1:** Add `release/**` to Admin Console push branches.
- [ ] **Step 2:** Remove the second identical `mvn -B -f examples/showcase/pom.xml -Pintegration test` execution.
- [ ] **Step 3:** Preserve the remaining integration run, which already covers the full Showcase integration suite.

### Task 4: Prevent future GitHub Actions drift

**Files:**
- Create: `.github/dependabot.yml`

**Interfaces:**
- Produces: weekly GitHub Actions dependency update PRs only; Maven dependency automation remains out of scope.

- [ ] **Step 1:** Configure Dependabot with `package-ecosystem: github-actions`, root directory `/`, weekly schedule, and a small PR limit.
- [ ] **Step 2:** Do not add a Maven ecosystem entry.

### Task 5: Verification

**Files:** none.

- [ ] **Step 1:** Run/inspect `scripts/test-ci-workflows.sh` against the completed branch.
- [ ] **Step 2:** Open a pull request to `main` so GitHub executes ordinary PR workflows.
- [ ] **Step 3:** Verify Starter, Admin Console, and Documentation PR checks complete successfully; Maven release workflow is intentionally excluded.
