# Repository Architecture Refactor Baseline

Date: 2026-09-09
Branch: `codex/repository-architecture-refactor`
Base: `origin/main` at `ca9788b`

## Current structure

The current root contains ten framework Maven modules plus `muer-example` and `muer-tests`. The Vue Admin Console is `muer-admin-web`; the Astro documentation site is `muer-docs`. The OpenAPI source is inside `muer-management-web`, so the frontend currently knows the Java module's internal source path.

## OpenAPI baseline

Current path: `muer-management-web/src/main/resources/openapi/iam.yaml`

SHA-256: `48ca623dda277817dad07f6bee903dd338548b1041c12489de962141f8755955`

The contract contains authentication, current-principal, Session, profile, diagnostics, capabilities, and administration operations. The migration must preserve every path, method, operationId, request/response schema, status code, and security declaration.

## Database migration baseline

No migration files are to be added, removed, or edited. Existing files and SHA-256 values:

```text
b3d80a5da24106d9adfa6103a7bf132248e9e2715e6a26f5877e9e598441d652  muer-persistence-mybatis/src/main/resources/db/iam/migration/V1__iam_identity.sql
c3ff70f8dbcde7762fd0fc78070b6969c8e695f609c8cc64dc8ed76602063198  muer-persistence-mybatis/src/main/resources/db/iam/migration/V2__iam_permission.sql
e287c1274b4d5724f903f0db307f292066fc7231e6f8432b7eb387d8b728df84  muer-persistence-mybatis/src/main/resources/db/iam/migration/V3__iam_authorization.sql
5b3211aba0b95856d1b52e6c8b8d6ba2d89f6d5a5a3ca477e0308a04afdf4386  muer-persistence-mybatis/src/main/resources/db/iam/migration/V4__iam_session.sql
b6b4c97b86ded521443597af1e200c37f5d23868ca5d17cae5adb240518e01cc  muer-persistence-mybatis/src/main/resources/db/iam/migration/V5__iam_audit.sql
98ca9c921eaad0309dda1b559a0e519844c137dfafe73c694c1f4d64d3e65929  muer-persistence-mybatis/src/main/resources/db/iam/migration/V6__iam_login_context.sql
```

## `muer-example` classification

Showcase responsibilities: readable document and order examples, permission configuration, identity adapter, resource hierarchy adapter, demo seeders, and human-oriented application/integration tests.

Acceptance responsibilities: public API boundary verification, starter auto-configuration smoke test, Testcontainers authentication/authorization/session/profile flows, and management API acceptance. Acceptance fixtures must be minimal and independently consume the installed Starter.

Quick Start overlap: the existing Quick Start seeder and safety test remain in `examples/quickstart`; they are not copied into Showcase.

Framework-test candidates: tests that only validate framework internals should move to the relevant `modules/*/src/test` or `tests/architecture` rather than either consumer.

## Legacy reference counts before migration

```text
muer-management-web                  36
muer-admin-web                       22
muer-example                         53
muer-docs                            33
muer-tests                            4
src/main/resources/openapi/iam.yaml  19
```

These counts include active source, workflow, script, and documentation references. Historical release-note mentions may remain only when clearly marked as history.
