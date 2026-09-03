# Declarative MVC Permission Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a public `@RequirePermission` MVC adapter that sends annotated host routes through the existing IAM authorization engine.

**Architecture:** The annotation supplies an application permission and required scope access. A host-owned MVC resolver supplies a `ResourceDescriptor`; an auto-configured interceptor reads the `IamPrincipal`, constructs an `AuthorizationRequest`, and delegates to `AuthorizationEngine`. The document example uses the adapter while its host business behavior remains unchanged.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC/Security, JUnit 5, Mockito, Maven.

**Spec:** `docs/superpowers/specs/2026-09-03-require-permission-mvc-adapter-design.md`

## Global Constraints

- Public types live under `io.github.iamstarter.autoconfigure.web` and are available through `iam-spring-boot-starter`.
- The adapter delegates only to `AuthorizationEngine`; it never treats roles as authorization input.
- Hosts resolve all business resources through `MvcResourceDescriptorResolver`.
- Annotated handlers return `401` without an IAM Principal, `403` on engine denial, and `500` when a host resolver is missing or returns empty.
- The feature is Servlet/MVC-only. Direct `AuthorizationEngine.decide` use remains supported.
- Do not log or expose opaque tokens.

---

### Task 1: Define annotation and resource resolver SPI

**Files:**
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/RequirePermission.java`
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/MvcResourceDescriptorResolver.java`
- Test: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/RequirePermissionTest.java`

**Interfaces:** Produces `@RequirePermission(String value, ScopeAccess access = READ)` and `Optional<ResourceDescriptor> MvcResourceDescriptorResolver.resolve(HttpServletRequest, HandlerMethod)`.

- [ ] **Step 1: Write the failing public API test**

```java
@RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
void update() { }

assertTrue(method.isAnnotationPresent(RequirePermission.class));
assertEquals("document:update", method.getAnnotation(RequirePermission.class).value());
assertEquals(ScopeAccess.WRITE, method.getAnnotation(RequirePermission.class).access());
assertEquals(ScopeAccess.READ, RequirePermission.class.getMethod("access").getDefaultValue());
assertEquals(Optional.class, MvcResourceDescriptorResolver.class
        .getMethod("resolve", HttpServletRequest.class, HandlerMethod.class).getReturnType());
```

- [ ] **Step 2: Run red**

```bash
mvn -pl iam-spring-boot-autoconfigure -am -Dtest=RequirePermissionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because both public types are absent.

- [ ] **Step 3: Implement the minimal API**

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {
    String value();
    ScopeAccess access() default ScopeAccess.READ;
}

@FunctionalInterface
public interface MvcResourceDescriptorResolver {
    Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod);
}
```

- [ ] **Step 4: Run green and commit**

Run the Task 1 Maven command and require zero failures.

```bash
git add iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/RequirePermissionTest.java
git commit -m "feat: define declarative MVC permission API"
```

### Task 2: Add fail-closed MVC interception

**Files:**
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptor.java`
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationWebMvcConfiguration.java`
- Modify: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamAutoConfiguration.java`
- Test: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptorTest.java`
- Test: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/IamAutoConfigurationTest.java`

**Interfaces:** Consumes `RequirePermission`, `MvcResourceDescriptorResolver`, `AuthorizationEngine`, `IamPrincipal`, and `HandlerMethod`. Produces a `HandlerInterceptor` registered for `/**` only in a Servlet application with an authorization engine.

- [ ] **Step 1: Write failing interceptor behavior tests**

```java
assertTrue(interceptor.preHandle(request, response, unannotatedHandler));
assertFalse(interceptor.preHandle(request, unauthorizedResponse, readHandler));
assertEquals(401, unauthorizedResponse.getStatus());
assertEquals(500, missingResourceResponse.getStatus());
assertEquals(403, deniedResponse.getStatus());
verify(engine).decide(principal, new AuthorizationRequest(
        "document:update", "EXAMPLE", "WEB", resource, ScopeAccess.WRITE));
```

Use an annotation on a controller class and a different annotation on one
method to prove that the method declaration takes precedence.

- [ ] **Step 2: Run red**

```bash
mvn -pl iam-spring-boot-autoconfigure -am -Dtest=IamAuthorizationInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because `IamAuthorizationInterceptor` is absent.

- [ ] **Step 3: Implement interception and registration**

`preHandle` returns true for an unannotated handler. For annotated handlers it
must: locate method annotation before type annotation; require an authenticated
`IamPrincipal` or send `401`; require a resolver and non-empty descriptor or
send `500`; call `engine.decide` using principal domain/client, annotation
permission/access, and resolved resource; send `403` on denial; otherwise
return true. `IamAuthorizationWebMvcConfiguration` implements
`WebMvcConfigurer` and registers the interceptor. Add both beans in
`IamAutoConfiguration` with Servlet-web and `AuthorizationEngine` conditions.

- [ ] **Step 4: Run green and commit**

```bash
mvn -pl iam-spring-boot-autoconfigure -am -Dtest=IamAuthorizationInterceptorTest,IamAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
git add iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure
git commit -m "feat: add declarative MVC authorization interceptor"
```

### Task 3: Migrate the document consumer

**Files:**
- Create: `iam-example/src/main/java/io/github/iamstarter/example/ExampleDocumentResourceResolver.java`
- Modify: `iam-example/src/main/java/io/github/iamstarter/example/DocumentController.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/DocumentControllerTest.java`
- Modify: `iam-example/src/test/java/io/github/iamstarter/example/IamConsumerIntegrationTest.java`
- Modify: `iam-example/README.md`

**Interfaces:** `ExampleDocumentResourceResolver` implements the public resolver SPI, maps an existing document ID to a `DOCUMENT` descriptor with `PROJECT` and `DEPARTMENT` parents, and returns empty for an unknown document. The controller uses `@RequirePermission("document:read")` and `@RequirePermission(value = "document:update", access = WRITE)`.

- [ ] **Step 1: Write failing consumer assertions**

```java
assertFalse(Arrays.stream(DocumentController.class.getConstructors())
        .anyMatch(constructor -> Arrays.asList(constructor.getParameterTypes()).contains(AuthorizationEngine.class)));
assertEquals(200, request("/api/documents/1001", "GET", author.token(), null).statusCode());
assertEquals(403, request("/api/documents/1001", "POST", reader.token(), update).statusCode());
assertEquals(200, request("/api/documents/1001", "POST", author.token(), update).statusCode());
assertEquals(403, request("/api/documents/2001", "GET", reader.token(), null).statusCode());
```

- [ ] **Step 2: Run red**

```bash
mvn -pl iam-example -am -Dtest=DocumentControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the controller still has direct engine ownership and has no resolver.

- [ ] **Step 3: Implement host mapping and annotation use**

Move document lookup into a focused host-owned store/service shared by the
controller and resolver. Keep `404` in the controller for an unknown document;
the interceptor only evaluates known resources. Remove manual principal lookup,
authorization request construction, and direct engine calls from the
controller. Keep the host security chain and its bearer filter unchanged.

- [ ] **Step 4: Run green and commit**

```bash
mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/verify-consumer-public-api.sh iam-example/src/main/java
git add iam-example
git commit -m "feat: demonstrate declarative document authorization"
```

### Task 4: Document the contract and enforce it in example checks

**Files:**
- Modify: `README.md`
- Modify: `docs/IAM_INTEGRATION_GUIDE.md`
- Modify: `docs/IAM_TARGET_ARCHITECTURE.md`
- Modify: `iam-example/README.md`
- Modify: `scripts/test-consumer-public-api.sh`
- Modify: `scripts/test-showcase-readme.sh`

**Interfaces:** Documents annotation usage, resolver registration, continued bearer-filter ownership, direct engine compatibility, and 401/403/500 responses.

- [ ] **Step 1: Write failing documentation checks**

Add requirements for `@RequirePermission` and `MvcResourceDescriptorResolver`
to `scripts/test-showcase-readme.sh`; add a legal synthetic import of
`io.github.iamstarter.autoconfigure.web.RequirePermission` to
`scripts/test-consumer-public-api.sh`.

- [ ] **Step 2: Run red**

```bash
bash scripts/test-consumer-public-api.sh
bash scripts/test-showcase-readme.sh
```

Expected: README check failure until the new contract is documented.

- [ ] **Step 3: Add concise usage documentation**

Show annotation and resolver examples, state the 401/403/500 behavior, and
correct the target architecture example wording to include documents, projects,
and departments.

- [ ] **Step 4: Run green and commit**

```bash
bash scripts/test-consumer-public-api.sh
bash scripts/test-showcase-readme.sh
git diff --check
git add README.md docs/IAM_INTEGRATION_GUIDE.md docs/IAM_TARGET_ARCHITECTURE.md iam-example/README.md scripts/test-consumer-public-api.sh scripts/test-showcase-readme.sh
git commit -m "docs: describe declarative MVC authorization"
```

## Final verification

- [ ] Run `mvn -pl iam-spring-boot-autoconfigure -am -Dtest=IamAuthorizationInterceptorTest,IamAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] Run `mvn -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] Run `bash scripts/test-consumer-public-api.sh && bash scripts/test-showcase-readme.sh`.
- [ ] Run `git diff --check` and confirm the branch is clean after commits.
