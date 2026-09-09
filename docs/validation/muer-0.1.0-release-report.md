# Muer 0.1.0 Release Validation Report

## Candidate

| Item | Value |
| --- | --- |
| Branch | `main` |
| Product candidate SHA | `bd409d9b3da1138a1b4c1b920d12234e09740665` |
| Maven version | `0.1.0-SNAPSHOT` |
| Runtime baseline | Java 21, Spring Boot 4.0.0, MySQL 8.4, Redis 7 |
| Documentation baseline | Node 22+, Astro + Starlight |
| Admin Console baseline | Node 22+, Vue 3 + TypeScript + Vite + Element Plus |
| Tag / publication | Not created |

`bd409d9b` is the product candidate merge commit that brings the complete 0.1.0 scope into `main`.
This report is a documentation-only follow-up and does not change product runtime code.

## 0.1.0 product scope

0.1.0 is defined as the first complete product baseline and contains:

- `muer-spring-boot-starter` and Spring Boot auto-configuration;
- host-owned credential authentication through `IdentityAuthenticator`;
- opaque token issuance, durable MySQL Session state and Redis token index;
- Permission, immutable Permission Template Version, Profile and Resource Scope authorization;
- direct `AuthorizationEngine` use and declarative MVC authorization through `@RequirePermission`;
- Profile Switch with replacement token/session semantics;
- isolated Session revocation;
- Audit and authenticated self Authorization Diagnostics;
- Management API for users, identities, permissions, templates, profiles/scopes, sessions, audit and overview;
- `GET /iam/auth/capabilities` for current-principal capabilities;
- optional `muer-admin-web` Vue 3 Management Console;
- Astro + Starlight documentation site and manual deployment/QuickStart documentation.

The Admin Console is part of the 0.1.0 release scope but is **not** a runtime dependency of applications that only consume the Starter.

## Deployment and QuickStart boundary

End users are not required to install Docker or run the repository's full verification suite.
Supported deployment paths include manually installed or pre-existing MySQL 8.x and Redis 7 services, internal/cloud services, containers, or equivalent managed infrastructure.

The user-facing QuickStart verifies the minimum useful path:

1. configure MySQL and Redis;
2. add the Starter and host adapters;
3. start Spring Boot and complete IAM schema migration;
4. confirm login and current-principal endpoints;
5. confirm at least one allow/deny authorization result;
6. optionally start and manually inspect the Admin Console.

Docker/Testcontainers remain maintainer-side CI tools and are not a production deployment requirement.

## Starter and consumer verification

| Check | Remote result at `bd409d9b` |
| --- | --- |
| Project identity / legacy identifier scan | PASS |
| Spring Boot auto-configuration | PASS |
| Consumer Starter smoke | PASS |
| Consumer showcase documentation | PASS |
| Starter package | PASS |
| Management API tests | PASS |
| Consumer public API boundary | PASS |
| Independent Consumer acceptance | PASS |
| MySQL + Redis Testcontainers showcase | PASS |
| Admin demo seeder isolation | PASS |

The Admin Demo Seeder isolation regression specifically verifies that a non-demo permission such as `iam.admin.extension.read` survives demo reseeding and is not attached to the demo Admin Template. The Seeder now deletes and links only the explicit canonical permission set in `AdminDemoSeedConstants`.

## Admin Console verification

The optional Management Console uses the OpenAPI contract as the Java/TypeScript API boundary.

| Check | Remote result at `bd409d9b` |
| --- | --- |
| `npm ci` | PASS |
| TypeScript client generation from `iam.yaml` | PASS |
| Vue/TypeScript type check | PASS |
| Vitest | PASS |
| Vite production build | PASS |

Security and compatibility boundaries reviewed before merge:

- management routes use current-principal capabilities only as a frontend usability guard;
- every `/iam/admin/**` backend operation remains authorized by `AuthorizationEngine`;
- no role/super-admin bypass was introduced;
- `/account` requires authentication but not an admin capability;
- `POST /iam/authorization/diagnostics` remains authenticated **self diagnostics**, not admin-only;
- browser session data uses `sessionStorage`; no repository use of `localStorage`, `v-html`, or `console.log(token)` was found in final review;
- management Session responses do not expose raw tokens or Redis keys;
- logout calls the backend before local session cleanup;
- production creates no default administrator and exposes no public admin-bootstrap endpoint.

## Administrator bootstrap

`muer-example` provides an explicit development-only administrator for manual Console acceptance only when **both** conditions hold:

```text
SPRING_PROFILES_ACTIVE=dev
IAM_EXAMPLE_SEED_ADMIN=true
```

Demo credentials are `admin-demo / demo-pass / WEB` and must never be copied into production.

Production first-administrator provisioning is deployment-owned: controlled SQL/migration/deployment seeding or the host application's own initial-provisioning process must establish the first IAM Admin Template/Profile/Scope. Subsequent administration can be performed through the Management Console.

## Documentation verification

| Check | Remote result at `bd409d9b` |
| --- | --- |
| Docs workflow configuration guard | PASS |
| `npm ci` | PASS |
| Astro type check | PASS |
| Astro static build | PASS |

Repository and documentation-site QuickStart material now consistently describes manual MySQL/Redis configuration as the primary path, Docker as optional, and HTTP examples by request method/path/body/expected result rather than requiring curl/jq-based walkthroughs.

## Remote CI evidence

All three main-branch workflows passed for the product candidate merge commit `bd409d9b3da1138a1b4c1b920d12234e09740665`.

| Workflow | Run ID | Head SHA | Conclusion | Important jobs |
| --- | --- | --- | --- | --- |
| Verify Muer Starter | `33970825245` | `bd409d9b` | success | `verify`, `Verify Muer Management API`, `Independent Consumer acceptance`, `Docker/Testcontainers consumer showcase` |
| Verify Muer Admin Console | `33970825261` | `bd409d9b` | success | OpenAPI generation, type check, tests, production build |
| Verify Muer Documentation | `33970825217` | `bd409d9b` | success | workflow guard, install, Astro check, Astro build |

The same feature head `03e723ff` also passed all three PR workflows before PR #2 was merged into `main`.

## Findings

| Priority | Finding | Status |
| --- | --- | --- |
| P0 | No release-blocking software correctness or security issue recorded after final review. | Closed / none |
| P1 | No `LICENSE` file existed; license metadata was intentionally omitted rather than invented, which blocked public open-source / Maven Central distribution. | Resolved — Apache License 2.0 added (`LICENSE`, root POM `<licenses>`, README and docs). Final public-distribution readiness still gated on a passing Muer CI baseline. |
| P2 | `main` currently has no enforced branch protection / required status checks. | Open — repository governance improvement |
| P2 | GitHub Actions still use v4 checkout/setup actions that have deprecation/runtime notices; upgrade can be handled separately without changing 0.1.0 product semantics. | Open — technical debt |
| P3 | Browser-level Playwright end-to-end coverage for the Admin Console is deferred; backend integration, OpenAPI generation, frontend unit/component tests, type checking and production build are present. | Deferred |

## Release state

**Software release candidate**: `READY FOR HUMAN RELEASE APPROVAL`.

The product candidate is merged into `main`, the Starter/Management/Consumer/Admin Console/Documentation verification matrix is green, the compatibility regression around self diagnostics was corrected, and the Admin Demo Seeder is explicitly scoped to demo-owned permissions.

**Public Maven / open-source distribution**: pending. The repository owner has chosen **Apache License 2.0**; a `LICENSE` file and matching Maven license metadata are now in place. The distribution gate will be re-evaluated once the Muer migration baseline (this branch) is green in CI.

The repository intentionally remains `0.1.0-SNAPSHOT`. No `v0.1.0` tag, Maven publication, or GitHub Release has been created yet.
