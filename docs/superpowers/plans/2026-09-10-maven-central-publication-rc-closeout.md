# Muer 0.1.0 Maven Central Publication & RC Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Prepare the current `0.1.0-SNAPSHOT` Release Candidate for Maven Central publication without changing runtime behavior, then replace stale RC evidence with current, reproducible validation evidence.

**Architecture:** Keep the existing multi-module Maven reactor and publish only the parent POM plus the ten `cloud.muer` framework modules. Put sources, Javadoc, GPG signing, and Central Portal publishing behind an explicit `central-release` profile; ordinary development builds remain credential-free. Add a manual, default-dry-run GitHub Actions workflow that can validate the release bundle but cannot publish on ordinary pushes.

**Tech Stack:** Maven, Java 21, Spring Boot 4.0.0, `maven-source-plugin`, `maven-javadoc-plugin`, `maven-gpg-plugin`, Sonatype `central-publishing-maven-plugin`, GitHub Actions, Markdown.

**Spec:** `/mnt/c/Users/wangb/.codex/attachments/e7c6117a-3fad-456d-881e-fdb4d012fe72/pasted-text.txt`

## Global Constraints

- Keep Maven version at `0.1.0-SNAPSHOT`; do not create a tag, GitHub Release, or upload to Maven Central.
- Do not modify authentication, authorization, database migrations, OpenAPI behavior, frontend source, or branch protection.
- Never commit Central credentials, GPG private keys, passphrases, `settings.xml`, or `.env` files.
- Publishable set is `muer-parent` plus `modules/*`; `tests/architecture`, `examples/*`, `apps/*`, and `test-apps/*` must remain non-published.
- Ordinary `mvn verify` must not require credentials or a GPG key.
- `central-release` validation must generate main/source/Javadoc artifacts; the paired `central-publish` profile stages a Central bundle without uploading.
- Keep `main` and `release/0.1.0` synchronized after the preparation commit; use a fast-forward-only update for `main`.

---

### Task 1: Establish the current RC evidence model

**Files:**
- Modify: `docs/validation/muer-0.1.0-release-report.md`
- Modify: `docs/validation/muer-0.1.0-final-blockers.md`

**Interfaces:**
- Consumes: current `origin/main`, `origin/release/0.1.0`, local validation output, and current GitHub Actions run IDs.
- Produces: a current RC report with no stale branch/SHA/CI claims and a blocker document that records completed branch synchronization as completed.

- [ ] Record the exact baseline SHA and environment versions from commands, not historical notes.
- [ ] Rewrite the report sections to Candidate Baseline, Product Scope, Architecture, Framework/Consumer/Frontend/Docs/Contract/DB/Security validation, Maven readiness, blockers, and recommendation.
- [ ] Record current workflow results for Starter, Admin Console, Documentation, and Publish Documentation, including Starter sub-jobs.
- [ ] Replace the obsolete “PR #5 pending” blocker note with the synchronized `release/0.1.0` status.
- [ ] Keep `0.1.0-SNAPSHOT` and explicitly state that no tag/release/upload exists.

### Task 2: Add a credential-free Maven Central publication profile

**Files:**
- Modify: `pom.xml`
- Modify: `tests/architecture/pom.xml` if deploy-skip inheritance needs an explicit assertion.

**Interfaces:**
- Consumes: existing inherited Maven metadata and module list.
- Produces: `central-release` profile with attached `*-sources.jar`, `*-javadoc.jar`, optional GPG signing, plus a separate `central-publish` profile with pinned Central Portal plugin configuration.

- [ ] Add fixed plugin properties for source, Javadoc, GPG, and Central Portal plugin versions.
- [ ] Configure `maven-source-plugin` with `jar-no-fork` and ensure the POM parent does not create a meaningless source artifact.
- [ ] Configure `maven-javadoc-plugin` with `jar` and exclude only the generated OpenAPI packages in `muer-http-api`, documenting the narrow compatibility reason.
- [ ] Configure `maven-gpg-plugin` only in `central-release`, with `gpg.skip` defaulting to `false` for real release and allowing `-Dgpg.skip=true` for local bundle validation.
- [ ] Configure `org.sonatype.central:central-publishing-maven-plugin` as an extension with `publishingServerId=central`, `autoPublish=false`, and a profile property that enables `skipPublishing` for dry-run validation.
- [ ] Do not add legacy OSSRH/Nexus URLs or credentials.
- [ ] Preserve `maven.deploy.skip=true` for architecture tests and ensure examples/apps/test-apps are outside the reactor.

### Task 3: Add the release artifact verification entry point

**Files:**
- Create: `scripts/verify-maven-release-artifacts.sh`
- Modify: `.github/workflows/release-maven.yml`
- Create: `docs/release/maven-central-publishing.md`

**Interfaces:**
- Consumes: `mvn -Pcentral-release` output and Maven `target` directories.
- Produces: deterministic checks for artifact/source/Javadoc pairs, absent test/demo content, effective-POM metadata, and dry-run Central staging.

- [ ] Make the script fail if any publishable JAR lacks matching sources/Javadoc, if test/demo modules are staged, or if required POM metadata is missing.
- [ ] Check for secrets/private keys in the repository and fail on accidental tracked credential files.
- [ ] Document the publishable artifact classification and owner-only setup for `cloud.muer` namespace, Central user token, and GPG key.
- [ ] Add a manual-only workflow with `workflow_dispatch` and a `dry_run` input defaulting to `true`.
- [ ] Make the workflow run ordinary tests and release-artifact validation; only permit deploy when the workflow is manually invoked with `dry_run=false` and required secrets are present. Never trigger deployment from `push`.
- [ ] Pass credentials exclusively through GitHub Secrets and never print their values.

### Task 4: Validate release artifacts and consumer safety

**Files:**
- Modify: `docs/validation/muer-0.1.0-release-report.md` with fresh results.

**Interfaces:**
- Consumes: profile build, artifact verification script, existing Reactor/consumer/frontend/docs checks.
- Produces: evidence for Main/Sources/Javadoc artifacts, dependency boundaries, and unchanged runtime contracts.

- [ ] Run `mvn -B clean verify`.
- [ ] Run `mvn -B -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true clean verify` (or the exact documented dry-run property supported by the pinned plugin).
- [ ] Inspect publishable JAR contents and run starter dependency-tree checks for testcontainers/H2/examples leakage.
- [ ] Run root layout, identity, docs, showcase README, consumer public API, Admin Console, and Docs Site checks.
- [ ] Verify OpenAPI count, migration continuity/hashes, and no runtime source/contract diffs.
- [ ] Record any network/toolchain-only clean-room limitation separately from software results.

### Task 5: Commit and synchronize the preparation baseline

**Files:**
- Only files from Tasks 1–4.

**Interfaces:**
- Consumes: verified release-prep branch.
- Produces: one focused preparation commit, pushed branch, and matching `main`/`release/0.1.0` heads.

- [ ] Run `git diff --check`, inspect the changed-file list, and confirm no runtime/OpenAPI/DB/frontend files changed.
- [ ] Commit with a release-preparation message.
- [ ] Push `release-prep/maven-central-0.1.0`.
- [ ] Fast-forward `main` only if its remote SHA is still the expected base, then fast-forward `release/0.1.0` to the resulting `main` SHA.
- [ ] Re-fetch and verify both branches are equal, clean, and still `0.1.0-SNAPSHOT`; do not tag or publish.
