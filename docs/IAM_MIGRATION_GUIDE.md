# IAM Migration Guide

## Boundary

Extraction does not replace a live application's security implementation in
one change. The generic starter is introduced beside the existing path, and
each host endpoint changes authority only after decision parity is measured.
The source application's tables, tokens and routes remain authoritative until
that endpoint is explicitly migrated.

## Adapter map

| Host responsibility | IAM adapter or model |
| --- | --- |
| Credential verification | `IdentityAuthenticator` |
| Authenticated user projection | `IamPrincipal` |
| Resource lookup and ancestry | `ResourceDescriptor`, `ResourceHierarchyProvider` |
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
4. Run the existing authorization path and `AuthorizationEngine.decide` in
   shadow mode for one read-only endpoint.
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

## Rollback

Rollback changes only the endpoint's enforcement selector back to the existing
path. Do not delete IAM tables or revoke source tokens during an endpoint
rollback. Keep audit and comparison evidence so the mismatch can be diagnosed.

## Current extraction status

The generic starter and example remain isolated from host vocabulary. The host
now contains a narrow bridge for the authorization-diagnostics catalog:
principal, resource, hierarchy and policy adapters compare the legacy and IAM
decisions for the same operator. The comparison is disabled by default through
`iam.shadow.authorization-catalog.enabled` and records mismatches without
changing the response or enforcement result. No existing endpoint has switched
to IAM enforcement.
