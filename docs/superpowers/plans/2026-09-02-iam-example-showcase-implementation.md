# IAM Example Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `iam-example` a runnable, generic HTTP showcase for every
runtime IAM capability a Spring Boot consumer needs to integrate.

**Architecture:** Extend the existing consumer application, retaining one
starter production dependency. Keep host adapters and business routes inside
`iam-example`; use a reusable test fixture to seed generic MySQL state and
Redis-backed opaque-token behavior only in an explicit integration profile.

**Tech Stack:** Java 21, Spring Boot 4, Spring Security, MyBatis, MySQL 8.4,
Redis 7, Testcontainers, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-09-02-iam-example-showcase-design.md`

## Global Constraints

- Do not modify the original dormitory repository or its `main` branch.
- Keep `iam-example` generic; do not add legacy identifiers, real credentials,
  database dumps, or application-specific migrations.
- `iam-spring-boot-starter` remains the example's sole IAM production
  dependency.
- Do not store raw opaque tokens in MySQL, logs, fixtures, or documentation.
- Keep fast tests container-free. Tag or profile Testcontainers tests so Docker
  is a deliberate integration prerequisite.
- Every production behavior starts with a failing automated test and ends with
  its focused test passing.

---

### Task 1: Establish the showcase fixture boundary

**Files:**
- Create: `iam-example/src/test/java/io/github/iamstarter/example/IamShowcaseFixture.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamSecurityIntegrationTest.java`
- Modify: `iam-example/pom.xml`

**Interfaces:**
- Produces `IamShowcaseFixture.seedOperatorA(JdbcTemplate)` and
  `IamShowcaseFixture.seedOperatorB(JdbcTemplate)`.
- Produces a Maven profile named `integration` that selects container-backed
  tests while normal focused tests remain Docker-free.

- [ ] **Step 1: Write failing fixture-consumer tests**

```java
@Test
void seeded_reader_profile_can_read_only_its_department() {
    fixture.seedOperatorA(jdbc);
    assertEquals(200, requestAs("operator-a", "WEB", "/example/orders/9001").statusCode());
    assertEquals(403, requestAs("operator-a", "WEB", "/example/orders/9002").statusCode());
}
```

- [ ] **Step 2: Run the focused integration test and verify it fails**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest test`

Expected: FAIL because `IamShowcaseFixture` does not exist.

- [ ] **Step 3: Implement a minimal reusable fixture**

Create a test-only class that clears IAM tables in dependency order and inserts
users, identities, permission templates, published versions, permissions,
profiles and READ/WRITE department scopes. Use generic names such as
`operator-a`, `reader-501`, and `approver-501`.

- [ ] **Step 4: Make Testcontainers opt-in**

Configure Surefire so `*IntegrationTest` and `*ConsumptionTest` run under the
`integration` profile. Preserve `IamStarterAutoConfigurationSmokeTest` in the
default fast path.

- [ ] **Step 5: Run fixture tests and commit**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest,IamSecurityIntegrationTest test`

Expected: PASS with MySQL and Redis containers available.

Commit: `test: isolate IAM showcase integration fixtures`

### Task 2: Demonstrate configured-client and credential boundaries

**Files:**
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleIdentityAdapter.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterAutoConfigurationSmokeTest.java`

**Interfaces:**
- Consumes `iam.client-types` and `IdentityAuthenticator`.
- Produces HTTP `401` without token/session creation for unsupported clients or
  invalid credentials.

- [ ] **Step 1: Write failing HTTP tests**

```java
assertEquals(401, login("operator-a", "demo-pass", "MOBILE").statusCode());
assertEquals(401, login("operator-a", "wrong", "WEB").statusCode());
```

- [ ] **Step 2: Verify the unsupported-client case fails before adapter work**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest test`

Expected: FAIL until the fixture and adapter credential mapping use the
showcase identities.

- [ ] **Step 3: Implement only the host credential mapping**

Map `operator-a` and `operator-b` credentials to principals. Do not duplicate
the configured-client allow-list in the adapter; the starter owns that rule.

- [ ] **Step 4: Re-run and commit**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest test`

Expected: PASS; no unsupported-client token or session exists.

Commit: `feat: demonstrate IAM login client boundaries`

### Task 3: Demonstrate permission and resource-scope decisions

**Files:**
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleOrderController.java`
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/ExampleResourceHierarchyAdapter.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/ExampleOrderControllerTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`

**Interfaces:**
- Consumes `AuthorizationEngine.decide(IamPrincipal, AuthorizationRequest)`.
- Produces `200`, `401`, `403`, and `404` responses for order reads and
approvals.

- [ ] **Step 1: Write failing controller and HTTP tests**

```java
assertEquals(403, request(tokenForReader501, "POST", "/example/orders/9001/approve").statusCode());
assertEquals(200, request(tokenForApprover501, "POST", "/example/orders/9001/approve").statusCode());
```

- [ ] **Step 2: Run each focused test and verify failure**

Run: `mvn -pl iam-example -am -Dtest=ExampleOrderControllerTest test`

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest test`

Expected: FAIL while the fixture lacks the approver profile and WRITE scope.

- [ ] **Step 3: Add only generic profiles, permissions, and scopes to fixture data**

Create `reader-501`, `approver-501`, and `reader-502` profile data. Keep the
business controller's resource construction generic: `ORDER` with a
`DEPARTMENT` parent path.

- [ ] **Step 4: Re-run tests and commit**

Expected: reader denial and approver success are both proven over HTTP.

Commit: `feat: demonstrate IAM permission and scope decisions`

### Task 4: Demonstrate profile switching and session lifecycle

**Files:**
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamSecurityIntegrationTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamShowcaseFixture.java`

**Interfaces:**
- Consumes `POST /iam/authorization/profiles/{profileId}/switch`.
- Consumes `GET /iam/sessions` and `POST /iam/sessions/{sessionId}/revoke`.
- Produces a replacement token after profile switching and `401` after
  revocation.

- [ ] **Step 1: Write failing workflow test**

```java
var switched = switchProfile(readerToken, 402L);
assertEquals(403, request(readerToken, "POST", "/example/orders/9001/approve").statusCode());
assertEquals(200, request(switched.token(), "POST", "/example/orders/9001/approve").statusCode());
assertEquals(401, revokeAndReuse(switched).statusCode());
```

- [ ] **Step 2: Run and verify failure**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamSecurityIntegrationTest test`

Expected: FAIL until fixture profile identifiers and active-profile state match
the switch endpoint contract.

- [ ] **Step 3: Align fixture identifiers and token assertions**

Seed a stable profile ID for the approver profile. Assert that the original
reader token remains unable to approve, the switched token works, and revoking
that session rejects its token.

- [ ] **Step 4: Re-run and commit**

Expected: PASS for profile switching, session listing, and revocation.

Commit: `test: demonstrate IAM profiles and session lifecycle`

### Task 5: Demonstrate audit and decision diagnostics

**Files:**
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamStarterConsumptionTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamShowcaseFixture.java`
- Modify: `iam-example/README.md`

**Interfaces:**
- Consumes `POST /iam/authorization/diagnostics` with an order resource and
  requested permission.
- Produces decision steps derived from `AuthorizationEngine`, not a duplicate
  authorization calculation.

- [ ] **Step 1: Write failing diagnostic-response test**

```java
var diagnosis = diagnose(readerToken, "order.approve", "ORDER", "9001", "WRITE");
assertEquals(false, diagnosis.allowed());
assertTrue(diagnosis.steps().stream().anyMatch(step -> step.code().contains("PERMISSION")));
```

- [ ] **Step 2: Run and verify failure**

Run: `mvn -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest test`

Expected: FAIL until the request body matches the generated OpenAPI DTO and
fixture data carries the expected template permissions.

- [ ] **Step 3: Use the generated contract without adding host authorization logic**

Build the HTTP body from the OpenAPI schema. Keep all decision explanation in
the starter diagnostics endpoint.

- [ ] **Step 4: Re-run and commit**

Expected: PASS for allowed and denied diagnostic projections.

Commit: `test: demonstrate IAM authorization diagnostics`

### Task 6: Publish the runnable walkthrough and CI boundaries

**Files:**
- Create: `scripts/test-showcase-readme.sh`
- Modify: `iam-example/README.md`
- Modify: `README.md`
- Modify: `.github/workflows/verify.yml`

**Interfaces:**
- Produces a copyable HTTP walkthrough and explicit fast versus
  real-infrastructure commands.

- [ ] **Step 1: Write README command-output checks**

Add a shell-based documentation check that verifies the README names the login,
order-read, forbidden-scope, profile-switch, diagnostics, and revoke steps.

- [ ] **Step 2: Run the check and verify failure**

Run: `bash scripts/test-showcase-readme.sh`

Expected: FAIL because the walkthrough is absent.

- [ ] **Step 3: Add walkthrough and CI stages**

Document only demonstration credentials and environment variables. Keep CI's
default jobs container-free; add a separately named integration job that runs
only when Docker is available on the runner.

- [ ] **Step 4: Verify and commit**

Run: `bash scripts/test-showcase-readme.sh`

Run: `mvn -pl iam-example -am -Dtest=IamStarterAutoConfigurationSmokeTest test`

Expected: PASS.

Commit: `docs: add IAM consumer showcase walkthrough`

## Final verification

- [ ] Run `scripts/test-identity-check.ps1` and the forbidden-identifier scan.
- [ ] Run `mvn -pl iam-spring-boot-autoconfigure -am -Dtest=IamAutoConfigurationTest test`.
- [ ] Run `mvn -pl iam-example -am -Dtest=IamStarterAutoConfigurationSmokeTest test`.
- [ ] Run `mvn -pl iam-spring-boot-starter -am package -DskipTests`.
- [ ] When Docker is available, run both showcase integration classes under
  `-Pintegration` and record that result separately from fast checks.
- [ ] Run `git diff --check`, review no legacy identifiers appear, and push the
  feature branch only after every applicable command succeeds.
