# Declarative MVC Permission Adapter Design

## Goal

Allow a Spring MVC host application to protect a controller method with a
public `@RequirePermission` annotation while preserving IAM's existing
permission, active-profile, and resource-scope decision path.

## Scope and constraints

- The feature belongs to `iam-spring-boot-autoconfigure` and is transitively
  available through the single `iam-spring-boot-starter` dependency.
- The annotation is an optional host convenience; direct
  `AuthorizationEngine.decide` calls remain supported and unchanged.
- A host owns resource lookup. IAM never infers application resource types,
  identifiers, hierarchy, or authorization policies from HTTP paths.
- The interceptor obtains the current `IamPrincipal` from Spring Security and
  invokes the existing `AuthorizationEngine`; it never interprets roles or
  grants access on its own.
- The adapter is Servlet/MVC-only. It does not add a WebFlux implementation.

## Public API

`io.github.iamstarter.autoconfigure.web.RequirePermission` is a runtime,
method-or-type annotation:

```java
@RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
@PostMapping("/api/documents/{id}")
Document update(@PathVariable String id, @RequestBody DocumentUpdate update) { ... }
```

`value` is the application-defined atomic permission code. `access` defaults
to `ScopeAccess.READ`. A method annotation overrides the enclosing controller
type annotation.

`io.github.iamstarter.autoconfigure.web.MvcResourceDescriptorResolver` is the
host SPI:

```java
Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod);
```

The resolver returns the resource to evaluate for the matched handler. It may
use validated request path variables and host services. Returning an empty
result means the requested host resource does not exist and produces `404`; it
is never treated as allowed access. An annotated route without a resolver bean
is a host configuration error.

## Request flow

```text
MVC handler with @RequirePermission
  -> IamAuthorizationInterceptor
  -> SecurityContext IamPrincipal
  -> host MvcResourceDescriptorResolver
  -> AuthorizationRequest(permission, principal domain/client, resource, access)
  -> existing AuthorizationEngine
  -> handler, or 401/403 response
```

If no annotation is present, the interceptor does nothing. If an annotated
handler has no authenticated `IamPrincipal`, the response is `401`. If the
engine denies the request, the response is `403`. If the resolver returns empty
the interceptor returns `404`. If no resolver bean exists, it returns `500` so
an incomplete host integration cannot silently expose a route.

## Auto-configuration

The auto-configuration creates the interceptor only in a Servlet web
application with an `AuthorizationEngine`. A small `WebMvcConfigurer` registers
it for all MVC paths; annotation detection makes unannotated handlers no-ops.
The resolver is looked up lazily so applications without annotated controllers
do not need to register one.

The adapter does not configure host security matchers. Hosts continue to add
the existing `IamBearerTokenFilter` to their own protected route chain. The
interceptor supplies a deterministic `401` when an annotated route is reached
without an IAM Principal.

## Example integration

The document showcase will replace its manual `AuthorizationEngine` calls with
`@RequirePermission` on read and update methods. Its resolver reads the
document ID path variable, maps it to the host-owned document, and returns a
`DOCUMENT` descriptor with `PROJECT` and `DEPARTMENT` parents. The business
handler remains responsible for returning `404` when the document is absent.

## Verification

Focused unit tests prove annotation precedence, an unannotated handler bypass,
unauthenticated `401`, missing-resource `404`, resolver misconfiguration `500`,
engine denial `403`, and an allowed request reaching the handler. Consumer integration tests prove
the document read/write, cross-project denial, profile switch, and session
revocation flows still return their established HTTP results through the
annotation path.
