# IAM Implementation Inventory

> Source repository: `Wust-Dormitory-Select` at branch point `main`.
>
> Purpose: identify reusable IAM capabilities before extraction. This document
> describes source-system facts; it is not part of the generic framework API.

## Authentication and principal propagation

| Source area | Existing responsibility | Extraction disposition |
| --- | --- | --- |
| `auth/AuthService` | Password authentication, account activation, password change, web token issuance and admin-profile binding | Split credential verification behind an `IdentityAuthenticator` port from generic token/session issuance. Activation and student-specific messages remain application code. |
| `platform/PlatformAuthService` | Privileged platform login | Map to a client-specific application adapter; no platform role is retained in IAM Core. |
| `mobile/MobileAuthService` | Mobile administrator login | Reuse the client-context and login-audit flow; mobile workflow constraints stay outside IAM. |
| `security/AuthTokenService` | Redis-backed opaque token, eight-hour sliding TTL, local cache invalidation and token/session reverse mapping | Extract as configurable `RedisTokenStore`; eliminate fixed prefix and fixed TTL. Replace key scans for user revocation with a user/session index. |
| `mobile/MobileAuthTokenService` | Parallel mobile opaque-token implementation | Consolidate into the shared `TokenStore`; distinguish clients through configured `ClientType`, not separate services. |
| `security/BearerTokenFilter` / `MobileBearerTokenFilter` | Parse Bearer token and install Spring Security authentication | Extract a client-neutral filter. Selection latency logging and endpoint naming remain application observability. |
| `security/CurrentUser` | Current principal plus student and administrator fields | Replace with `IamPrincipal`; student, account-domain role checks and dorm-staff helpers become adapters/policies. |
| `security/SecurityConfig` / `MobileSecurityConfig` | Stateless Spring Security chain and route gates | Starter supplies authenticated/anonymous configuration. Applications own route allow lists and invoke `AuthorizationEngine` for fine-grained enforcement. |

## Authorization kernel

`AdminAuthorizationService` resolves an administrator context from the active
profile, checks account versus business domain, applies atomic permissions and
returns explainable decisions. `AdminAuthorizationContext` holds permissions,
profile metadata and scopes. This is the primary source for extraction.

Reusable capabilities:

- a domain dimension in addition to permission code;
- permission templates and immutable published template versions;
- exactly one active authorization profile in a session;
- client eligibility, enabled state and validity-window checks;
- READ/WRITE scope access;
- `authz_version` token invalidation;
- explainable `AuthorizationDecision` and ordered `AuthorizationDecisionStep`;
- diagnostics projection that is created directly from the runtime decision.

Source-specific behavior that must not enter IAM Core:

- `ACCOUNT`/`BUSINESS` as a fixed enum and their role combinations;
- role literals such as `ACCOUNT_ADMIN`, `BUSINESS_ADMIN` and `DORM_STAFF`;
- implied `bed_confirmation.read` permission;
- `DORM_STAFF_HARD_DENY` high-risk rule;
- `canAccessRoom`, `canAccessBed`, `canAccessStudent` and all mapper SQL that
  traverses campus, building, floor, college or major data.

The latter belongs in application `AuthorizationPolicy` and
`ResourceHierarchyProvider` adapters.

## Existing persistence facts

| Migration/table | Existing capability | Generic successor |
| --- | --- | --- |
| `V44__add_school_admin_authorization.sql`: `school_admin_identity` | Administrator identity, domain, base role, `authz_version` | `iam_user`, `iam_identity` |
| `V44`: `admin_permission` | Atomic permission catalog | `iam_permission` |
| `V44`: template/version/permission tables | Immutable permission-template revisions | `iam_permission_template`, `iam_permission_template_version`, `iam_template_permission` |
| `V44`: profile/scope tables | Profile, client scope, validity, default marker and resource scopes | `iam_authorization_profile`, `iam_authorization_scope` |
| `V49__split_admin_scope_read_write.sql` | Explicit read/write data scope | `access_mode` in `iam_authorization_scope` |
| `V63__add_auth_login_event_and_session.sql` | Login events, session lifecycle and privacy boundary | `iam_login_event`, `iam_session` |
| `V64__add_audit_subject_links.sql` | Multi-subject audit query index | `iam_audit_log`, `iam_audit_subject_link` |

The source migrations cannot be copied or edited. The generic framework starts
with its own independent Flyway migration history.

## Session and audit capabilities

The source system creates a server session separately from the opaque token,
stores no raw token in MySQL, and maintains Redis reverse mappings. It records
client type, client instance, server-resolved IP, user agent, device metadata,
login/last-seen/expiry/revocation state and successful or failed login events.
This becomes the generic session service and login audit model. Hardware IDs,
GPS, passwords and raw tokens remain prohibited.

`audit/AuditService`, `AuditSubject`, `AuditSubjectMapper` and their MyBatis XML
already provide the needed event-master plus multi-subject-link pattern. Event
names and source resource types are application-owned and must be converted to
generic operator/action/resource/result/request/before/after/metadata fields.

## Diagnostics and tests

`AuthorizationDiagnosticsService`, `AuthorizationDiagnosisProjection` and
their controller/catalog mapping demonstrate the required single-fact-source
pattern: diagnostics project an actual authorization decision and do not repeat
authorization logic. Existing focused tests cover token behavior, bearer filters,
context permission implication, authorization decisions, diagnosis projection,
and platform/mobile/auth service paths. They are source references only; new
generic tests will be rewritten against framework interfaces and generic order
example data.

## Coupling map and migration boundary

The current authorization mappers and numerous feature mappers embed resource
scope checks directly in business SQL. The initial extraction must leave those
queries untouched. A later adapter supplies `ResourceDescriptor` conversion,
resource hierarchy traversal and source-policy implementations while one
low-risk endpoint is migrated at a time. Existing tokens, routes and tables
remain authoritative until that endpoint has parity verification.
