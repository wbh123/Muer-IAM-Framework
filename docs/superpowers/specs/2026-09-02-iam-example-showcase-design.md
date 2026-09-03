# IAM Example Showcase Design

## Goal

Turn the existing `iam-example` Spring Boot consumer into a complete, generic
showcase for integrating `iam-spring-boot-starter`. It must demonstrate the
framework through runnable HTTP scenarios without introducing school-specific
language, data, database migrations, publishing credentials, or a second IAM
deployment layer.

## Decision

Extend `iam-example` instead of creating a second consumer module. The module
already depends only on `iam-spring-boot-starter`, has a host-owned business
security chain, and contains both a fast container-free startup check and an
explicit MySQL/Redis integration path. Keeping one consumer prevents divergent
examples and makes the README, runtime walkthrough, and tests describe the
same application.

## Showcase domain and data

The host domain remains deliberately anonymous: orders belong to departments.
Two identities, two authorization profiles, and two departments demonstrate
the framework boundary without modeling real people or organizations.

| Identity | Client | Active profile | Effective behavior |
| --- | --- | --- | --- |
| `operator-a` | `WEB` | `reader-501` | Read order `9001`; cannot approve it or read department `502`. |
| `operator-a` | `WEB` | `approver-501` | Read and approve order `9001`; still cannot access department `502`. |
| `operator-b` | `WEB` | `reader-502` | Read order `9002`; cannot access department `501`. |

The demonstration only accepts the configured `WEB` client. A request for an
unconfigured client must receive the same unauthenticated result as rejected
credentials and must not invoke the host `IdentityAuthenticator`.

## Architecture

`IamExampleApplication` is the host application. Its `IdentityAuthenticator`
maps demo credentials to an `IamPrincipal`; its
`ExampleResourceHierarchyAdapter` maps an order to a parent `DEPARTMENT`
resource; and `ExampleSecurityConfiguration` applies the starter Bearer filter
to `/example/**`. The starter owns opaque tokens, sessions, IAM HTTP routes,
profiles, permission templates, scope evaluation, audit and diagnostics.

The real-infrastructure suite seeds only generic IAM tables through a dedicated
test fixture. It starts MySQL and Redis with Testcontainers and verifies HTTP
responses. Fast checks remain container-free: `ApplicationContextRunner` tests
the starter auto-configuration and `IamStarterAutoConfigurationSmokeTest`
starts a minimal consumer context.

## HTTP walkthrough

The example README will contain a copyable sequence using a returned opaque
token:

1. `POST /iam/auth/login` as `operator-a` with `WEB`.
2. `GET /iam/auth/me` and `GET /iam/sessions` to show the resolved principal
   and persisted session.
3. `GET /example/orders/9001` succeeds; `GET /example/orders/9002` returns
   `403` because the department scope differs.
4. `POST /example/orders/9001/approve` returns `403` under `reader-501`.
5. `POST /iam/authorization/profiles/{approverProfileId}/switch` returns a new
   token; approval with that new token succeeds.
6. `POST /iam/authorization/diagnostics` shows the runtime decision steps for
   the allowed and denied requests.
7. `POST /iam/sessions/{sessionId}/revoke` invalidates the token; subsequent
   business requests return `401`.

No raw token is logged or inserted into MySQL. The README labels every
credential as demonstration-only.

## Error and security behavior

- Bad credentials or disallowed client types return `401` and issue no token.
- A missing token returns `401`; a valid token lacking a permission or scope
  returns `403`; an unknown order returns `404` only after authentication.
- Switching a profile creates a new token and session context; it does not
  mutate the token already in use.
- Session revocation and an authorization-version change invalidate the token
  on subsequent resolution.
- MySQL is the durable source for sessions, profiles and permissions; Redis is
  an opaque-token index and reverse lookup, never the final authorization
  authority.

## Acceptance criteria

- The module is still consumable through only `iam-spring-boot-starter`.
- One container-free test proves starter discovery; one Testcontainers suite
  proves the complete HTTP walkthrough against MySQL and Redis.
- The walkthrough covers authentication, client filtering, permissions,
  scopes, profile switching, sessions, audit/diagnostics and revocation.
- All example data and prose remain generic and have no legacy identifiers.
- Daily CI runs the fast checks; real-infrastructure tests run in a dedicated
  Maven profile so Docker availability is an explicit prerequisite.
