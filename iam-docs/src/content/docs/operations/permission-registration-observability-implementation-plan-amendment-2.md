# Muer Product Closure Implementation Plan Amendment 2

`@RequirePermission` consistency warnings compare a handler's permission code
only with the immutable set of definitions declared by the current
`PermissionDefinitionProvider` beans. They do not use `PermissionRepository`
or `iam_permission` as the source of truth.

`PermissionRegistrationService` exposes that validated, deduplicated set after
registration. The MVC listener emits the prescribed WARN for each annotation
code absent from the set. Database-only and legacy rows are still preserved by
registration, but do not suppress the warning because they are not a current
application declaration.
