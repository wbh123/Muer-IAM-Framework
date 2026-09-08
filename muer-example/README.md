# IAM Starter Consumer Showcase

`muer-example` is a consuming Spring Boot application, not another IAM
deployment layer. Its only production dependency is `muer-spring-boot-starter`.
The host application supplies its small `IdentityAuthenticator` and order-to-
department hierarchy adapter; the starter supplies schema migration, MyBatis
repositories, opaque tokens, persisted sessions, IAM controllers, permission
evaluation, audit, and diagnostics.

The showcase uses anonymous demonstration data only:

| Identity | Password | Client | Profile state | Result |
| --- | --- | --- | --- | --- |
| `operator-a` | `demo-pass` | `WEB` | `reader-501` | Reads order `9001`; cannot approve it or read department `502`. |
| `operator-a` | `demo-pass` | `WEB` | `approver-501` after profile switch | Reads and approves order `9001`; still cannot read department `502`. |
| `operator-b` | `demo-pass` | `WEB` | `reader-502` | Reads order `9002`; cannot read department `501`. |

These credentials exist solely for this consumer example. Do not copy them into
a real application or use them as deployment credentials.

## Independent document consumer

The same application also contains a deliberately small, host-owned document
surface. It demonstrates how a third-party service protects its own routes
without importing IAM persistence or implementation classes:

- `GET /public/health` is public.
- `GET /api/documents/{id}` requires `document:read` and PROJECT READ scope.
- `POST /api/documents/{id}` requires `document:update` and PROJECT WRITE scope.

The isolated Testcontainers fixture uses `author-a` as a project `101`
administrator and `reader-b` as a project `101` reader. A request for document
`2001` (project `202`) is denied even though `reader-b` has a valid token.
`author-a` can also switch to a same-user read-only Profile; the replacement
token loses write access while the original administrator session remains
valid. The fixture is test-only; these identities are not local deployment
accounts.

Host code depends on public IAM API/SPI types only. Check that boundary before
publishing changes:

```bash
bash scripts/verify-consumer-public-api.sh
```

### Declarative MVC authorization

The document controller declares its permission at the endpoint boundary. The
host application supplies the resource mapping; the starter supplies the MVC
interceptor and delegates the final decision to `AuthorizationEngine`.

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
ResponseEntity<Document> read(@PathVariable String id) { /* host handler */ }

@Component
final class DocumentResourceResolver implements MvcResourceDescriptorResolver {
    public Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handler) {
        // Resolve validated route variables through host-owned services.
    }
}
```

An unannotated route is unchanged. An annotated route returns `401` without an
IAM principal, `403` when the engine denies access, `404` when the host cannot
find the requested resource, and `500` when no resolver bean is registered.
Applications retain ownership of authentication filters, route-to-resource
mapping, and business handlers. Direct `AuthorizationEngine.decide(...)` use
remains available for non-MVC or exceptional flows.

The starter's default `IamAuthorizationFailureHandler` writes an RFC 9457
`ProblemDetail` response. It contains only the HTTP status, a generic title and
detail, the request path, and one stable code: `IAM_UNAUTHENTICATED` (401),
`IAM_ACCESS_DENIED` (403), `IAM_RESOURCE_NOT_FOUND` (404), or
`IAM_RESOURCE_RESOLUTION_UNAVAILABLE` (500). It never returns the bearer token,
principal, permission, resource, scope, or engine-decision details.

An application with an established error envelope can replace the default with
one bean; this changes response formatting only, not interceptor decisions:

```java
@Bean
IamAuthorizationFailureHandler authorizationFailures() {
    return (request, response, status, failure) -> {
        response.setStatus(status);
        // Write the application's safe error envelope using failure.code().
    };
}
```

Run the document and session acceptance suite against temporary MySQL and
Redis containers:

```bash
mvn -pl muer-example -am -Pintegration \
  -Dtest=IamConsumerIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Run it with infrastructure

The application needs MySQL for durable IAM state and Redis for the opaque-
token index. Set the following environment variables for a MySQL database and
Redis instance that contain the generic showcase projection. The Testcontainers
integration command below creates and seeds that projection automatically.

```bash
export MUER_EXAMPLE_JDBC_URL='jdbc:mysql://127.0.0.1:3306/iam_example'
export MUER_EXAMPLE_DB_USERNAME='iam'
export MUER_EXAMPLE_DB_PASSWORD='replace-with-local-password'
export MUER_EXAMPLE_REDIS_HOST='127.0.0.1'
export MUER_EXAMPLE_REDIS_PORT='6379'

mvn -pl muer-example -am spring-boot:run
```

Keep the application running, then set its address in a second shell:

```bash
export MUER_EXAMPLE_BASE_URL='http://127.0.0.1:8080'
```

The commands below use `curl` and `jq`; they retain the token returned by your
own login response in shell variables and never require copying, logging, or
committing an opaque token.

## HTTP walkthrough

### 1. Log in as the reader profile

`POST /iam/auth/login` accepts the demonstration identity only through the
configured `WEB` client. It returns an `accessToken` and `sessionId`.

```bash
LOGIN_RESPONSE="$(curl --fail-with-body -sS \
  -X POST "$MUER_EXAMPLE_BASE_URL/iam/auth/login" \
  -H 'Content-Type: application/json' \
  --data '{"username":"operator-a","password":"demo-pass","clientType":"WEB"}')"
export MUER_EXAMPLE_TOKEN="$(jq -r '.accessToken' <<<"$LOGIN_RESPONSE")"
export MUER_EXAMPLE_SESSION_ID="$(jq -r '.sessionId' <<<"$LOGIN_RESPONSE")"
```

A rejected password or non-`WEB` client returns `401` and creates no session.

### 2. Read the resolved identity and its persisted session

`GET /iam/auth/me` resolves the current principal, while `GET /iam/sessions`
lists that principal's active persisted sessions.

```bash
curl --fail-with-body -sS "$MUER_EXAMPLE_BASE_URL/iam/auth/me" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN"

curl --fail-with-body -sS "$MUER_EXAMPLE_BASE_URL/iam/sessions" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN"
```

The session list includes `MUER_EXAMPLE_SESSION_ID`; MySQL remains the durable
session authority while Redis indexes opaque tokens.

### 3. Read an allowed order and observe a forbidden department scope

`GET /example/orders/9001` returns `200` for `reader-501` because it has READ
scope for department `501`.

```bash
curl --fail-with-body -sS "$MUER_EXAMPLE_BASE_URL/example/orders/9001" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN"
```

`GET /example/orders/9002` returns `403`: the valid identity lacks department
`502` scope.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  "$MUER_EXAMPLE_BASE_URL/example/orders/9002" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN"
# 403
```

### 4. Observe the reader's forbidden approval

`POST /example/orders/9001/approve` returns `403` under `reader-501`, because
that profile has no `order.approve` permission.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST "$MUER_EXAMPLE_BASE_URL/example/orders/9001/approve" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN"
# 403
```

### 5. Switch profile and approve with the replacement token

The seeded approver profile has ID `402`. `POST /iam/authorization/profiles/402/switch`
creates a separate session and returns
a new token; it does not modify the reader token.

```bash
SWITCH_RESPONSE="$(curl --fail-with-body -sS \
  -X POST "$MUER_EXAMPLE_BASE_URL/iam/authorization/profiles/402/switch" \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN")"
export MUER_EXAMPLE_APPROVER_TOKEN="$(jq -r '.accessToken' <<<"$SWITCH_RESPONSE")"
export MUER_EXAMPLE_APPROVER_SESSION_ID="$(jq -r '.sessionId' <<<"$SWITCH_RESPONSE")"

curl --fail-with-body -sS \
  -X POST "$MUER_EXAMPLE_BASE_URL/example/orders/9001/approve" \
  -H "Authorization: Bearer $MUER_EXAMPLE_APPROVER_TOKEN"
```

The approval returns `200`. The original reader token still cannot approve.

### 6. Inspect runtime authorization diagnostics

`POST /iam/authorization/diagnostics` projects the starter's decision; the
example does not recompute permissions or scope. This reader-token request
returns a denied `PERMISSION_DENIED` decision with its decision steps.

```bash
curl --fail-with-body -sS \
  -X POST "$MUER_EXAMPLE_BASE_URL/iam/authorization/diagnostics" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MUER_EXAMPLE_TOKEN" \
  --data '{"permissionCode":"order.approve","applicationCode":"EXAMPLE","clientType":"WEB","resourceType":"ORDER","resourceId":"9001","scopeAccess":"WRITE"}'
```

For an allowed decision, submit `order.read`, resource type `DEPARTMENT`,
resource ID `501`, and scope access `READ` with the same reader token.

### 7. Revoke the replacement session

`POST /iam/sessions/{sessionId}/revoke` invalidates every opaque token for that
session. Reusing the replacement token for `GET /example/orders/9001` then
returns `401`; the separate reader session remains active.

```bash
curl --fail-with-body -sS -o /dev/null -w '%{http_code}\n' \
  -X POST "$MUER_EXAMPLE_BASE_URL/iam/sessions/$MUER_EXAMPLE_APPROVER_SESSION_ID/revoke" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MUER_EXAMPLE_APPROVER_TOKEN" \
  --data '{}'
# 204

curl -sS -o /dev/null -w '%{http_code}\n' \
  "$MUER_EXAMPLE_BASE_URL/example/orders/9001" \
  -H "Authorization: Bearer $MUER_EXAMPLE_APPROVER_TOKEN"
# 401
```

## Verification boundaries

The fast smoke check is container-free and proves a consuming Spring Boot
application discovers the starter:

```bash
mvn -pl muer-example -am -Dtest=IamStarterAutoConfigurationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The real-infrastructure suite requires Docker. It starts isolated MySQL and
Redis Testcontainers, seeds the generic projection, and proves this HTTP
walkthrough including profile switching and revocation:

```bash
mvn -pl muer-example -am -Pintegration -Dtest=IamStarterConsumptionTest,IamSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```
