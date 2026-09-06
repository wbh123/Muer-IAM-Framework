# Muer Brand and Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver one breaking Muer namespace and brand cutover without altering authentication, authorization, client type, Profile, session, or diagnostics semantics.

**Architecture:** Atomically rename modules and Java source roots to muer-* and io.github.muer. Rename only public framework entry types, bind configuration exclusively at muer.*, retain physical data names, and retain Astro/Starlight with configurable site URLs for the current project site and future organization homepage.

**Tech Stack:** Java 21, Maven, Spring Boot 4, MyBatis, Astro 7, Starlight, Node 22, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-09-06-muer-brand-and-metadata-design.md

## Global Constraints

- Java root package and Maven groupId are exactly io.github.muer.
- Modules change one-to-one; authentication, authorization, physical schema, Redis keys, and example identities remain unchanged.
- The external configuration prefix is muer; iam.* is a documented breaking migration, not a hidden alias.
- SCM and GitHub links remain wbh123/iam until the muer organization exists.
- Every modified Java type and method receives Chinese class/method comments; non-obvious modified control flow receives Chinese line comments.

---

### Task 1: Add namespace migration contract

**Files:**
- Create: scripts/test-muer-identity.sh
- Modify: .github/workflows/verify.yml
- Remove: scripts/test-rename-project.ps1
- Test: scripts/test-muer-identity.sh

**Interfaces:**
- Produces: a contract preventing old namespace/configuration identifiers outside MIGRATION.md, historical design records, and immutable SQL migrations.

- [ ] **Step 1: Write the failing test**

~~~bash
#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)"
if rg -n 'io\.github\.iamstarter' "$root" --glob '!docs/superpowers/**' --glob '!MIGRATION.md' --glob '!**/db/iam/migration/**'; then
  echo 'legacy Java namespace remains' >&2
  exit 1
fi
~~~

- [ ] **Step 2: Verify RED**

Run: bash scripts/test-muer-identity.sh

Expected: FAIL with legacy Java namespace remains.

- [ ] **Step 3: Add final assertions**

~~~bash
rg -q '<groupId>io.github.muer</groupId>' "$root/pom.xml"
rg -q '^io.github.muer.autoconfigure.MuerAutoConfiguration$' "$root/muer-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
! rg -n '^[[:space:]]*iam\.' "$root" --glob '*.yaml' --glob '*.yml' --glob '*.properties'
~~~

- [ ] **Step 4: Run it in CI**

~~~yaml
- name: Verify Muer project identity
  run: bash scripts/test-muer-identity.sh
~~~

- [ ] **Step 5: Commit**

~~~bash
git add scripts/test-muer-identity.sh .github/workflows/verify.yml
git rm scripts/test-rename-project.ps1
git commit -m "test: add Muer namespace migration contract"
~~~

### Task 2: Migrate Maven graph and Java source root

**Files:**
- Modify: pom.xml and every iam-*/pom.xml
- Move: every iam-*/src/{main,test}/java/io/github/iamstarter/** to its io/github/muer/** equivalent
- Modify: moved Java sources/tests, mapper XML, consumer scripts, and workflows
- Test: MuerAutoConfigurationTest and scripts/test-muer-identity.sh

**Interfaces:**
- Produces: one-to-one muer-* modules, imports rooted at io.github.muer, and entry types MuerAutoConfiguration, MuerProperties, MuerSchemaMigrator.

- [ ] **Step 1: Write the failing compilation test**

Rename IamAutoConfigurationTest to MuerAutoConfigurationTest and use:

~~~java
private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MuerAutoConfiguration.class));
~~~

Run: mvn -B -pl muer-spring-boot-autoconfigure -am -Dtest=MuerAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL because the new artifact/class does not exist.

- [ ] **Step 2: Rename artifacts and paths**

Map all existing iam-* modules to equal muer-* names, set root artifactId to muer-parent, and set all internal groupIds to io.github.muer. Move source/test directories and replace package/import/FQN values in Java, MyBatis XML, SPI, scripts, workflows, and docs. Preserve Flyway paths, table names, and Redis key values.

- [ ] **Step 3: Rename public entry types only**

~~~java
/** Muer 的 Spring Boot 自动配置入口。 */
@AutoConfiguration
@EnableConfigurationProperties(MuerProperties.class)
public class MuerAutoConfiguration { }
~~~

Retain AuthorizationDecision, SessionService, IdentityAuthenticator, RequirePermission, and AuthorizationEngine names. Add required Chinese comments to changed Java types and methods.

- [ ] **Step 4: Verify GREEN**

~~~bash
mvn -B -pl muer-spring-boot-autoconfigure -am -Dtest=MuerAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/test-muer-identity.sh
~~~

Expected: PASS.

- [ ] **Step 5: Commit**

~~~bash
git add pom.xml muer-* scripts .github
git commit -m "refactor: migrate Java and Maven namespaces to Muer"
~~~

### Task 3: Migrate Spring configuration and auto-configuration discovery

**Files:**
- Modify: muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerProperties.java
- Modify: muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerAutoConfiguration.java
- Modify: the AutoConfiguration.imports resource
- Modify: example YAML and all tests using iam.*
- Test: MuerAutoConfigurationTest, MuerStarterAutoConfigurationSmokeTest, MuerSecurityIntegrationTest

**Interfaces:**
- Produces: muer.* binding and MuerAutoConfiguration discovery, with no old-prefix alias.

- [ ] **Step 1: Write the failing new-prefix test**

~~~java
contextRunner.withPropertyValues(
        "muer.enabled=true",
        "muer.session.touch-interval=3m",
        "muer.client-types=WEB");
~~~

Run: mvn -B -pl muer-spring-boot-autoconfigure -am -Dtest=MuerAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test

Expected: FAIL because the old prefix remains bound.

- [ ] **Step 2: Implement only the property cutover**

~~~java
/** Muer 对外暴露的 Spring Boot 配置属性。 */
@ConfigurationProperties("muer")
public class MuerProperties { }

@ConditionalOnProperty(prefix = "muer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MuerAutoConfiguration { }
~~~

Keep defaults, validation, SecurityFilterChain, client-type checks, schema-history table, mappers, and token/session behavior unchanged. Update validation text to muer.*.

- [ ] **Step 3: Register the new FQN**

The imports resource must contain exactly:

~~~text
io.github.muer.autoconfigure.MuerAutoConfiguration
~~~

- [ ] **Step 4: Verify GREEN and security invariants**

~~~bash
mvn -B -pl muer-spring-boot-autoconfigure -am -Dtest=MuerAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl muer-example -am -Dtest=MuerStarterAutoConfigurationSmokeTest,MuerSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS including disabled configuration, client restrictions, and filter registration.

- [ ] **Step 5: Commit**

~~~bash
git add muer-spring-boot-autoconfigure muer-example
git commit -m "feat: expose Muer Spring configuration namespace"
~~~

### Task 4: Rebrand Astro/Starlight, README, and user documentation

**Files:**
- Modify: README.md, iam-docs/astro.config.mjs, iam-docs/src/content/docs/index.md
- Modify: relevant iam-docs/src/content/docs/**/*.md
- Create: iam-docs/public/brand/logo.svg, logo-horizontal.svg, logo-mark.svg, favicon.svg
- Test: scripts/test-muer-identity.sh, iam-docs npm run check/build

**Interfaces:**
- Produces: a Muer project homepage with truthful current GitHub links and configurable canonical/base URLs.

- [ ] **Step 1: Write failing brand assertions**

~~~bash
rg -q "title: 'Muer'" "$root/iam-docs/astro.config.mjs"
rg -q 'Identity infrastructure that grows naturally with your applications\.' "$root/README.md"
test -f "$root/iam-docs/public/brand/favicon.svg"
~~~

Run: bash scripts/test-muer-identity.sh

Expected: FAIL because IAM starter branding remains.

- [ ] **Step 2: Implement configurable metadata**

~~~js
const site = process.env.SITE_URL ?? 'https://wbh123.github.io/iam/';
const base = process.env.BASE_PATH ?? '/iam';
export default defineConfig({ site, base, integrations: [starlight({ title: 'Muer' })] });
~~~

Keep current wbh123/iam edit/GitHub URLs. Document future organization values SITE_URL=https://muer.github.io and BASE_PATH=/, without creating fictional source links.

- [ ] **Step 3: Implement content and assets**

Use 木耳 Muer, approved taglines, Quick Start/Documentation/GitHub actions, and Identity/Authentication/Authorization/Session/Audit/Diagnostics/Spring Boot capability content. Use restrained brand colors in SVG/CSS. Preserve substantive pages and relabel navigation to Getting Started, Guide, Concepts, Reference, Examples, Releases; do not add empty routes.

- [ ] **Step 4: Update user snippets**

Change Maven/import/configuration examples to Muer values. Retain operator-a/operator-b and state examples are not production defaults.

- [ ] **Step 5: Verify GREEN**

~~~bash
bash scripts/test-muer-identity.sh
(cd iam-docs && npm run check && npm run build)
~~~

Expected: PASS with a Muer homepage and sitemap.

- [ ] **Step 6: Commit**

~~~bash
git add README.md iam-docs scripts/test-muer-identity.sh
git commit -m "docs: establish Muer project identity"
~~~

### Task 5: Prepare CI, publication metadata, and migration guidance

**Files:**
- Modify: .github/workflows/docs.yml, docs-pages.yml, admin-web.yml, verify.yml
- Modify: root and module POM metadata
- Create: MIGRATION.md
- Modify: release notes and changelog
- Test: scripts/test-docs-workflow.sh, Maven package

**Interfaces:**
- Produces: PR build-only validation, main Pages deploy, truthful POM metadata, and explicit breaking-change guidance.

- [ ] **Step 1: Write failing Pages workflow assertions**

~~~bash
require_content 'Pages deploy action' "$pages_workflow" 'actions/deploy-pages@v'
require_content 'Pages artifact action' "$pages_workflow" 'actions/upload-pages-artifact@v'
require_content 'Pages main trigger' "$pages_workflow" 'branches: \[main\]'
~~~

Run: bash scripts/test-docs-workflow.sh

Expected: FAIL because Pages is manual-only.

- [ ] **Step 2: Implement build/deploy separation**

PR: install, check, build only. Main Pages workflow: pages: write and id-token: write; build with SITE_URL=https://wbh123.github.io/iam/ and BASE_PATH=/iam; upload with actions/upload-pages-artifact@v3; deploy with actions/deploy-pages@v4; retain manual dispatch.

- [ ] **Step 3: Update metadata and migration guide**

Use Muer IAM Framework, the supplied short description, and https://muer.github.io as planned project URL; preserve current SCM URLs. MIGRATION.md must contain:

~~~text
io.github.iamstarter -> io.github.muer
io.github.iamstarter:iam-spring-boot-starter -> io.github.muer:muer-spring-boot-starter
iam.enabled -> muer.enabled
iam.client-types -> muer.client-types
iam.session.* -> muer.session.*
iam.audit.* -> muer.audit.*
iam.diagnostics.* -> muer.diagnostics.*
~~~

State physical schema/table names and Redis keys are unchanged, and organization-site migration remains operational work.

- [ ] **Step 4: Verify GREEN**

~~~bash
bash scripts/test-docs-workflow.sh
bash scripts/test-muer-identity.sh
mvn -B -pl muer-spring-boot-starter -am package -DskipTests
~~~

Expected: PASS.

- [ ] **Step 5: Commit**

~~~bash
git add .github/workflows scripts/test-docs-workflow.sh pom.xml muer-*/pom.xml MIGRATION.md iam-docs
git commit -m "ci: prepare Muer Pages publication and migration guide"
~~~

### Task 6: Run full regression and record release readiness

**Files:**
- Create: docs/release-validation/muer-brand-cutover.md
- Test: backend, integration, documentation, admin web, static contracts

**Interfaces:**
- Produces: reproducible evidence of retained safety semantics and records environment blockers.

- [ ] **Step 1: Run full backend and integration checks**

~~~bash
mvn -B test
mvn -B -pl muer-example -am -Pintegration -Dtest=MuerStarterConsumptionTest,MuerSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS. If Docker/Testcontainers is unavailable, retain exact blocker output.

- [ ] **Step 2: Run documentation, frontend, and structural checks**

~~~bash
(cd iam-docs && npm run check && npm run build)
(cd iam-admin-web && npm test && npm run type-check && npm run build)
bash scripts/test-muer-identity.sh
bash scripts/test-docs-workflow.sh
bash scripts/verify-consumer-public-api.sh
~~~

Expected: PASS.

- [ ] **Step 3: Record evidence**

Record commit, command outputs, package/config mappings, unchanged physical-data contract, and final operational step: create/migrate muer/muer.github.io, then use SITE_URL=https://muer.github.io and BASE_PATH=/.

- [ ] **Step 4: Commit**

~~~bash
git add docs/release-validation/muer-brand-cutover.md
git commit -m "docs: record Muer brand cutover validation"
~~~

