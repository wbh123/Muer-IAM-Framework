# Muer Cloud Final Identity Cutover

## Purpose

Before the first stable `0.1.0` artifact, the project will make `cloud.muer` and
`https://muer.cloud` its permanent public identity. The earlier
`io.github.muer` namespace was a transitional Maven/Java coordinate and has not
been released as a stable artifact, so this is the last reasonable breaking
cutover. No compatibility alias or second Maven publication will be provided.

## Canonical identity

- Product: Muer IAM Framework (木耳 Muer)
- Repository: `wbh123/Muer-IAM-Framework`
- Website: `https://muer.cloud`
- Maven group: `cloud.muer`
- Parent: `cloud.muer:muer-parent`
- Starter: `cloud.muer:muer-spring-boot-starter`
- Java root: `cloud.muer`
- Spring prefix: `muer.*`
- Admin project: `muer-admin-web`
- Documentation project: `muer-docs`

## Migration boundary

Maven metadata, Java source/test directories, imports, public API links,
autoconfiguration imports, repository/SCM metadata, active documentation,
frontend/docs directory names, package names, and identity guards move to the
canonical values above. Spring configuration keys remain `muer.*` and runtime
HTTP, database, Redis, and permission contracts remain intentionally IAM-shaped:
`/iam/**`, `iam_*`, `iam.admin.*`, and the default Redis value `iam`.

Historical `docs/superpowers/**` records may retain former literals when they
are explicitly identified as historical design context; scanners must exclude
that history rather than rewriting or deleting it.

## Verification and rollback

Verification must cover identity metadata, absence of legacy runtime/POM/public
documentation matches, physical Java directory moves, generated
autoconfiguration discovery, Maven consumer/public-API tests, admin and docs
type/build checks, and Pages asset smoke checks. The branch must stop at
`READY FOR FINAL MUER 0.1.0 RELEASE REVIEW`; it must not tag, publish, or create
a release. Rollback is a normal Git revert before release; no database rename
migration or compatibility package is required.
