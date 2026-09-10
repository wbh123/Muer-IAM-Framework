# Muer 0.1.0 Release Candidate Validation Report

## Candidate baseline

| Item | Value | Status |
| --- | --- | --- |
| Candidate branches | `main`, `release/0.1.0`, `release-prep/maven-central-0.1.0` | `main` and `release/0.1.0` are aligned |
| Framework/runtime candidate SHA | `9d9d16611430765dcee4d36d9f922007fb5a9afb` | Verified locally and with `git ls-remote` |
| Release-guard follow-up SHA | `c3dacba0e96683239129f5fb262668091c8c5ebd` | Fixes a false positive for the documented `.env.example`; no runtime changes |
| Maven version | `0.1.0-SNAPSHOT` | Intentionally unchanged; no tag or release created |
| Java / Spring Boot | Java 21 / Spring Boot 4.0.0 | PASS |
| Runtime dependencies | MySQL 8.4 / Redis 7 | PASS in Testcontainers validation |
| Documentation | Node 22+, Astro + Starlight | PASS |
| Admin Console | Vue 3, TypeScript, Vite, Element Plus | PASS |
| Release mode | RC validation only | No Central upload, GitHub Release, or `v0.1.0` tag |

The release-preparation change is limited to Maven publication configuration,
artifact guards, a manual-only workflow, and release evidence. It does not
change authentication, authorization, persistence behavior, OpenAPI content,
Flyway migrations, HTTP routes, or frontend runtime behavior.

## Product and repository scope

The 0.1.0 candidate contains the Muer Spring Boot starter, authentication,
opaque sessions, authorization and profile switching, audit and diagnostics,
MyBatis persistence, the generated HTTP API, optional Management Console, and
Astro/Starlight documentation. The published consumer entry point remains
`cloud.muer:muer-spring-boot-starter`.

The repository keeps the framework modules separate from examples, the
architecture test module, the optional web applications, and consumer
acceptance projects. Only the ten framework modules plus the parent POM are
intended for Maven Central; tests, examples, apps, and test apps are excluded.

## Framework and consumer validation

| Check | Result | Evidence |
| --- | --- | --- |
| Root Maven reactor `mvn -B clean verify` | PASS | All 12 reactor modules; MySQL/Redis Testcontainers included |
| QuickStart consumer | PASS | 3 tests |
| Independent consumer acceptance | PASS | 2 tests |
| Consumer public API boundary | PASS | `CONSUMER_PUBLIC_API_VERIFIED` |
| Showcase integration | PASS | 23 tests with MySQL/Redis Testcontainers |
| Starter runtime dependency tree | PASS | No test-app, architecture, or frontend dependency leakage |
| Repository layout / docs / identity guards | PASS | All repository guard scripts passed |
| Admin Console build and checks | PASS | Covered by remote workflow below |
| Documentation build and checks | PASS | Covered by remote workflow below |

The local runs emitted only expected Testcontainers teardown/Lettuce reconnect
warnings. They did not produce test failures. The local Maven mirror stalled
while downloading `maven-javadoc-plugin:3.11.2`; therefore the release profile
artifact attachment is not claimed as locally passed.

## Remote CI evidence

All four push workflows completed successfully on the framework/runtime
candidate SHA
`9d9d16611430765dcee4d36d9f922007fb5a9afb`:

| Workflow | Run | Conclusion | Key jobs |
| --- | --- | --- | --- |
| Verify Muer Starter | [34450531437](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450531437) | success | `verify`, Management API, Docker/Testcontainers showcase, Independent Consumer acceptance |
| Verify Muer Documentation | [34450531466](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450531466) | success | Documentation install, check, and build |
| Verify Muer Admin Console | [34450531575](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450531575) | success | Admin Console production validation |
| Publish Muer Documentation | [34450531454](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450531454) | success | Documentation deployment job |

The same Starter and Documentation workflows also completed successfully for
the synchronized `release/0.1.0` push ([Starter
34450533290](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450533290),
[Documentation
34450533465](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450533465)).
The final report-only commit does not alter runtime or publication configuration;
the listed runs therefore remain the validation evidence for the code candidate.

The runs report GitHub Actions warnings that `actions/checkout@v4`,
`actions/setup-java@v4`, and related actions target the deprecated Node 20
runtime. This is non-blocking technical debt and is independent of the Muer
runtime contract.

## Contract, database, and security checks

- `/iam/**`, `iam_*`, `iam.admin.*`, and Redis value namespace `iam` remain
  unchanged compatibility contracts.
- OpenAPI operation identifiers and generated Java/TypeScript boundaries remain
  unchanged.
- The six Flyway migrations and their checksums remain unchanged.
- The Starter still requires host-owned credentials and does not create a
  production administrator or expose a public bootstrap endpoint.
- No credentials, private keys, `settings.xml`, `.env`, or generated secrets
  were added to tracked files.

## Maven Central publication readiness

| Gate | Result | Interpretation |
| --- | --- | --- |
| Main JAR and POM | PASS | Ordinary Maven build produces the reactor artifacts and metadata |
| Sources JAR | NOT LOCALLY VERIFIED | `central-release` profile is configured; local mirror stalled downloading the source/Javadoc toolchain |
| Javadoc JAR | NOT LOCALLY VERIFIED | Same network/toolchain limitation; no successful dry-run evidence yet |
| GPG signing configuration | READY / OWNER ACTION REQUIRED | `maven-gpg-plugin` is configured; the private key and passphrase are intentionally absent |
| Central Portal plugin | CONFIGURED | `central-publishing-maven-plugin:0.11.0` is pinned in the separate `central-publish` profile |
| Central namespace `cloud.muer` | OWNER ACTION REQUIRED | Namespace ownership must be verified in Central Portal/DNS; code cannot prove it |
| Central credentials | OWNER ACTION REQUIRED | `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` must be configured as GitHub Secrets |
| Manual dry-run workflow | FAILED THEN FIXED / RERUN PENDING | Run [34450935438](https://github.com/wbh123/Muer-IAM-Framework/actions/runs/34450935438) reached Maven staging but the artifact guard incorrectly rejected `examples/quickstart/.env.example`; the guard fix is in `c3dacba`, and a post-fix run is still required |
| Formal Central upload | NOT EXECUTED | Explicitly out of scope for this RC round |

The repository now follows the official [Central Portal Maven publishing
flow](https://central.sonatype.org/publish/publish-portal-maven/), including
the required [publication artifacts and metadata](https://central.sonatype.org/publish/requirements/).
The `cloud.muer` namespace remains an owner-only item under the [namespace
registration process](https://central.sonatype.org/register/namespace/).

## Release blockers and recommendation

Software P0/P1 blockers: **0**. The first dry-run exposed and fixed a P2
artifact-guard false positive; its post-fix rerun is still open. Owner-only
release setup is also incomplete:
namespace verification, Central token secrets, a published GPG public key,
and a successful manual dry-run that verifies Sources/Javadoc/signatures.
These are not defects in runtime behavior, but they prevent an evidence-backed
Maven Central release decision today.

**Recommendation: NOT READY FOR VERSION FREEZE.**

The candidate is code-ready for the final release exercise, but version freeze
must wait for the owner to complete Central setup and for the manual dry-run to
produce a successful artifact-bundle report. Until then, keep
`0.1.0-SNAPSHOT`, do not create `v0.1.0`, and do not upload to Central.
