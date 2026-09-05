#!/usr/bin/env bash
# Verify that the documentation site CI and manual Pages preparation are correct.
#
# Checks:
#   1. .github/workflows/docs.yml exists and:
#        - triggers on pull_request to main and on push to main/release
#        - sets up Node 22 (Astro 7 requires Node >= 22.12)
#        - runs npm ci, npm run check, npm run build
#   2. If a Pages workflow exists (.github/workflows/docs-pages.yml) it is
#        triggerable ONLY by workflow_dispatch (never push/schedule) and also
#        builds with Node 22.
#   3. The starter verify workflow (.github/workflows/verify.yml) runs on
#        release/** so pushes to a release branch trigger backend verification.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docs_workflow="$repository_root/.github/workflows/docs.yml"
pages_workflow="$repository_root/.github/workflows/docs-pages.yml"

require_file() {
  local description="$1"
  local path="$2"
  if [[ ! -f "$path" ]]; then
    printf 'Missing documentation workflow file for: %s (%s)\n' "$description" "$path" >&2
    exit 1
  fi
}

require_content() {
  local description="$1"
  local file="$2"
  local expression="$3"
  if ! grep -Eq -- "$expression" "$file"; then
    printf 'Docs workflow missing: %s\n' "$description" >&2
    exit 1
  fi
}

# docs.yml is required.
require_file 'docs CI workflow' "$docs_workflow"

# Trigger on pull_request to main and push to main and release branches.
require_content 'pull_request trigger' "$docs_workflow" 'pull_request:'
require_content 'push trigger' "$docs_workflow" 'push:'
require_content 'push guards release branches' "$docs_workflow" 'release/\*\*'
require_content 'push guards main' "$docs_workflow" 'push:'
require_content 'pull_request targets main' "$docs_workflow" 'pull_request:'
require_content 'Node setup' "$docs_workflow" 'setup-node'
require_content 'installs lockfile dependencies' "$docs_workflow" 'npm[[:space:]]+ci'
require_content 'type-checks site' "$docs_workflow" 'npm[[:space:]]+run[[:space:]]+check'
require_content 'builds static site' "$docs_workflow" 'npm[[:space:]]+run[[:space:]]+build'

# The Astro 7 toolchain requires Node >= 22.12; CI must use Node 22 so the
# local and remote toolchains stay aligned with iam-docs/package.json engines.
require_content 'Node 22 in docs CI' "$docs_workflow" 'node-version:[[:space:]]*22'

# If present, the Pages workflow may only be manually dispatched and must also
# build with the same Node 22 toolchain as the docs CI workflow.
if [[ -f "$pages_workflow" ]]; then
  if grep -Eq -- '^\s+push:|^\s+pull_request:|^\s+schedule:' "$pages_workflow"; then
    printf 'Docs Pages workflow must be workflow_dispatch only (no push/pull_request/schedule).\n' >&2
    exit 1
  fi
  require_content 'Pages manual dispatch' "$pages_workflow" 'workflow_dispatch:'
  require_content 'Node 22 in docs Pages' "$pages_workflow" 'node-version:[[:space:]]*22'
fi

# The starter verify workflow must also run on release/** so that a push to a
# release branch actually triggers the backend verification gate.
verify_workflow="$repository_root/.github/workflows/verify.yml"
if [[ -f "$verify_workflow" ]]; then
  require_content 'verify runs on release branches' "$verify_workflow" 'release/\*\*'
fi

printf 'Docs workflow checks passed.\n'
