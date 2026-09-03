# Independent Consumer Release Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove `iam-example` is a standalone, generic third-party consumer of `iam-spring-boot-starter`.

**Architecture:** Replace the order-specific host surface with generic document/project/department host code while retaining IAM only through public starter APIs. Keep fast context/unit tests container-free and execute real MySQL/Redis acceptance tests only through an explicit Maven integration profile and dedicated CI job.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, Maven, MySQL 8.4, Redis 7, Testcontainers, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-03-independent-consumer-release-validation-design.md`

## Global Constraints

- Work only on `codex/release-readiness`; never modify or merge `main`.
- `iam-example` declares only `iam-spring-boot-starter` as an IAM production dependency.
- Consumer code uses public IAM API/SPI only; no `internal`, `impl`, or persistence implementation import.
- Use generic Document, Project, Department, and AppUser terminology only.
- Integration tests use real MySQL and Redis Testcontainers and fail when Docker is unavailable.
- Write each behavior test before production or fixture code and observe its failure.
- Never persist, log, document, or assert a literal opaque token.

---

### Task 1: Define the standalone Consumer boundary

**Files:**
- Create: `iam-example/src/main/java/io/github/iamstarter/example/AppUser.java`
- Create: `iam-example/src/main/java/io/github/iamstarter/example/Document.java`
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleIdentityAdapter.java`
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleResourceHierarchyAdapter.java`
- Test: `iam-example/src/test/java/io/github/iamstarter/example/ConsumerPublicApiTest.java`

**Interfaces:** Consumes public `IdentityAuthenticator`, `IamPrincipal`, `ResourceHierarchyProvider`, `ResourceDescriptor`, and `ResourceScope`. Produces an `AppUser` projection and document parent path `PROJECT:<id>`, `DEPARTMENT:<id>`.

- [ ] **Step 1: Write failing public-boundary test**

```java
assertTrue(new AppUser(101L, "author-a", "demo-pass", true, "Author A").enabled());
assertTrue(new ExampleResourceHierarchyAdapter().isWithinScope(
        new ResourceDescriptor("DOCUMENT", "1001", List.of("PROJECT:101", "DEPARTMENT:10"), Map.of()),
        new ResourceScope("PROJECT", "101", ScopeAccess.READ)));
```

- [ ] **Step 2: Run red test**

Run: `mvn -pl iam-example -am -Dtest=ConsumerPublicApiTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because `AppUser` and document hierarchy do not exist.

- [ ] **Step 3: Implement minimal host projection and hierarchy**

```java
public record AppUser(long id, String username, String password, boolean enabled, String displayName) {}
```

Map enabled `AppUser` values to the existing public `IamPrincipal`; return `Optional.empty()` for wrong password, unknown, or disabled user.

- [ ] **Step 4: Run green test and commit**

Run: `mvn -pl iam-example -am -Dtest=ConsumerPublicApiTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `feat: model independent IAM consumer host boundary`

### Task 2: Add host security and document HTTP surface

**Files:**
- Create: `iam-example/src/main/java/io/github/iamstarter/example/DocumentController.java`
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleSecurityConfiguration.java`
- Test: `iam-example/src/test/java/io/github/iamstarter/example/DocumentControllerTest.java`

**Interfaces:** Produces `GET /public/health`, `GET /api/documents/{id}`, and `POST /api/documents/{id}`. Document routes call `AuthorizationEngine.decide` with `document:read`/`document:update`, `DOCUMENT` resource, and READ/WRITE access.

- [ ] **Step 1: Write failing HTTP/controller tests**

```java
assertEquals(200, controller.health().getStatusCode().value());
assertEquals(401, controller.read("1001").getStatusCode().value());
```

- [ ] **Step 2: Run red test**

Run: `mvn -pl iam-example -am -Dtest=DocumentControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation failure because the controller does not exist.

- [ ] **Step 3: Implement public and protected host routes**

Use the starter bearer filter for `/api/**`; permit `/public/**`; do not write role string comparisons in controller code.

- [ ] **Step 4: Run green test and commit**

Run: `mvn -pl iam-example -am -Dtest=DocumentControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `feat: add independent consumer document security`

### Task 3: Seed generic RBAC, scope, profile, and multi-session data

**Files:**
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamShowcaseFixture.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`
- Test: `iam-example/src/test/java/io/github/iamstarter/example/IamConsumerIntegrationTest.java`

**Interfaces:** Produces `seedIndependentConsumer(JdbcTemplate)`, profile IDs for admin and reader, document permissions, project/department scopes, and deterministic session assertions.

- [ ] **Step 1: Write failing container workflow test**

```java
assertEquals(200, request(adminToken, "GET", "/api/documents/1001").statusCode());
assertEquals(403, request(readerToken, "POST", "/api/documents/1001").statusCode());
```

- [ ] **Step 2: Run red integration test**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failing route/fixture assertion before generic permissions are seeded.

- [ ] **Step 3: Implement fixture-only IAM data**

Seed separate published templates for `document:read`, `document:create`, `document:update`, and `document:delete`; give reader only read permission and project 101 READ scope. Keep database fixture data generic.

- [ ] **Step 4: Run green test and commit**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `test: validate independent consumer RBAC and scope`

### Task 4: Validate configuration, tokens, profiles, and sessions

**Files:**
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterAutoConfigurationSmokeTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamConsumerIntegrationTest.java`
- Modify: `iam-example/src/main/resources/application.yaml`

**Interfaces:** Uses public `/iam/auth/login`, `/iam/sessions`, `/iam/sessions/{sessionId}/revoke`, and `/iam/authorization/profiles/{profileId}/switch` endpoints.

- [ ] **Step 1: Write failing lifecycle/configuration tests**

```java
assertThrows(IllegalStateException.class, () -> start("iam.token.ttl=0s"));
assertEquals(401, request("Bearer invalid", "GET", "/api/documents/1001").statusCode());
```

- [ ] **Step 2: Run red test**

Run: `mvn -pl iam-example -am -Dtest=IamStarterAutoConfigurationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: failure until consumer-specific configuration assertions are added.

- [ ] **Step 3: Add profile and multi-session lifecycle assertions**

Prove an admin profile can update, a switched reader profile cannot, revoking one session invalidates only its token, and another session remains valid. Assert MySQL session rows and Redis-backed resolution through public HTTP behavior, never by mocking Redis.

- [ ] **Step 4: Run green tests and commit**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `test: validate independent consumer token and session lifecycle`

### Task 5: Enforce public API usage and publish consumer documentation

**Files:**
- Create: `scripts/verify-consumer-public-api.sh`
- Modify: `iam-example/README.md`
- Modify: `README.md`
- Test: `scripts/test-consumer-public-api.sh`

**Interfaces:** The scanner exits nonzero if `iam-example/src/main` imports `io.github.iamstarter.*.internal`, `.impl`, or `.persistence` implementation packages.

- [ ] **Step 1: Write failing scanner test**

```bash
mkdir -p "$sandbox/src/main/java/example"
printf '%s\n' 'import io.github.iamstarter.session.internal.Secret;' > "$sandbox/src/main/java/example/Illegal.java"
! bash scripts/verify-consumer-public-api.sh "$sandbox/src/main/java"
```

- [ ] **Step 2: Run red test**

Run: `bash scripts/test-consumer-public-api.sh`

Expected: failure because the scanner does not exist.

- [ ] **Step 3: Implement scanner and 5-minute guide**

Document only verified Maven coordinates, minimal YAML, identity/resource adapters, startup command, and token-safe curl placeholders. The root README links to the detailed consumer guide.

- [ ] **Step 4: Run green test and commit**

Run: `bash scripts/test-consumer-public-api.sh && bash scripts/test-showcase-readme.sh`

Commit: `docs: add independent consumer integration guide`

### Task 6: Add dedicated Consumer CI and release report

**Files:**
- Modify: `.github/workflows/verify.yml`
- Create: `docs/validation/iam-independent-consumer-release-report.md`
- Test: `iam-example/src/test/java/io/github/iamstarter/example/IamConsumerIntegrationTest.java`

**Interfaces:** Produces a `consumer-integration` GitHub Actions job that runs Docker preflight, public API scan, and `IamConsumerIntegrationTest` under `-Pintegration`.

- [ ] **Step 1: Write failing workflow/documentation assertions**

```bash
rg -q 'consumer-integration' .github/workflows/verify.yml
rg -q 'verify-consumer-public-api.sh' .github/workflows/verify.yml
```

- [ ] **Step 2: Run red check**

Run: `bash -c "rg -q 'consumer-integration' .github/workflows/verify.yml"`

Expected: exit 1 before the job is added.

- [ ] **Step 3: Add CI job and acceptance report template**

The report records branch/SHA, public APIs, independence statements, test counts, GitHub run ID/SHA/jobs/conclusion, commits, findings by P0-P3, and release recommendation. CI must fail when Docker is absent instead of silently skipping.

- [ ] **Step 4: Run verification and commit**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Commit: `ci: validate independent IAM consumer release readiness`

## Final verification

- [ ] Run `mvn clean test` from repository root.
- [ ] Run `bash scripts/verify-consumer-public-api.sh iam-example/src/main/java`.
- [ ] Run `bash scripts/test-consumer-public-api.sh` and `bash scripts/test-showcase-readme.sh`.
- [ ] Run `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest,IamStarterConsumptionTest,IamSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] Run `git diff --check` and prohibited-identifier scan.
- [ ] Push only `codex/release-readiness`, read GitHub Actions Run ID/SHA/job conclusions, and populate the release report with actual evidence.
