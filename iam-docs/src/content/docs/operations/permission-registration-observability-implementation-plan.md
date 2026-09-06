# Muer Product Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide application-declared permission registration and optional, low-cardinality Muer runtime observability without adding a Maven module or changing Task A-owned files.

**Architecture:** `muer-authorization` exposes the permission-definition SPI and registration service, while its persistence port is implemented by MyBatis and invoked by application-ready auto-configuration. `muer-core` owns a framework-neutral metrics interface; authentication, authorization, and session flows receive it explicitly, and conditional auto-configuration supplies Micrometer and Actuator adapters only when their classes are present.

**Tech Stack:** Java 21, Spring Boot 4 auto-configuration, MyBatis, MySQL upsert SQL, Spring MVC, Actuator, Micrometer, JUnit 5, AssertJ application-context tests.

**Spec:** `iam-docs/src/content/docs/operations/permission-registration-observability-design.md`

## Global Constraints

- Work only in `codex/muer-product-closure` and preserve Task A-owned root metadata, CI, license, scripts, README, release report, and release notes.
- Use existing `iam_permission`; do not add a `muer_permission` table or modify database migrations.
- Do not add a Maven module or modify the root POM.
- `PermissionDefinition` and `PermissionDefinitionProvider` are stable Starter-consumer SPI; `MuerMetrics` remains undocumented as a public extension contract.
- Registration inserts new definitions, refreshes only display name and description, preserves extra rows and enabled state, and never deletes.
- Metrics must not contain user, token, session, resource, permission-code, IP, or username tags; instrumentation failures must not affect business results.
- User-facing documentation is added only after the corresponding APIs and behavior exist.

---

### Task 1: Define and verify the permission-registration domain boundary

**Files:**
- Create: `muer-authorization/src/main/java/io/github/muer/authorization/PermissionDefinition.java`
- Create: `muer-authorization/src/main/java/io/github/muer/authorization/PermissionDefinitionProvider.java`
- Create: `muer-authorization/src/main/java/io/github/muer/authorization/PermissionRepository.java`
- Create: `muer-authorization/src/main/java/io/github/muer/authorization/PermissionRegistrationService.java`
- Create: `muer-authorization/src/test/java/io/github/muer/authorization/PermissionRegistrationServiceTest.java`

**Interfaces:**
- Produces: `record PermissionDefinition(String code, String displayName, String description)`.
- Produces: `interface PermissionDefinitionProvider { Collection<PermissionDefinition> getPermissionDefinitions(); }`.
- Produces: `interface PermissionRepository { boolean existsByCode(String code); void upsert(PermissionDefinition definition); }`.
- Produces: `void PermissionRegistrationService.register(Collection<PermissionDefinitionProvider> providers)`.

- [ ] **Step 1: Write failing domain tests**

```java
@Test void accepts_identical_definitions_once_and_upserts_once() {
    var repository = new RecordingPermissionRepository();
    var definition = new PermissionDefinition("order:item:approve", "Approve", "Approves an item");
    new PermissionRegistrationService(repository).register(List.of(() -> List.of(definition), () -> List.of(definition)));
    assertThat(repository.upserts()).containsExactly(definition);
}

@Test void rejects_conflicting_metadata_for_one_code() {
    var service = new PermissionRegistrationService(new RecordingPermissionRepository());
    assertThatThrownBy(() -> service.register(List.of(
            () -> List.of(new PermissionDefinition("order:read", "Read", "One")),
            () -> List.of(new PermissionDefinition("order:read", "Read orders", "Two")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Conflicting Muer permission definition: order:read");
}
```

- [ ] **Step 2: Run the focused test and confirm compilation failure**

Run: `mvn -B -pl muer-authorization test -Dtest=PermissionRegistrationServiceTest`

Expected: test compilation fails because the registration types do not exist.

- [ ] **Step 3: Implement validation, normalization, grouping, and registration**

```java
public void register(Collection<PermissionDefinitionProvider> providers) {
    Map<String, PermissionDefinition> definitions = new LinkedHashMap<>();
    for (var provider : providers) for (var raw : provider.getPermissionDefinitions()) {
        var definition = normalizeAndValidate(raw);
        var previous = definitions.putIfAbsent(definition.code(), definition);
        if (previous != null && !previous.equals(definition)) {
            throw new IllegalStateException("Conflicting Muer permission definition: " + definition.code());
        }
    }
    definitions.values().forEach(repository::upsert);
}
```

Reject null, blank-after-trim text, control characters, codes or display names longer than 191 characters, and descriptions longer than 500 characters. Allow punctuation such as `:`, `.`, and `-` without a restrictive regex.

- [ ] **Step 4: Add validation and zero-provider tests, then run them**

```java
@Test void accepts_zero_providers() { new PermissionRegistrationService(repository).register(List.of()); assertThat(repository.upserts()).isEmpty(); }
@ParameterizedTest @ValueSource(strings = {"", " ", "bad\ncode"})
void rejects_invalid_code(String code) { assertThatThrownBy(() -> new PermissionDefinition(code, "Name", null)).isInstanceOf(IllegalArgumentException.class); }
```

Run: `mvn -B -pl muer-authorization test -Dtest=PermissionRegistrationServiceTest`

Expected: PASS.

- [ ] **Step 5: Commit the domain boundary**

```bash
git add muer-authorization/src/main muer-authorization/src/test
git commit -m "feat: add permission definition registration domain"
```

### Task 2: Persist definitions and wire application-ready registration

**Files:**
- Create: `muer-persistence-mybatis/src/main/java/io/github/muer/persistence/MyBatisPermissionRepository.java`
- Create: `muer-persistence-mybatis/src/main/java/io/github/muer/persistence/mapper/IamPermissionMapper.java`
- Create: `muer-persistence-mybatis/src/main/resources/mapper/iam/IamPermissionMapper.xml`
- Modify: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerAutoConfiguration.java`
- Create: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/PermissionDefinitionRegistrationListener.java`
- Create: `muer-persistence-mybatis/src/test/java/io/github/muer/persistence/MyBatisPermissionRepositoryIntegrationTest.java`
- Modify: `muer-spring-boot-autoconfigure/src/test/java/io/github/muer/autoconfigure/MuerAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `PermissionRepository.upsert(PermissionDefinition)` from Task 1.
- Produces: a conditional `PermissionRegistrationService` bean and application-ready listener that receives all providers.

- [ ] **Step 1: Write failing MyBatis integration tests**

```java
@Test void upsert_inserts_then_refreshes_descriptive_metadata_without_changing_enabled() {
    repository.upsert(new PermissionDefinition("document:read", "Read", "Initial"));
    disable("document:read");
    repository.upsert(new PermissionDefinition("document:read", "Read document", "Updated"));
    assertThat(permission("document:read")).containsExactly("Read document", "Updated", false);
}
```

Also prove that a pre-existing `legacy:read` row survives registration of `document:read`.

- [ ] **Step 2: Run the integration test and confirm it fails**

Run: `mvn -B -pl muer-persistence-mybatis -am test -Dtest=MyBatisPermissionRepositoryIntegrationTest`

Expected: failure because no permission mapper or repository exists.

- [ ] **Step 3: Add the mapper and MyBatis repository**

```xml
<insert id="upsert">
  INSERT INTO iam_permission (permission_code, display_name, description)
  VALUES (#{definition.code}, #{definition.displayName}, #{definition.description})
  ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), description = VALUES(description)
</insert>
```

Register `IamPermissionMapper.xml` in `MuerAutoConfiguration.MAPPER_RESOURCES` and expose `MyBatisPermissionRepository` when an SQL session factory is available.

- [ ] **Step 4: Wire ready-event registration and prove lifecycle behavior**

```java
@EventListener(ApplicationReadyEvent.class)
public void registerDefinitions() { service.register(providers.orderedStream().toList()); }
```

Use an `ObjectProvider<PermissionDefinitionProvider>` so zero providers start normally. Add an application-context test with one provider and a fake `PermissionRepository`, publish `ApplicationReadyEvent`, and assert one upsert. Add a second event assertion to prove idempotency.

- [ ] **Step 5: Run persistence and auto-configuration tests**

Run: `mvn -B -pl muer-spring-boot-autoconfigure -am test -Dtest=MyBatisPermissionRepositoryIntegrationTest,MuerAutoConfigurationTest`

Expected: PASS.

- [ ] **Step 6: Commit persistence and lifecycle wiring**

```bash
git add muer-persistence-mybatis muer-spring-boot-autoconfigure
git commit -m "feat: register declared permissions at application ready"
```

### Task 3: Add annotation consistency warnings and update the example

**Files:**
- Create: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/web/RequirePermissionDefinitionWarningListener.java`
- Create: `muer-spring-boot-autoconfigure/src/test/java/io/github/muer/autoconfigure/web/RequirePermissionDefinitionWarningListenerTest.java`
- Modify: `muer-example/src/main/java/io/github/muer/example/QuickStartDemoSeeder.java`
- Create or modify: `muer-example/src/main/java/io/github/muer/example/DocumentPermissionConfiguration.java`
- Modify: `muer-example/src/test/java/io/github/muer/example/QuickStartDemoSeedIntegrationTest.java`

**Interfaces:**
- Consumes: registered definitions from `PermissionDefinitionProvider` and `PermissionRepository.existsByCode(String)`.
- Produces: advisory WARN records for MVC `@RequirePermission` values absent from defined permissions.

- [ ] **Step 1: Write a failing scan test**

```java
@Test void warns_when_mvc_handler_requires_undefined_permission() {
    listener.onApplicationEvent(readyEvent());
    assertThat(capturedLogs()).contains("Muer permission 'order:approve' is referenced but no PermissionDefinitionProvider registered it.");
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `mvn -B -pl muer-spring-boot-autoconfigure -am test -Dtest=RequirePermissionDefinitionWarningListenerTest`

Expected: failure because the advisory scanner does not exist.

- [ ] **Step 3: Implement the MVC-only advisory scan**

At application ready, inspect `RequestMappingHandlerMapping.getHandlerMethods()`, read method annotation before class annotation as the interceptor does, and compare each value against the deduplicated provider definitions. Emit one WARN per missing code. Do not alter interceptor behavior and do not fail startup.

- [ ] **Step 4: Replace example permission SQL with a provider**

```java
@Bean
PermissionDefinitionProvider documentPermissions() {
    return () -> List.of(
        new PermissionDefinition("document:read", "Read document", "Read a document"),
        new PermissionDefinition("document:update", "Update document", "Update a document"));
}
```

Delete only the `iam_permission` cleanup and insert statements from the seeder. Keep template/profile/scope setup. Update the integration assertion to obtain the two rows through registration.

- [ ] **Step 5: Run focused example and scanner tests**

Run: `mvn -B -pl muer-example,muer-spring-boot-autoconfigure -am test -Dtest=QuickStartDemoSeedIntegrationTest,RequirePermissionDefinitionWarningListenerTest`

Expected: PASS.

- [ ] **Step 6: Commit consumer consistency and example changes**

```bash
git add muer-spring-boot-autoconfigure muer-example
git commit -m "feat: declare example permissions through provider SPI"
```

### Task 4: Establish core metrics and instrument business services

**Files:**
- Create: `muer-core/src/main/java/io/github/muer/core/metrics/MuerMetrics.java`
- Create: `muer-core/src/main/java/io/github/muer/core/metrics/NoOpMuerMetrics.java`
- Modify: `muer-authentication/src/main/java/io/github/muer/authentication/AuthenticationService.java`
- Modify: `muer-authorization/src/main/java/io/github/muer/authorization/DefaultAuthorizationEngine.java`
- Modify: `muer-session/src/main/java/io/github/muer/session/SessionService.java`
- Create: `muer-core/src/test/java/io/github/muer/core/metrics/NoOpMuerMetricsTest.java`
- Modify: `muer-authentication/src/test/java/io/github/muer/authentication/AuthenticationServiceTest.java`
- Modify: `muer-authorization/src/test/java/io/github/muer/authorization/DefaultAuthorizationEngineTest.java`
- Modify: `muer-session/src/test/java/io/github/muer/session/SessionServiceTest.java`

**Interfaces:**
- Produces: `MuerMetrics.authenticationAttempt(String result, String clientType)`, `authorizationDecision(boolean allowed, String decisionCode, Duration duration)`, `sessionCreated()`, `sessionRevoked()`, and `tokenLookup(String result)`.
- Consumes: `NoOpMuerMetrics.INSTANCE` as the required fallback.

- [ ] **Step 1: Write failing instrumentation tests**

```java
@Test void records_allow_decision_and_duration() {
    var metrics = new RecordingMuerMetrics();
    var decision = engine(metrics).decide(principal(), allowedRequest());
    assertThat(decision.allowed()).isTrue();
    assertThat(metrics.decision()).isEqualTo(new RecordedDecision(true, "ALLOWED"));
    assertThat(metrics.duration()).isPositive();
}
```

Add equivalent tests for login success/failure, token hit/miss/error, session creation, and session revocation.

- [ ] **Step 2: Run the focused tests and confirm constructor or API failures**

Run: `mvn -B -pl muer-core,muer-authorization,muer-authentication,muer-session -am test -Dtest=DefaultAuthorizationEngineTest,AuthenticationServiceTest,SessionServiceTest,NoOpMuerMetricsTest`

Expected: test compilation fails because `MuerMetrics` and explicit constructor dependencies are absent.

- [ ] **Step 3: Implement no-op-safe instrumentation**

Wrap all metrics calls with a private `record(Runnable)` helper that catches `RuntimeException`. Measure the whole `DefaultAuthorizationEngine.decide` call with `System.nanoTime()` in a `finally` block and report the final decision code and allow/deny result. Record authentication result only after its functional result is determined. Record token lookup hit/miss/error around `TokenStore.resolve`. Record session-created after successful session persistence and session-revoked after the repository/token revocation completes.

- [ ] **Step 4: Run the focused core tests**

Run: `mvn -B -pl muer-core,muer-authorization,muer-authentication,muer-session -am test -Dtest=DefaultAuthorizationEngineTest,AuthenticationServiceTest,SessionServiceTest,NoOpMuerMetricsTest`

Expected: PASS, including tests where a throwing metrics implementation leaves the original business outcome unchanged.

- [ ] **Step 5: Commit framework-neutral metrics**

```bash
git add muer-core muer-authorization muer-authentication muer-session
git commit -m "feat: instrument core Muer runtime flows"
```

### Task 5: Add conditional Micrometer and Actuator adapters

**Files:**
- Modify: `muer-spring-boot-autoconfigure/pom.xml`
- Create: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/metrics/MicrometerMuerMetrics.java`
- Create: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/actuator/MuerHealthIndicator.java`
- Create: `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerObservabilityAutoConfiguration.java`
- Modify: `muer-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `muer-spring-boot-autoconfigure/src/test/java/io/github/muer/autoconfigure/MuerObservabilityAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `MuerMetrics` from Task 4.
- Produces: a conditional Micrometer implementation and Actuator `muer` health contributor.

- [ ] **Step 1: Add optional test dependencies and failing context tests**

```java
@Test void actuator_classpath_registers_muer_health_indicator() {
    runner.withClassLoader(new FilteredClassLoader())
          .run(context -> assertThat(context).hasSingleBean(MuerHealthIndicator.class));
}

@Test void meter_registry_records_low_cardinality_metrics() {
    var registry = new SimpleMeterRegistry();
    metrics(registry).authorizationDecision(false, "PERMISSION_DENIED", Duration.ofMillis(4));
    assertThat(registry.find("muer.authorization.decisions").tags("result", "deny", "decisionCode", "PERMISSION_DENIED").counter().count()).isEqualTo(1);
}
```

- [ ] **Step 2: Run the observability test and confirm it fails**

Run: `mvn -B -pl muer-spring-boot-autoconfigure -am test -Dtest=MuerObservabilityAutoConfigurationTest`

Expected: failure because optional adapters do not exist.

- [ ] **Step 3: Implement conditional adapters**

Add optional `spring-boot-actuator` and `micrometer-core` dependencies to the module POM. Guard the metrics configuration with `@ConditionalOnClass(MeterRegistry.class)` and `@ConditionalOnBean(MeterRegistry.class)`. Guard health with `@ConditionalOnClass(HealthIndicator.class)`. Register counters/timer only with the prescribed names and tags. `MuerHealthIndicator.health()` must return `Health.up().withDetail("enabled", true).build()` and no sensitive detail or database/Redis probe.

- [ ] **Step 4: Add no-Actuator and no-registry tests, then run all adapter tests**

```java
@Test void no_meter_registry_uses_no_op_metrics() { runner.run(context -> assertThat(context).hasSingleBean(NoOpMuerMetrics.class)); }
@Test void no_actuator_classpath_does_not_require_health_indicator() { /* filtered classpath context starts */ }
```

Run: `mvn -B -pl muer-spring-boot-autoconfigure -am test -Dtest=MuerObservabilityAutoConfigurationTest`

Expected: PASS.

- [ ] **Step 5: Commit optional runtime observability**

```bash
git add muer-spring-boot-autoconfigure/pom.xml muer-spring-boot-autoconfigure/src
git commit -m "feat: add optional Muer actuator metrics"
```

### Task 6: Publish the implemented consumer documentation and validate Task B

**Files:**
- Create: `iam-docs/src/content/docs/getting-started/define-permissions.md`
- Create: `iam-docs/src/content/docs/getting-started/permission-management.md`
- Create: `iam-docs/src/content/docs/operations/observability.md`
- Modify: `iam-docs/src/content/config.ts` or the existing docs navigation source
- Modify: `docs/QUICK_START.md`
- Modify: `docs/IAM_INTEGRATION_GUIDE.md`
- Modify: `docs/PUBLIC_API.md`

**Interfaces:**
- Documents only the SPI and observable behavior completed in Tasks 1-5.

- [ ] **Step 1: Write permission definition guidance from real APIs**

Include a compilable `@Bean PermissionDefinitionProvider` example, explain registration/upsert/no-deletion semantics, and show the developer-to-template-to-profile-to-scope path. State that administrators manage assignments, not arbitrary permission codes.

- [ ] **Step 2: Write permission management guidance**

Answer whether a separate backend is needed: it is not. Explain official Admin Console and Management API options, and prohibit direct writes to `iam_*` tables.

- [ ] **Step 3: Write runtime observability guidance**

Explain the distinction between governance dashboard, Actuator health, and Micrometer runtime metrics. List only implemented metric names, allowed tags, and the optional dependency behavior.

- [ ] **Step 4: Update navigation and existing guides**

Add Define Permissions and Permission Management under Getting Started, and Observability under Operations. Replace Quick Start seeding instructions with the Provider workflow; add only `PermissionDefinition` and `PermissionDefinitionProvider` to the stable public API document.

- [ ] **Step 5: Run focused checks and full Task B verification**

Run:

```bash
mvn -B -pl muer-spring-boot-autoconfigure,muer-example -am test
mvn -B test
cd iam-docs && npm ci && npm run check && npm run build
cd ../iam-admin-web && npm ci && npm run api:generate && npm run type-check && npm run test && npm run build
git diff --check
git status --short
```

Expected: Task B tests and builds pass. Any repository-level failure caused solely by Task A-owned identity/CI files is recorded separately and not changed here.

- [ ] **Step 6: Commit documentation and verification-ready work**

```bash
git add iam-docs docs
git commit -m "docs: explain Muer permissions and observability"
```
