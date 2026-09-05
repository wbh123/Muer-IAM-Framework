# IAM 0.1.0 Release Validation Report

## Candidate

| Item | Value |
| --- | --- |
| Branch | `release/0.1.0` |
| Maven version | `0.1.0-SNAPSHOT` |
| Runtime baseline | Java 21 (OpenJDK 21.0.12), Spring Boot 4.0.0, MySQL 8.4, Redis 7 |
| Local build toolchain | Maven 3.8.7, Node v26.3.1 (wust conda env) |
| Remote CI | `Pending` (push not yet observed) |
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

All local release-gate commands above passed. Identity scans
(`test-identity-check.ps1`, `test-rename-project.ps1`, `verify-no-legacy-identifiers.ps1`)
are PowerShell-based and run as part of the remote CI `verify` job; they have not been
observed locally. Remote CI status remains `Pending` until the branch is pushed.

| Priority | Finding | Status |
| --- | --- | --- |
| P0 | None recorded | None |
| P1 | No `LICENSE` file exists in the repository; publish/license metadata is omitted from the POM. This is a Central-publish blocker but does not affect the software build or tests. | Open — requires human decision |
| P2 | Root POM developer entry records only a verified GitHub id (`wbh123`); full name and email are not published. | Informational |
| P2 | Remote CI for `release/0.1.0` has not been observed. | Pending |
| P3 | Deferred work is tracked as 0.2.0 roadmap items, not 0.1.0. | Informational |

## Conclusion

Local gates are green and no P0 exists. One P1 (missing `LICENSE`) and remote-CI
observation remain before this can be marked `READY FOR RELEASE`. Remote CI evidence
must be collected by pushing `release/0.1.0`, then the release decision is handed to a
human. No tag, artifact publication, or GitHub Release has been created.
