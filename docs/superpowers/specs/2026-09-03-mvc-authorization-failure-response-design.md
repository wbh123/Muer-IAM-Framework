# MVC Authorization Failure Response Design

## Goal

Make declarative MVC authorization failures consumable by applications without
requiring every host to replace the IAM interceptor. The starter must provide a
safe default JSON response and allow a host to replace its response format.

## Scope and boundaries

- Applies only to the Servlet MVC `@RequirePermission` adapter.
- Preserves the interceptor's existing authorization decisions and HTTP status
  semantics.
- Does not change bearer-token authentication, `AuthorizationEngine`, route
  ownership, or direct engine invocation.
- Does not expose tokens, principal details, permission codes, resource
  descriptors, parent scopes, or engine decision steps in an HTTP error body.

## Public extension point

The starter exposes this public SPI under
`io.github.iamstarter.autoconfigure.web`:

```java
void write(HttpServletRequest request, HttpServletResponse response,
           int status, IamAuthorizationFailure failure) throws IOException;
```

`IamAuthorizationFailureHandler` owns the HTTP body. Its companion public
failure value identifies the stable failure category. A host can register one
handler bean to replace the starter default and integrate a pre-existing error
envelope, localization policy, or observability convention.

The default implementation writes Spring `ProblemDetail` JSON with these safe
fields:

| Field | Meaning |
| --- | --- |
| `status` | The HTTP status: 401, 403, 404, or 500. |
| `title` | A generic HTTP-status title. |
| `detail` | A fixed generic description. |
| `code` | A stable IAM failure code. |
| `path` | The request path. |

## Failure contract

| Condition | Status | Stable code |
| --- | --- | --- |
| No authenticated IAM principal | 401 | `IAM_UNAUTHENTICATED` |
| Authorization engine denies | 403 | `IAM_ACCESS_DENIED` |
| Host resolver cannot find the resource | 404 | `IAM_RESOURCE_NOT_FOUND` |
| No host resource resolver is registered | 500 | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` |

Unannotated MVC handlers bypass the interceptor and never invoke this SPI.

## Auto-configuration

The Servlet auto-configuration provides the default handler only when the host
has not registered an `IamAuthorizationFailureHandler`. The interceptor is
constructed with the selected handler. A custom handler does not change which
requests are intercepted or what authorization request reaches the engine.

## Error behavior

The interceptor maps its known terminal conditions to a status and failure
category, delegates response writing exactly once, and returns `false`. A
handler implementation is responsible for committing the response. The default
handler sets the HTTP status and JSON content type before writing its body.

## Verification

Focused tests prove that the default handler writes each status and stable code
without protected IAM data; that a custom handler replaces the default; and
that the interceptor delegates each terminal condition once. Existing tests
continue to prove annotation precedence, unannotated bypass, authorization
request construction, and engine approval. The consumer integration suite
proves document routes retain 401, 403, 404, and successful responses through
MySQL and Redis Testcontainers.
