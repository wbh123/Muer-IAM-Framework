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
bash scripts/test-consumer-public-api.sh
bash scripts/test-docs-workflow.sh
```

The full verification matrix (Starter, Management API, Docker/Testcontainers showcase, independent consumer acceptance, Admin Console, and documentation) runs automatically in GitHub Actions and is authoritative.

## Compatibility

Keep the `muer` configuration prefix stable. Treat changes to public Java packages, Maven groupId, SPI signatures, database migrations, and token semantics as compatibility-sensitive changes.
