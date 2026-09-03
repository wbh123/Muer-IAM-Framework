# MVC Authorization Failure Response Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe default `ProblemDetail` responses and a host-overridable SPI for declarative MVC IAM authorization failures.

**Architecture:** Public response types live under the auto-configuration web package. The interceptor maps existing failures to stable values and delegates output. Servlet auto-configuration supplies a default only when the host supplies no replacement.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC Servlet API, `ProblemDetail`, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-03-mvc-authorization-failure-response-design.md`

## Global Constraints

- Retain Servlet MVC-only scope and existing direct `AuthorizationEngine` use.
- Never emit opaque tokens, principal data, permissions, resources, scopes, or decision steps in default bodies.
- Preserve 401, 403, 404, and 500 behavior for annotated handlers; unannotated routes bypass it.
- A host `IamAuthorizationFailureHandler` replaces the default bean.
- Work only on `codex/post-release-development`; do not modify `main`.

## Task 1: Public failure API and safe default writer

**Files:**
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailure.java`
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailureHandler.java`
- Create: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/ProblemDetailIamAuthorizationFailureHandler.java`
- Test: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailureHandlerTest.java`

**Interfaces:** `IamAuthorizationFailure` is a public enum with `code()` and values `UNAUTHENTICATED`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, and `RESOURCE_RESOLUTION_UNAVAILABLE`. Their codes are respectively `IAM_UNAUTHENTICATED`, `IAM_ACCESS_DENIED`, `IAM_RESOURCE_NOT_FOUND`, and `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`. `IamAuthorizationFailureHandler.write(HttpServletRequest, HttpServletResponse, int, IamAuthorizationFailure)` returns `void` and throws `IOException`.

- [ ] **Step 1: Write the failing test**

Assert every enum code and reflect the exact SPI signature. Create a mock GET `/api/documents/1001`; call the not-yet-created default handler with status 403 and `ACCESS_DENIED`; assert status 403, content type `application/problem+json`, its code and request URI in the body, and the absence of `document:read` and `opaque-token`. Add status/code cases for 401, 404, and 500.

- [ ] **Step 2: Verify the test fails**

Run `mvn -q -pl iam-spring-boot-autoconfigure -am -Dtest=IamAuthorizationFailureHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`.

Expected: compilation fails because the types do not exist.

- [ ] **Step 3: Implement the minimal types**

Create the public enum and functional interface. Create a stateless final default handler that forms a Spring `ProblemDetail` using a generic title/detail, adds only `code` and `path`, sets the status and `application/problem+json`, and writes JSON through the Servlet MVC JSON conversion path. Do not manually interpolate the URI or any JSON field.

- [ ] **Step 4: Verify the test passes**

Run the Step 2 command. Expected: all API and safe-body cases pass.

- [ ] **Step 5: Commit**

Run `git add iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailure.java iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailureHandler.java iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/ProblemDetailIamAuthorizationFailureHandler.java iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailureHandlerTest.java` then `git commit -m "feat: add MVC authorization failure responses"`.

## Task 2: Delegate interceptor failures

**Files:**
- Modify: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptor.java`
- Modify: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptorTest.java`

**Interfaces:** Add required constructor argument `IamAuthorizationFailureHandler failures`. Map missing principal to `(401, UNAUTHENTICATED)`, absent resolver to `(500, RESOURCE_RESOLUTION_UNAVAILABLE)`, empty resource to `(404, RESOURCE_NOT_FOUND)`, and engine denial to `(403, ACCESS_DENIED)`.

- [ ] **Step 1: Write the failing test**

Build the interceptor with a Mockito `IamAuthorizationFailureHandler`; exercise all four terminal conditions and verify `write(request, response, expectedStatus, expectedFailure)` exactly once. For engine-allowed and unannotated routes, verify no handler interactions.

- [ ] **Step 2: Verify the test fails**

Run `mvn -q -pl iam-spring-boot-autoconfigure -am -Dtest=IamAuthorizationInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test`.

Expected: constructor compilation failure.

- [ ] **Step 3: Implement delegation**

Replace `sendError` with a private method accepting request, response, status and failure, calling `failures.write(...)` exactly once and returning false. Require `failures` non-null. Leave annotation precedence, principal validation, resolver invocation and `AuthorizationRequest` construction unchanged.

- [ ] **Step 4: Verify the test passes and commit**

Run the Step 2 command, then stage the interceptor and its test and commit `feat: delegate MVC authorization failures`.

## Task 3: Auto-configure default and host override

**Files:**
- Modify: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamAutoConfiguration.java`
- Modify: `iam-spring-boot-autoconfigure/src/test/java/io/github/iamstarter/autoconfigure/IamAutoConfigurationTest.java`

**Interfaces:** Add a Servlet-only bean named `iamAuthorizationFailureHandler`, conditional on a missing `IamAuthorizationFailureHandler`; inject the selected handler into the interceptor factory.

- [ ] **Step 1: Write the failing test**

Assert the default context gets `ProblemDetailIamAuthorizationFailureHandler`. Add `CustomFailureHandlerConfiguration`, returning a lambda `IamAuthorizationFailureHandler`; assert the custom bean exists and `iamAuthorizationFailureHandler` does not.

- [ ] **Step 2: Verify the test fails**

Run `mvn -q -pl iam-spring-boot-autoconfigure -am -Dtest=IamAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.

Expected: default bean and constructor wiring are absent.

- [ ] **Step 3: Implement conditional wiring**

Add the `@ConditionalOnWebApplication(SERVLET)` plus `@ConditionalOnMissingBean(IamAuthorizationFailureHandler.class)` factory. Add its selected handler as the final interceptor factory argument. Preserve all existing engine, resolver and security-chain conditions.

- [ ] **Step 4: Verify the test passes and commit**

Run the Step 2 command, then stage these files and commit `feat: configure MVC authorization failure handler`.

## Task 4: Document and verify consumer contract

**Files:**
- Modify: `iam-example/README.md`
- Modify: `docs/IAM_MIGRATION_GUIDE.md`
- Modify: `scripts/test-showcase-readme.sh`

**Interfaces:** Document `IamAuthorizationFailureHandler`, `ProblemDetail`, host replacement ownership, and all four stable codes.

- [ ] **Step 1: Write the failing documentation check**

Add script requirements for the literal terms `IamAuthorizationFailureHandler`, `ProblemDetail`, `IAM_ACCESS_DENIED`, and `IAM_RESOURCE_NOT_FOUND`.

- [ ] **Step 2: Verify the check fails**

Run `bash scripts/test-showcase-readme.sh`.

Expected: README lacks this public contract.

- [ ] **Step 3: Document customization**

Show a host `@Bean` returning `IamAuthorizationFailureHandler`; list all four status/code mappings; state that the default is `ProblemDetail` and must not reveal protected IAM details. Mirror the ownership boundary in the migration guide.

- [ ] **Step 4: Run final verification and commit**

Run `bash scripts/test-showcase-readme.sh`, `bash scripts/test-consumer-public-api.sh`, `mvn -q -pl iam-spring-boot-autoconfigure,iam-example -am -Dtest=IamAuthorizationFailureHandlerTest,IamAuthorizationInterceptorTest,IamAutoConfigurationTest,DocumentControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`, `mvn -q -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`, and `git diff --check`. Then stage documentation files and commit `docs: describe MVC authorization failure responses`.

Expected: focused checks and MySQL/Redis Testcontainers retain document 401, 403, 404, read and update behavior.

## Self-review

- Spec coverage: Tasks 1-3 implement the public SPI, safe default, delegation and host replacement; Task 4 documents and verifies it.
- Placeholder scan: no incomplete implementation markers are present.
- Type consistency: every task uses the Task 1 enum and handler types.
