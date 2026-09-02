# Task 3 Report: Permission and resource-scope decisions

## Scope delivered

- Added a real HTTP walkthrough assertion for profile switching: `reader-501`
  receives `403` for `POST /example/orders/9001/approve`; switching the same
  principal to stable profile `402` (`approver-501`) issues a new token that
  receives `200`.
- Kept the existing generic business boundary unchanged. The controller builds
  an `ORDER` resource with a `DEPARTMENT` parent path and delegates the
  decision to `AuthorizationEngine`; no authorization-engine logic was copied
  and no route behavior was expanded.
- Added the deliberate `seedOperatorAProfiles` fixture method. Existing
  single-profile fixture methods reset IAM state, so this is the only fixture
  that intentionally composes profiles for the profile-switch HTTP scenario.
- The fixture now seeds `approver-501` at profile ID `402`, using an isolated
  approver template containing `order.read` and `order.approve`, plus
  department `501` `READ` and `WRITE` scopes. The existing `reader-501` and
  `reader-502` fixtures remain read-only and department-specific.

## TDD evidence

1. Added `profile_switch_changes_order_approval_from_reader_denial_to_approver_success`
   before fixture implementation. It first expected an HTTP `200` from the
   profile switch while only the reader profile existed.
2. The initial integration run was blocked during Testcontainers startup with
   `Could not find a valid Docker environment`, so it could not reach the
   intended missing-profile assertion. A later default test invocation also
   exposed the intended red state as `expected: <200> but was: <404>` for the
   missing profile.
3. Added the minimal fixture data described above. The focused integration
   rerun passed the complete HTTP behavior, including the reader denial,
   two available profiles, successful switch, and approver success.

## Verification

- `mvn -pl iam-example -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ExampleOrderControllerTest test`
  - passed: 3 tests.
- `mvn -pl iam-example -am -Pintegration -Dsurefire.failIfNoSpecifiedTests=false -Dtest=IamStarterConsumptionTest#profile_switch_changes_order_approval_from_reader_denial_to_approver_success test`
  - passed: 1 test against MySQL and Redis Testcontainers.
- `mvn -pl iam-example -am -Dsurefire.failIfNoSpecifiedTests=false test`
  - passed: reactor build success; `iam-example` default suite passed 7 tests.
- `git diff --check`
  - passed before the full default run.

## Concerns

- Docker/Testcontainers availability was transient: the first focused
  integration invocation failed at Docker environment initialization, while a
  later focused run and the default reactor test run were able to start
  containers. The initial startup blocker is retained here because it can
  recur in this environment.
- The new scenario is explicitly covered by the integration Maven profile;
  the default `iam-example` suite intentionally excludes `*ConsumptionTest`.
