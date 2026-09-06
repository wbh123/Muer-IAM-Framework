# IAM Migration Guide

## Boundary

Extraction does not replace a live application's security implementation in
one change. The generic starter is introduced beside the existing path, and
each host endpoint changes authority only after decision parity is measured.
The source application's tables, tokens and routes remain authoritative until
that endpoint is explicitly migrated.

## Ownership and release boundary

The consuming application owns credential verification, its business-resource
lookup, endpoint rollout, rollback selector, and all host data migration.
The starter owns only its generic IAM modules, its isolated migrations from
`classpath:db/iam/migration`, and the `iam_flyway_schema_history` configured
for those migrations. Do not treat default MyBatis mappers, migration objects,
or IAM table layout as consumer extension APIs; the supported consumer boundary
is listed in [PUBLIC_API.md](PUBLIC_API.md).

The current candidate remains `0.1.0-SNAPSHOT`. It is not a published IAM
service and this migration guide does not authorize replacing a host's existing
security path in one change.

## Adapter map

| Host responsibility | IAM adapter or model |
| --- | --- |
| Credential verification | `IdentityAuthenticator` |
| Authenticated user projection | `IamPrincipal` |
| Resource lookup and ancestry | `ResourceDescriptor`, `ResourceHierarchyProvider` |
| MVC route permission declaration | `@RequirePermission`, `MvcResourceDescriptorResolver` |
| Additional risk or compliance rules | `AuthorizationPolicy` |
| Existing permission assignment | `PermissionTemplateVersion` |
| Current assignment and validity | `AuthorizationProfile` |
| Data boundary | `ResourceScope` |

Adapters belong to the consuming application. Generic IAM modules must not
depend on host entities, mappers or vocabulary.

## Staged migration

1. Publish the starter without enabling it in production.
2. Project users, identities, templates, profiles and scopes into IAM tables.
3. Implement credential and resource-hierarchy adapters.
4. Run the existing authorization path and either `AuthorizationEngine.decide`
   or `@RequirePermission` in shadow mode for one read-only endpoint.
5. Compare allowed/denied outcomes and diagnostic step codes. Resolve every
   mismatch before changing enforcement authority.
6. Enable IAM enforcement for that endpoint while retaining an immediate
   rollback switch.
7. Expand endpoint by endpoint; never merge permissions from multiple active
   profiles.
8. Retire legacy code only after every consumer has migrated and rollback is
   no longer required.

## Data migration rules

- Keep source identifiers as explicit external references; do not infer them
  from display names.
- Bind every profile to one immutable template version.
- Preserve validity windows, enabled/revoked state and client eligibility.
- Convert each data boundary into separate READ and WRITE scopes.
- Initialize the IAM authorization version from the current source value.
- Never copy credentials, passwords, raw tokens or prohibited device values.

IAM uses migrations from `classpath:db/iam/migration` and its own
`iam_flyway_schema_history`. This keeps host migration numbering independent.

## Declarative MVC enforcement

For Servlet MVC handlers, a host may replace repeated controller-level engine
calls with the starter's public web API:

```java
@RequirePermission(value = "invoice:approve", access = ScopeAccess.WRITE)
@PostMapping("/api/invoices/{id}/approve")
ResponseEntity<Invoice> approve(@PathVariable String id) { /* host logic */ }
```

The host registers one `MvcResourceDescriptorResolver` that converts validated
route variables into a host-owned `ResourceDescriptor`. The starter evaluates
that descriptor with the current `IamPrincipal`; it does not discover or model
host entities. Unannotated routes are unaffected. Annotated requests return
`401` without a principal, `403` on engine denial, `404` when the resolver
cannot find the requested resource, and `500` when no resolver is registered.
Direct engine invocation remains the correct option outside MVC or when a
single request needs exceptional authorization flow.

The default `IamAuthorizationFailureHandler` writes a safe `ProblemDetail`
body with a generic title/detail, request path, and one stable code:
`IAM_UNAUTHENTICATED`, `IAM_ACCESS_DENIED`, `IAM_RESOURCE_NOT_FOUND`, or
`IAM_RESOURCE_RESOLUTION_UNAVAILABLE`. A consuming application may replace
that handler with its own bean to adopt a shared error envelope. Replacement
must not disclose bearer tokens, principals, permissions, resources, scopes,
or authorization decision details.

## Rollback

Rollback changes only the endpoint's enforcement selector back to the existing
path. Do not delete IAM tables or revoke source tokens during an endpoint
rollback. Keep audit and comparison evidence so the mismatch can be diagnosed.

## Current extraction status

This repository's `muer-example` is the current consumer demonstration. It
verifies generic login, scoped document access, denied requests, profile switch,
diagnostics, and session revocation. A real host must still establish its own
decision-parity evidence before switching enforcement authority for any
endpoint.
