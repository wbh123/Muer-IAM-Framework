# Generic IAM Target Architecture

## Purpose and delivery form

The deliverable is a reusable Spring Boot library, consumed through one direct
dependency: `iam-spring-boot-starter`. It is not a separately operated IAM
application and does not use the source application's `server/model/client`
layout. Its own Maven reactor is rooted at `iam/`; consuming applications add
the starter and implement narrowly scoped adapters for their users, credentials,
resources and business policies.

```text
iam/
  iam-core/
  iam-authentication/
  iam-authorization/
  iam-session/
  iam-audit/
  iam-diagnostics/
  iam-persistence-mybatis/
  iam-management-web/
  iam-spring-boot-autoconfigure/
  iam-spring-boot-starter/
  iam-example/
  iam-tests/
```

`iam-core` has no Spring, MyBatis, Redis, MVC or application dependency.
`iam-spring-boot-starter` is the only artifact an application normally declares;
it brings the autoconfiguration and selected default implementations.

## Domain model

```text
IamUser --< Identity --< AuthorizationProfile >-- PermissionTemplateVersion --< Permission
                                  |
                                  `--< ResourceScope
```

- `IamUser`: `userId`, `username`, `userType`, `enabled`,
  `authorizationVersion`.
- `Identity`: an enabled user identity in an extensible `IdentityDomain`.
- `Permission`: application-registered atomic `permissionCode`; the framework
  has no fixed permission catalog.
- `Role`: an optional administrative grouping of permissions. It is never an
  enforcement input and no `hasRole` expression grants an operation.
- `PermissionTemplate` and `PermissionTemplateVersion`: editable template
  identity, immutable published revisions and revision-to-permission mappings.
- `AuthorizationProfile`: one user's named, enabled/revocable, client-limited
  and time-bounded binding to one template version. A profile can be default.
- `ResourceScope`: `scopeType`, `scopeRefId`, `accessMode` of `READ` or
  `WRITE`; scope type is application-defined.
- `ResourceDescriptor`: `resourceType`, `resourceId`, `parentPath` and
  attributes supplied by the application.

The `IamPrincipal` embedded in an opaque-token payload contains the user,
identity domain, active profile ID, template version ID, client type and
authorization version. A token does not merge permissions from profiles.

## Authorization engine

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
AuthorizationContext resolve(IamPrincipal principal);
void require(IamPrincipal principal, AuthorizationRequest request);
```

`AuthorizationRequest` includes permission code, identity domain, client type,
resource descriptor and requested scope access. The engine creates a decision
with one step for every stage:

```text
Principal -> account status -> identity -> domain -> active profile
-> profile validity/client -> template version -> atomic permission
-> resource scope -> extension policies -> decision
```

Every `AuthorizationDecisionStep` supplies stable code, title, result,
required value, actual value, reason, source and related ID. Step results are
`PASS`, `FAIL`, `OVERRIDDEN`, `NOT_APPLICABLE` or `WARNING`.

`ResourceScopeResolver` evaluates scope membership using an application
`ResourceHierarchyProvider`; IAM does not know resource types or hierarchy
storage. `AuthorizationPolicy` is the only extension point for business risk,
delegation, compliance and time-window decisions. It can deny or annotate a
decision but cannot bypass the core checks.

## Authentication, token and session model

`IdentityAuthenticator` verifies supplied credentials and returns a generic
authenticated identity. Authentication issues an opaque token through the
`TokenStore` port; authorization later consumes the resolved principal.

`RedisTokenStore` is the starter default. Configurable names are:

```text
{prefix}:token:{token}
{prefix}:token-session:{token}
{prefix}:session-token:{sessionId}
```

It supports save, resolve, revoke token, revoke session, revoke user and an
explicit TTL-refresh capability without requiring Redis in the core modules.
Issued credentials use a fixed absolute expiry by default; ordinary resolution
does not call TTL refresh. User/session indexes avoid keyspace scans for user
revocation.

`AuthSession` records session ID, user, client type and instance, server-derived
IP, user agent, login/last-seen/logout/revocation/expiry state. `LoginEvent`
records successful and failed attempts without credentials or raw tokens.
Session touch is Redis-throttled and updates MySQL only at
`iam.session.touch-interval`.

Authorization-affecting changes (identity, profile, template-permission or
scope) increment `authorizationVersion`; principal resolution rejects a token
whose version is stale. Scope replacement and version increment share one
database transaction in the default MyBatis implementation. Session APIs
support self listing, current/selected revoke, revoke others, revoke user all,
and administrator-forced revocation.

## Persistence and API

`iam-persistence-mybatis` owns MyBatis interfaces and XML plus independent
Flyway migrations:

```text
db/iam/migration/V1__iam_identity.sql
db/iam/migration/V2__iam_permission.sql
db/iam/migration/V3__iam_authorization.sql
db/iam/migration/V4__iam_session.sql
db/iam/migration/V5__iam_audit.sql
db/iam/migration/V6__iam_login_context.sql
```

The starter runs these migrations through an IAM-only Flyway instance and the
`iam_flyway_schema_history` table. Neither the migration location nor history
table overlaps a consuming application's default Flyway configuration.

The initial schema contains `iam_user`, `iam_identity`, `iam_permission`,
`iam_permission_template`, `iam_permission_template_version`,
`iam_template_permission`, `iam_authorization_profile`,
`iam_authorization_scope`, `iam_login_event`, `iam_session`, `iam_audit_log`
and `iam_audit_subject_link`. Mappers contain SQL; growth queries paginate.

`iam-management-web` is OpenAPI-first and contributes `/iam/auth`, `/iam/sessions`,
`/iam/authorization`, `/iam/admin` and authorization-diagnostics endpoints.
It is registered by Starter conditionally, rather than deployed as a separate
server. Diagnostics call the runtime engine and project its exact decision.

## Starter configuration and Spring Security integration

```yaml
iam:
  enabled: true
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  token:
    ttl: 8h
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
  audit:
    enabled: true
  diagnostics:
    enabled: true
  client-types: [WEB, MOBILE]
```

The starter provides the Bearer filter, principal resolver, engine, session
service, login auditing, Redis store and authenticated/anonymous security
integration. It does not own application route allow lists. Controller and
service enforcement use the engine directly (with a future
`@RequirePermission` adapter allowed); role expressions are not a fine-grained
security boundary.

## Adapter migration

The existing application remains live and unchanged during extraction. The
current host bridge maps source users to `IamPrincipal`, authorization-catalog
resources to `ResourceDescriptor`, hierarchy queries to
`ResourceHierarchyProvider`, and source-specific rules to `AuthorizationPolicy`.
One read-only authorization-catalog endpoint can run decision comparison behind
an opt-in shadow switch. Legacy authorization remains authoritative; IAM does
not grant or deny the request. Expansion requires parity evidence first.

## Verification baseline

Every module is test-first. Required coverage includes credential success and
failure, token resolution/TTL/revocation, absent/wrong/expired/disabled profile,
domain mismatch, atomic permission, read/write scopes, stale authorization
version, every session revocation path, direct URL/resource-ID bypass attempts,
single-profile non-union, and equality of runtime versus diagnostic decisions.
The example uses only order and department concepts. A forbidden-term scan over
IAM production sources rejects source-application business vocabulary.
