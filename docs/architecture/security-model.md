# Muer Security Model

Internal maintainer reference for the Muer authorization and token trust model.
The user-facing summary lives in
[`apps/muer-docs-site` operations/security-model](../../apps/muer-docs-site/src/content/docs/operations/security-model.md).

## Trust boundaries

Credential verification belongs to the consuming application. IAM trusts only
the `IamPrincipal` returned by a registered `IdentityAuthenticator`; request
parameters never select the current user, profile, domain or authorization
version. MySQL is the durable authorization and session authority. Redis stores
opaque token projections and reverse indexes, not the final permission model.

## Authorization invariants

Every decision evaluates, in order:

```text
Bearer principal -> authorization version -> identity domain
-> one active profile -> profile client and validity
-> immutable template version -> atomic permission
-> READ/WRITE resource scope -> extension policies
```

- A role name never grants access directly.
- A session activates exactly one authorization profile.
- Profiles are never unioned to produce a larger permission set.
- Domain and permission must both match.
- Scope type is application-defined and evaluated through a host hierarchy
  provider.
- The default hierarchy provider denies every scoped request.
- Diagnostics return the runtime engine's decision and do not reimplement it.

## Token and session controls

Tokens are random opaque values stored in Redis with configurable, fixed
absolute TTL and prefix. Resolution does not silently extend that expiry.
Reverse indexes support token, session and user revocation without keyspace
scans. MySQL stores the session ID but never the raw token. A token is accepted
only when its embedded authorization version equals the current durable
version; authorization changes therefore invalidate older tokens.

Token issuance writes Redis and then the durable session. If session creation
fails, the issued Redis token is revoked as compensation. If the subsequent
login-event append fails, both the token and durable session are revoked before
the audit failure is returned.

Successful token resolution attempts to acquire a short-lived, per-session
Redis lease. Only the lease holder updates the durable `last_seen_at` value,
so request volume does not become MySQL write volume. A projection-only touch
failure does not reject an otherwise valid token. Session or user revocation
also removes related token indexes and touch leases.

## HTTP controls

The starter security chain matches only `/iam/**`, is stateless, disables CSRF
for Bearer endpoints and permits only the login route anonymously. Its Bearer
filter is deliberately disabled as a global servlet filter. A consuming
application explicitly inserts the same filter into security chains for its
own protected routes, preventing the starter from changing unrelated host
routes.

Administration controllers call `AuthorizationEngine` with atomic IAM
administration permissions before mutation. Client-supplied identifiers do not
replace the authenticated principal.

User listing is cursor-paginated and bounded to 100 records. User and identity
governance routes require separate atomic administration permissions. Identity
ownership cannot be transferred by updating an existing identity, and every
identity mutation increments the durable authorization version so older tokens
fail on their next resolution.

## Privacy and audit

Passwords and raw tokens are never persisted in IAM tables or audit metadata.
Hardware serials, network interface identifiers, advertising identifiers and
precise location values are prohibited. Audit records use generic operator,
action, resource, result, request, before/after and subject-link fields.

Every completed credential decision appends a dedicated login event with an
opaque event ID, attempted identity key, client type, server-derived remote IP,
user agent, optional coarse client labels, request ID, result and timestamp.
The generic controller deliberately does not trust `X-Forwarded-For`; trusted
proxy resolution belongs to host infrastructure. Client-controlled metadata is
stripped of control characters and bounded to its persistence column width.
Successful events also bind the durable user, identity domain and session;
rejections and authenticator failures carry a bounded reason code. Credential
secrets are never passed into the event model.

## Failure behavior

- Missing or invalid Bearer token: unauthenticated.
- Domain, permission, profile, version, scope or policy mismatch: deny.
- Missing hierarchy adapter: deny.
- Persistence failure during token issuance: revoke the partial token.
- Persistence failure after a successful login event attempt: revoke the
  issued token rather than return an unaudited credential.
- Diagnostics failure or absence: never grants access.
- Redis loss: token resolution fails closed; it does not fall back to a
  client-provided principal.
