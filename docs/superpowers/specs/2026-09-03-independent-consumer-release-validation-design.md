# Independent Consumer Release Validation Design

## Goal

Turn `iam-example` into a reproducible independent-consumer acceptance target
that proves a generic Spring Boot application can adopt
`iam-spring-boot-starter` without any legacy business code or IAM private
implementation dependency.

## Scope and boundary

The consumer remains the existing `iam-example` Maven module. It is a
standalone Spring Boot 4 / Java 21 application with generic `Document`,
`Department`, and `Project` resources. It does not reference the original
application, database, configuration, names, or packages.

Its sole IAM production dependency remains `iam-spring-boot-starter`.
Standard host dependencies may include Spring MVC/Security, the MySQL driver,
and test-only Testcontainers. The consumer must not import IAM packages named
`internal`, `impl`, persistence implementation classes, or generated
infrastructure types beyond the public HTTP contract already exposed by the
starter.

## Consumer architecture

The consumer owns the following responsibilities:

- an `AppUser` projection with its own identifiers and account status;
- an `IdentityAuthenticator` that validates host credentials and returns the
  public `IamPrincipal` projection;
- a `ResourceHierarchyProvider` mapping `Document` resources to generic
  `Project` and `Department` parents;
- a host `SecurityFilterChain` that permits `/public/**` and protects
  `/api/**` with the starter bearer-token filter;
- document endpoints that ask the public `AuthorizationEngine` for a decision
  rather than comparing roles in controllers.

The starter continues to own opaque token issuance and resolution, sessions,
profile switching, authorization evaluation, audit, diagnostics, MySQL IAM
schema migration, and Redis-backed token indexing.

## Acceptance data and workflows

Test-only MySQL data uses generic principals and profiles:

| Principal | Active profile | Expected capability |
| --- | --- | --- |
| `author-a` | `admin-project-101` | Read and modify documents in project 101. |
| `author-a` after switch | `reader-project-101` | Read project 101; modification denied. |
| `reader-b` | `reader-project-202` | Read only project 202. |

The integration suite starts real MySQL 8.4 and Redis containers. It verifies
empty-schema startup, host identity authentication, missing/invalid/revoked
token rejection, permission and resource-scope enforcement, profile switching,
single-session revocation isolation, multiple sessions, Redis token state, and
MySQL session/profile persistence. Test names and assertions form the public
acceptance record; no opaque token is inserted into a fixture, log, or README.

## Configuration and failure behavior

The module supplies a minimal `application.yaml`: datasource, Redis, enabled
IAM schema, dedicated Flyway history table, positive token TTL, non-empty
client-type allow-list, and a consumer-specific Redis prefix. Configuration
tests must demonstrate clear startup failures for invalid TTL, missing or
blank Redis prefix, blank client type, and deliberately unreachable Redis in
an integration configuration. The consumer must not hide Docker absence: the
`integration` Maven profile explicitly runs container tests and fails if
Testcontainers cannot connect.

HTTP tests assert status semantics: unauthenticated/invalid/revoked is `401`,
authenticated but unauthorized is `403`, and an absent resource is `404` only
after authentication. Responses must not disclose Java stack traces, SQL, IAM
internal classes, or raw token data.

## CI, API boundary, and documentation

`scripts/verify-consumer-public-api.sh` scans consumer source imports and
fails for IAM private implementation namespaces. A separately named GitHub
Actions `consumer-integration` job builds the reactor, runs the script, and
executes the explicit Docker/Testcontainers Maven profile. Existing fast jobs
remain container-free.

The root README gains a short “5-minute integration” guide derived from the
consumer's executable configuration, identity adapter, resource adapter, and
HTTP workflow. The `iam-example` README remains the detailed walkthrough.

## Release decision

Candidate release requires all fast checks, consumer boundary scan, and
container integration tests to pass locally and in GitHub Actions on the same
branch SHA. A failure showing that the consumer needs an IAM private class or
cannot start with only starter-provided runtime services is P0. Authentication,
authorization, token, scope, profile, or session failures are P1 and must be
fixed before release. Documentation and ergonomics gaps are P2; style-only
items are P3.
