---
title: Permission Registration and Observability Design
description: Approved implementation design for the Muer product-closure branch.
---

# Permission Registration and Observability Design

## Status and scope

This is the approved implementation design for `codex/muer-product-closure`.
It covers permission definition registration and optional runtime observability.
It does not change Muer identity metadata, root Maven metadata, CI workflows,
licenses, release notes, or the root README.

## Permission definitions

Applications declare permissions through a stable consumer SPI in
`muer-authorization`:

```text
PermissionDefinition(code, displayName, description)
PermissionDefinitionProvider
```

The definition matches the descriptive fields of `iam_permission`.  It does
not introduce a synthetic domain field because that column does not exist in
the persistence model.

`PermissionRegistrationService` collects all providers, validates trimmed
non-empty values, maximum lengths, and control characters, and then groups
definitions by code. Identical duplicates are collapsed. Different metadata
for one code fails startup with `Conflicting Muer permission definition: <code>`.

The service depends on an internal `PermissionRepository` port. Its MyBatis
adapter performs an upsert into `iam_permission`: new definitions insert;
existing definitions refresh only display name and description. Definitions
not supplied by the current process are preserved, including their enabled
state. Registration never deletes permissions.

Auto-configuration invokes registration after application readiness, so the
schema migrator has already run. Zero providers remain a valid configuration.
An independent MVC scan of `@RequirePermission` logs a warning when an
annotation references no declared definition. The scan is advisory only;
direct `AuthorizationEngine` consumers remain fully supported.

## Runtime observability

`muer-core` owns the framework-neutral `MuerMetrics` abstraction and its
`NoOpMuerMetrics` implementation. Authentication, authorization, session, and
token-store paths receive this dependency explicitly; no compatibility
constructors are retained because this branch has no existing consumers.

The auto-configuration module provides `MicrometerMuerMetrics` only when
Micrometer is present. It records:

| Metric | Tags |
| --- | --- |
| `muer.authentication.attempts` | `result`, `clientType` |
| `muer.authorization.decisions` | `result`, `decisionCode` |
| `muer.authorization.duration` | none |
| `muer.session.created` | none |
| `muer.session.revoked` | none |
| `muer.token.lookup` | `result` |

No metric uses user identifiers, names, IP addresses, session IDs, token
values, resource identifiers, or permission codes as tags. Metric recording
is guarded so a metrics failure cannot change authentication, authorization,
or session outcomes.

When Actuator is on the classpath, conditional auto-configuration exposes a
`MuerHealthIndicator`. It reports only Muer-specific, non-sensitive readiness
state and does not repeat database or Redis health probes or issue expensive
queries. Without Actuator or a `MeterRegistry`, Muer remains fully usable.

## Verification strategy

Tests will cover provider absence, insertion, idempotency, exact duplicate
deduplication, conflicting definitions, metadata refresh, and preservation of
database-only permissions. Observability tests will cover no-Actuator startup,
conditional health availability, no-op operation, Micrometer counters and
timer results for allow and deny decisions, session changes, and token lookup
outcomes.

The example application will declare document permissions through a provider;
its seed data will retain users, templates, profiles, and scopes but will no
longer seed permissions directly. Public user documentation is written only
after the corresponding types and behavior are implemented and tested.
