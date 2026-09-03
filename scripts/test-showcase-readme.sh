#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readme="$repository_root/iam-example/README.md"

require() {
  local description="$1"
  local expression="$2"
  if ! grep -Eq -- "$expression" "$readme"; then
    printf 'Missing showcase documentation for: %s\n' "$description" >&2
    exit 1
  fi
}

require 'demonstration credentials' 'operator-a.*demo-pass|demo-pass.*operator-a'
require 'runtime environment variables' 'IAM_EXAMPLE_JDBC_URL'
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
require 'container-free smoke command' 'mvn[[:space:]]+-pl[[:space:]]+iam-example[[:space:]]+-am.*-Dtest=IamStarterAutoConfigurationSmokeTest.*test'
require 'Testcontainers integration command' 'mvn[[:space:]]+-pl[[:space:]]+iam-example[[:space:]]+-am[[:space:]]+-Pintegration.*IamStarterConsumptionTest.*IamSecurityIntegrationTest.*test'

printf 'Showcase README checks passed.\n'
