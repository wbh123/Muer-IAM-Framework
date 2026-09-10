# Contributing

## Branches

- `main` contains reviewed, releasable snapshots.
- Feature work starts from `develop/iam-starter-next` or a current development branch.
- Changes that affect Maven coordinates or Java packages are compatibility-sensitive and must be reviewed as a breaking change.

## Required checks

Run before opening a pull request:

```powershell
./scripts/test-identity-check.ps1 -RepositoryRoot $PWD
```

```bash
bash scripts/test-muer-identity.sh
bash scripts/verify-consumer-public-api.sh test-apps/consumer-acceptance/src/main/java
bash scripts/test-docs-workflow.sh
bash scripts/verify-documentation-contract.sh
bash scripts/verify-repository-layout.sh
```

## Repository map

Keep each concern in its designated top-level directory:

| Directory | Placement rule |
| --- | --- |
| `modules/` | Published framework modules and Spring Boot starter implementation |
| `contracts/` | Shared OpenAPI and other machine-readable contracts |
| `apps/` | Optional Admin Console and documentation site; never Maven reactor modules |
| `examples/` | Human-facing Quick Start and Showcase consumers |
| `test-apps/` | Independent CI consumers that verify the public Starter API |
| `tests/` | Non-published architecture and repository-boundary tests |
| `docs/` | Maintainer, release, and validation records |
| `metadata/` | Replaceable project identity and publication metadata |
| `scripts/` | Repository verification and maintenance tooling |

Place core/SPI, authentication, authorization, session, audit, diagnostics,
persistence, HTTP, auto-configuration, and starter code under `modules/`.
Put the canonical OpenAPI file at `contracts/openapi/iam.yaml`. New consumer
examples belong under `examples/`; CI-only third-party integration belongs
under `test-apps/`; architecture assertions belong under `tests/architecture`.

The full verification matrix (Starter, Management API, Docker/Testcontainers Showcase, independent Consumer acceptance, Admin Console, and documentation) runs automatically in GitHub Actions and is authoritative.

## Compatibility

Keep the `muer` configuration prefix stable. Treat changes to public Java packages, Maven groupId, SPI signatures, database migrations, and token semantics as compatibility-sensitive changes.
