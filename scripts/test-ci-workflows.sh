#!/usr/bin/env bash
# Guard ordinary GitHub Actions workflows against release-candidate coverage gaps
# and deprecated action-major regressions. Maven Central publication is
# intentionally excluded from this check.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow_dir="$repository_root/.github/workflows"

ordinary_workflows=(
  "$workflow_dir/verify.yml"
  "$workflow_dir/admin-web.yml"
  "$workflow_dir/docs.yml"
  "$workflow_dir/docs-pages.yml"
)

fail() {
  printf 'CI workflow hygiene check failed: %s\n' "$1" >&2
  exit 1
}

for workflow in "${ordinary_workflows[@]}"; do
  [[ -f "$workflow" ]] || fail "missing workflow: ${workflow#$repository_root/}"
done

# These majors are the maintained official-action lines used by the ordinary
# CI workflows. Keep Java 21 / Node 22 as application toolchain versions; the
# versions below are the GitHub Action runtimes themselves.
for workflow in "${ordinary_workflows[@]}"; do
  if grep -Eq 'actions/checkout@v[1-6]([[:space:]]|$)' "$workflow"; then
    fail "${workflow#$repository_root/} uses an obsolete actions/checkout major"
  fi
  if grep -Eq 'actions/setup-node@v[1-6]([[:space:]]|$)' "$workflow"; then
    fail "${workflow#$repository_root/} uses an obsolete actions/setup-node major"
  fi
  if grep -Eq 'actions/setup-java@v[1-5]([[:space:]]|$)' "$workflow"; then
    fail "${workflow#$repository_root/} uses an obsolete actions/setup-java major"
  fi
done

pages_workflow="$workflow_dir/docs-pages.yml"
grep -Eq 'actions/configure-pages@v6([[:space:]]|$)' "$pages_workflow" \
  || fail 'docs-pages.yml must use actions/configure-pages@v6'
grep -Eq 'actions/upload-pages-artifact@v5([[:space:]]|$)' "$pages_workflow" \
  || fail 'docs-pages.yml must use actions/upload-pages-artifact@v5'
grep -Eq 'actions/deploy-pages@v5([[:space:]]|$)' "$pages_workflow" \
  || fail 'docs-pages.yml must use actions/deploy-pages@v5'

admin_workflow="$workflow_dir/admin-web.yml"
grep -Eq 'release/\*\*' "$admin_workflow" \
  || fail 'admin-web.yml must run for release/** pushes'

verify_workflow="$workflow_dir/verify.yml"
showcase_command_count="$({ grep -F 'mvn -B -f examples/showcase/pom.xml -Pintegration test' "$verify_workflow" || true; } | wc -l | tr -d '[:space:]')"
[[ "$showcase_command_count" == "1" ]] \
  || fail "verify.yml must execute the full Showcase integration command exactly once (found $showcase_command_count)"

# Release publication has a separate lifecycle and is deliberately outside this
# guard. This prevents ordinary CI maintenance from silently changing the Maven
# Central workflow.
[[ -f "$workflow_dir/release-maven.yml" ]] \
  || fail 'release-maven.yml is missing (publication workflow should remain separate)'

printf 'Ordinary GitHub Actions workflow hygiene checks passed.\n'
