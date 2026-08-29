# Contributing

## Branches

- `main` contains reviewed, releasable snapshots.
- Feature work starts from `develop/iam-starter-next` or a current development branch.
- Changes that affect Maven coordinates or Java packages must include migration-tool coverage.

## Required checks

Run before opening a pull request:

```powershell
./scripts/test-identity-check.ps1 -RepositoryRoot $PWD
./scripts/test-rename-project.ps1 -RepositoryRoot $PWD
```

Then run the focused Spring Boot auto-configuration test and package the Starter using the commands in the GitHub Actions workflow.

## Compatibility

Keep the `iam` configuration prefix stable. Treat changes to public Java packages, Maven groupId, SPI signatures, database migrations, and token semantics as compatibility-sensitive changes.
