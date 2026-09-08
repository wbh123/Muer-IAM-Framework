# Muer Cloud Final Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Freeze Muer's public identity as `cloud.muer`, `muer.cloud`, `muer-admin-web`, and `muer-docs` before the first stable release.

**Architecture:** Keep IAM as the domain/API/database vocabulary while moving Java/Maven identity, active documentation, build paths, and user-facing product names to the Muer canonical values. Use metadata-driven guards and explicit historical-document exclusions.

**Tech Stack:** Maven, Java, Spring Boot, PowerShell/Bash identity guards, Vue/TypeScript admin console, Astro/Starlight documentation, GitHub Actions.

**Spec:** `docs/superpowers/specs/muer-cloud-final-cutover.md`

## Global Constraints

- Java/Maven namespace is `cloud.muer`; Spring keys remain `muer.*`.
- Keep `/iam/**`, `iam_*`, `iam.admin.*`, and Redis value `iam` unchanged.
- Do not publish `0.1.0`, tag, or create a release.
- Do not keep an `io.github.muer` compatibility alias or a second package tree.
- Historical `docs/superpowers/**` may retain former literals only through an explicit scanner exclusion.

### Task 1: Identity metadata, Maven, Java packages, and autoconfiguration

- Update `metadata/project-metadata.yaml`, root and child POMs, SCM/website URLs, and active source links.
- Physically move `src/main/java/io/github/muer` and `src/test/java/io/github/muer` trees to `cloud/muer`, then update declarations/imports and generated-import resources.
- Update identity/public-API guards and add tests for website and repository name.

### Task 2: Active docs and migration guidance

- Rewrite active README, release notes, migration guide, and docs-site content to `muer.cloud`, `cloud.muer`, and `muer.*` while preserving IAM domain contracts.
- Mark historical design records as excluded history and verify forbidden literals are absent from active docs.

### Task 3: Admin and documentation project renames

- Rename `iam-admin-web` to `muer-admin-web` and `iam-docs` to `muer-docs`.
- Update package names, titles, workflows, Astro site/base configuration, CNAME handling, and all active paths.

### Task 4: Verification and release gate

- Run legacy scans, source-tree checks, Maven consumer/public API and autoconfiguration tests, admin/docs checks/builds, and bounded remote CI/status checks.
- Report P0/P1 findings and stop at `READY FOR FINAL MUER 0.1.0 RELEASE REVIEW` without publishing.
