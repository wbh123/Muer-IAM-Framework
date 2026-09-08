#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readme="$repository_root/muer-example/README.md"
root_readme="$repository_root/README.md"

require() {
  local description="$1"
  local expression="$2"
  if ! grep -Eq -- "$expression" "$readme"; then
    printf 'Missing showcase documentation for: %s\n' "$description" >&2
    exit 1
  fi
}

require 'demonstration credentials' 'operator-a.*demo-pass|demo-pass.*operator-a'
require 'runtime environment variables' 'MUER_EXAMPLE_JDBC_URL'
require 'login endpoint' 'POST[[:space:]]+/iam/auth/login'
require 'principal and session reads' 'GET[[:space:]]+/iam/auth/me'
require 'session listing' 'GET[[:space:]]+/iam/sessions'
require 'allowed order read' 'GET[[:space:]]+/example/orders/9001'
require 'forbidden department scope' 'GET[[:space:]]+/example/orders/9002.*403|403.*GET[[:space:]]+/example/orders/9002'
require 'forbidden reader approval' 'POST[[:space:]]+/example/orders/9001/approve.*403|403.*POST[[:space:]]+/example/orders/9001/approve'
require 'profile switch endpoint' 'POST[[:space:]]+/iam/authorization/profiles/.*/switch'
require 'authorization diagnostics endpoint' 'POST[[:space:]]+/iam/authorization/diagnostics'
require 'session revoke endpoint' 'POST[[:space:]]+/iam/sessions/.*/revoke'
require 'post-revocation unauthorized result' '401'
require 'container-free smoke command' 'mvn[[:space:]]+-pl[[:space:]]+muer-example[[:space:]]+-am.*-Dtest=IamStarterAutoConfigurationSmokeTest.*test'
require 'Testcontainers integration command' 'mvn[[:space:]]+-pl[[:space:]]+muer-example[[:space:]]+-am[[:space:]]+-Pintegration.*IamStarterConsumptionTest.*IamSecurityIntegrationTest.*test'
require 'declarative permission annotation' '@RequirePermission'
require 'MVC resource resolver SPI' 'MvcResourceDescriptorResolver'
require 'declarative unauthenticated status' '401'
require 'declarative forbidden status' '403'
require 'declarative missing-resource status' '404'
require 'declarative resolver-misconfiguration status' '500'
require 'authorization failure handler SPI' 'IamAuthorizationFailureHandler'
require 'problem detail response' 'ProblemDetail'
require 'stable forbidden failure code' 'IAM_ACCESS_DENIED'
require 'stable missing-resource failure code' 'IAM_RESOURCE_NOT_FOUND'

require_root() {
  local description="$1"
  local expression="$2"
  if ! grep -Eq -- "$expression" "$root_readme"; then
    printf 'Missing repository release reference for: %s\n' "$description" >&2
    exit 1
  fi
}

require_file() {
  local description="$1"
  local path="$2"
  if [[ ! -f "$repository_root/$path" ]]; then
    printf 'Missing repository release file for: %s (%s)\n' "$description" "$path" >&2
    exit 1
  fi
}

require_root 'quick-start link' 'docs/QUICK_START\.md'
require_root 'public API link' 'docs/PUBLIC_API\.md'
require_root '0.1.0 release-notes link' 'docs/RELEASE_NOTES_0\.1\.0\.md'
require_file 'quick-start document' 'docs/QUICK_START.md'
require_file 'public API document' 'docs/PUBLIC_API.md'
require_file 'release notes document' 'docs/RELEASE_NOTES_0.1.0.md'
require_file 'release validation report' 'docs/validation/muer-0.1.0-release-report.md'

while IFS= read -r source_path; do
  if [[ ! -f "$repository_root/$source_path" ]]; then
    printf 'Invalid source path in PUBLIC_API.md: %s\n' "$source_path" >&2
    exit 1
  fi
done < <(grep -oE 'iam-[A-Za-z0-9._/-]+\.java' "$repository_root/docs/PUBLIC_API.md" | sort -u)

printf 'Showcase README checks passed.\n'
