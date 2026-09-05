# IAM 0.1.0 Release Validation Report

## Candidate

| Item | Value |
| --- | --- |
| Branch | `release/0.1.0` |
| Maven version | `0.1.0-SNAPSHOT` |
| Runtime baseline | Java 21 (OpenJDK 21.0.12), Spring Boot 4.0.0, MySQL 8.4, Redis 7 |
| Local build toolchain | Maven 3.8.7, OpenJDK 21, Node v26.3.1 |
| Remote CI head SHA | `0fefa88` (see "Remote CI" below) |
| Remote CI | See "Remote CI evidence" section — all green |
| Tag / publication | Not created |

## Repository release references (Task 1)

| Check | Command | Result |
| --- | --- | --- |
| Showcase / README release references | `bash scripts/test-showcase-readme.sh` | Passed (re-run during release gate) |
| Whitespace errors | `git diff --check` | Passed during documentation assembly |
| Snapshot-version scan | `git grep -n "0.1.0-SNAPSHOT"` | Repository remains on snapshot coordinates |

## Runnable QuickStart (Task 2)

| Check | Command | Result |
| --- | --- | --- |
| Auto-configuration unit test | `mvn -B -pl iam-spring-boot-autoconfigure -am -Dtest=IamAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed (BUILD SUCCESS) |
| Consumer container-free smoke | `mvn -B -pl iam-example -am -Dtest=IamStarterAutoConfigurationSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed (BUILD SUCCESS) |
| Showcase MySQL/Redis walkthrough | `mvn -B -pl iam-example -am -Pintegration -Dtest=IamStarterConsumptionTest,IamSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed (Testcontainers, BUILD SUCCESS) |
| Independent consumer acceptance | `mvn -B -pl iam-example -am -Pintegration -Dtest=IamConsumerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` | Passed (Testcontainers, BUILD SUCCESS) |
| Consumer public API boundary | `bash scripts/verify-consumer-public-api.sh` | Passed (`CONSUMER_PUBLIC_API_VERIFIED`) |
| Starter packaging | `mvn -B -pl iam-spring-boot-starter -am package -DskipTests` | Passed (BUILD SUCCESS) |
| Full reactor install after POM change | `mvn -B -DskipTests install` | Passed (BUILD SUCCESS) |

## Documentation site (Task 3) and CI (Task 4)

| Check | Command | Result |
| --- | --- | --- |
| Docs workflow-content guard | `bash scripts/test-docs-workflow.sh` | Passed |
| Site type-check | `npm run check` (`astro check`) | 0 errors / 0 warnings / 0 hints |
| Site static build | `npm run build` (`astro build`) | 55 pages, Pagefind + sitemap generated |
| Unfinished-marker scan | `grep -rniE "TODO|TBD|Coming soon|FIXME|PLACEHOLDER" iam-docs/src docs/*.md` | No placeholders (only authoring-guideline mentions) |
| Release-branch CI trigger | `.github/workflows/verify.yml` push branches | Now includes `release/**` |

## Release metadata (Task 5)

Root `pom.xml` now declares a source-verified `description`, `url`, `scm`, and a
developer entry, with the full reactor still building. No license claim was invented.

## Gate and findings

All local release-gate commands above passed. The legacy-identifier blocker that had
failed the remote `verify` job (a local-environment string in the prior version of this
report) was removed, and the documentation Node toolchain was aligned to Node 22
(Astro 7 requires `>= 22.12.0`);
`scripts/test-docs-workflow.sh` now asserts Node 22 in both docs workflows and a
`release/**` trigger in `verify.yml`. See "Remote CI evidence" below for the observed
results at the pushed head.

| Priority | Finding | Status |
| --- | --- | --- |
| P0 | None recorded | None |
| P1 | No `LICENSE` file exists in the repository; publish/license metadata is omitted from the POM. This is a Central-publish blocker but does not affect the software build or tests. | Open — requires human decision |
| P2 | Root POM developer entry records only a verified GitHub id (`wbh123`); full name and email are not published. | Informational |
| P2 | GitHub Actions deprecation notices: `actions/checkout@v4`/`actions/setup-java@v4` target Node 20 (forced onto Node 24 runners), and `setup-java@v4` is deprecated in favour of `v5`. Non-blocking technical debt for 0.2.0. | Informational |
| P3 | Deferred work is tracked as 0.2.0 roadmap items, not 0.1.0. | Informational |

## Remote CI evidence

Observed after pushing `release/0.1.0` at head SHA `0fefa8888480d96b25f865bbeae24db7614d9a83`.

| Workflow | Run ID | Head SHA | Conclusion | Jobs |
| --- | --- | --- | --- | --- |
| Verify IAM Starter | 33941185772 | `0fefa88` | success | `verify` (success), `Independent Consumer acceptance` (success), `Docker/Testcontainers consumer showcase` (success) |
| Verify IAM Documentation | 33941185821 | `0fefa88` | success | `docs` (success) |

The remote `verify` job's `Verify project identity` step (PowerShell identity scans:
`test-identity-check.ps1`, `test-rename-project.ps1`, `verify-no-legacy-identifiers.ps1`)
passed on the runner. No step used `continue-on-error` and no verification was relaxed.

## Conclusion

**Software release candidate**: `READY FOR HUMAN RELEASE APPROVAL` — all code, tests,
docs, consumer-acceptance, Testcontainers and both remote CI workflows are green, with
no P0. The only P1 is a missing `LICENSE`, which is a **public open-source / Maven
Central distribution** matter, not a software-quality blocker.

**Public Maven / open-source distribution**: `NOT READY` — a `LICENSE` decision by the
project owner is required before any Central/OSI publication. This is intentionally kept
open and not auto-created by the release tooling.

No tag, artifact publication, or GitHub Release has been created.
