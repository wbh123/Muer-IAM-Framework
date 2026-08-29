# Release Policy

## Versioning

- Development work uses `*-SNAPSHOT` versions.
- A release version must be immutable after publication.
- Changes to the Java root package or Maven groupId require a new major version and a migration note.

## Release checklist

1. Update `metadata/project-metadata.yaml` with the target version.
2. Run the identity, legacy-identifier, auto-configuration, and Starter packaging checks.
3. Review generated dependency coordinates in the root POM and Starter POM.
4. Create a tagged release only after a distribution destination is approved.

## Distribution boundary

This repository does not publish artifacts automatically. Package-registry selection, signing, and credentials are explicit release decisions.
