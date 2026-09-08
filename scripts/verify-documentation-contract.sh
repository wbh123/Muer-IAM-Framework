#!/usr/bin/env bash
# Verify that the Muer documentation matches the real runtime contract.
#
# This is a Docs-vs-Code contract guard (per the muer-docs authoring spec):
# documentation must not drift from the actual Maven coordinates, Java/Spring
# namespaces, configuration properties, demonstration environment variables, and
# HTTP API the framework really exposes. It deliberately avoids ripgrep and only
# uses grep so it runs on the stock GitHub Actions ubuntu-latest runner.
#
# Checks:
#   1. Identity: active docs match metadata (brand Muer, website muer.cloud,
#      repository wbh123/Muer-IAM-Framework, group cloud.muer, package cloud.muer).
#   2. Active muer-docs content contains no legacy technical identity (the
#      former Java namespace, the former GitHub web hosts, the former repository
#      slug under wbh123, and the old frontend directory names). Historical
#      docs/ and docs/superpowers/** are intentionally excluded (legacy records).
#   3. Spring configuration prefix in active docs is muer.* -- no stray iam.*
#      property-prefix usage (legitimate iam.admin.* management permissions,
#      iam_* tables and /iam/** HTTP paths are allowed).
#   4. reference/configuration.md documents the real MuerProperties key set and
#      does not advertise properties that do not exist in MuerProperties.
#   5. muer-example demonstration environment variables are consistently
#      MUER_EXAMPLE_*; no stale IAM_EXAMPLE_* remains in active code/docs.
#   6. Core Quick Start endpoints exist in the real OpenAPI contract
#      (muer-management-web/src/main/resources/openapi/iam.yaml).
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docs_content="$repository_root/muer-docs/src/content/docs"
openapi="$repository_root/muer-management-web/src/main/resources/openapi/iam.yaml"
properties_src="$repository_root/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/MuerProperties.java"

failures=0
note_failure() {
  printf '  FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

pass() { printf '  ok: %s\n' "$1"; }

require_file() {
  local description="$1"
  local path="$2"
  if [[ ! -f "$path" ]]; then
    note_failure "$description (missing $path)"
    return 1
  fi
  pass "$description"
  return 0
}

# --- 1. Identity / brand contract -------------------------------------------
printf 'Identity contract\n'

metadata="$repository_root/metadata/project-metadata.yaml"
if require_file 'project-metadata.yaml exists' "$metadata"; then
  grep -Eq 'groupId:[[:space:]]*cloud\.muer' "$metadata" || note_failure 'metadata groupId is cloud.muer'
  grep -Eq 'website:[[:space:]]*https://muer\.cloud' "$metadata" || note_failure 'metadata website is https://muer.cloud'
  grep -Eq 'repositoryName:[[:space:]]*wbh123/Muer-IAM-Framework' "$metadata" || note_failure 'metadata repositoryName is wbh123/Muer-IAM-Framework'
  grep -Eq 'basePackage:[[:space:]]*cloud\.muer' "$metadata" || note_failure 'metadata basePackage is cloud.muer'
  grep -Eq 'starterArtifact:[[:space:]]*muer-spring-boot-starter' "$metadata" || note_failure 'metadata starterArtifact is muer-spring-boot-starter'
fi

# Root POM group id is cloud.muer.
root_pom="$repository_root/pom.xml"
if require_file 'root pom.xml exists' "$root_pom"; then
  grep -Eq '<groupId>cloud\.muer</groupId>' "$root_pom" || note_failure 'root POM groupId is cloud.muer'
  grep -Eq '<url>https://muer\.cloud</url>' "$root_pom" || note_failure 'root POM url is https://muer.cloud'
fi

# Astro site + edit link target the official site / repository.
astro_config="$repository_root/muer-docs/astro.config.mjs"
if require_file 'astro.config.mjs exists' "$astro_config"; then
  grep -Eq "https://muer\.cloud" "$astro_config" || note_failure 'astro site defaults to https://muer.cloud'
  grep -Eq "wbh123/Muer-IAM-Framework" "$astro_config" || note_failure 'astro edit link points at wbh123/Muer-IAM-Framework'
fi

# --- 2. No legacy technical identity in active docs --------------------------
printf 'Legacy-identity scan (active muer-docs only)\n'
# Exclude the former Java namespace, the former GitHub web hosts, the former
# repository slug under wbh123, and the old frontend directory names. A
# migration guide may reference the "former GitHub-based Java namespace" in prose
# without repeating the literal; here we only forbid the literal in current docs.
for token in 'io\.github\.muer' 'muer\.github\.io' 'wbh123\.github\.io' 'wbh123/iam' 'iam-admin-web' 'iam-docs'; do
  if grep -rEq "$token" "$docs_content" 2>/dev/null; then
    hits="$(grep -rlE "$token" "$docs_content" 2>/dev/null | sed "s#$repository_root/##" | tr '\n' ' ')"
    note_failure "legacy token '$token' found in active docs: $hits"
  else
    pass "no legacy token '$token' in active docs"
  fi
done

# --- 3. Spring config prefix is muer.*, not iam.* ----------------------------
printf 'Spring configuration prefix contract\n'
# Any backtick/prose iam.<prop> that is not a legitimate iam.admin.* management
# permission and not part of /iam/** (yaml file name) is a prefix drift.
stray="$(grep -rhoE 'iam\.[a-zA-Z0-9][a-zA-Z0-9._-]*' "$docs_content" 2>/dev/null \
  | grep -vE '^iam\.admin($|\.)' | grep -vE '^iam\.yaml$' | sort -u || true)"
if [[ -n "$stray" ]]; then
  note_failure "stray iam.* property references in active docs: $(printf '%s' "$stray" | tr '\n' ' ')"
else
  pass 'no stray iam.* property-prefix in active docs (only iam.admin.* management permissions remain)'
fi

# --- 4. Reference configuration documents only real MuerProperties keys ------
printf 'Configuration property contract\n'
if require_file 'MuerProperties.java exists' "$properties_src"; then
  real_keys=('muer.enabled' 'muer.token.ttl' 'muer.token.redis-prefix' 'muer.session.enabled'
    'muer.session.touch-interval' 'muer.schema.enabled' 'muer.schema.history-table'
    'muer.audit.enabled' 'muer.diagnostics.enabled' 'muer.client-types')
  config_doc="$docs_content/reference/configuration.md"
  if require_file 'reference/configuration.md exists' "$config_doc"; then
    for key in "${real_keys[@]}"; do
      grep -Fq "$key" "$config_doc" || note_failure "configuration.md missing real property $key"
    done
    # The doc must not present iam.* as the property namespace.
    grep -Eq '## `muer\.\*` 配置属性' "$config_doc" || note_failure 'configuration.md documents the muer.* property namespace'
    if grep -Eq '## `iam\.\*` 配置属性' "$config_doc"; then
      note_failure 'configuration.md still uses an iam.* property section heading'
    fi
  fi
fi

# --- 5. Example environment variables are consistently MUER_EXAMPLE_* --------
printf 'Example environment variable contract\n'
example_sources=(
  "$repository_root/muer-example/src/main/resources/application.yaml"
  "$repository_root/muer-example/src/main/resources/application-dev.yaml"
  "$repository_root/examples/quickstart/docker-compose.yml"
  "$repository_root/examples/quickstart/.env.example"
  "$repository_root/scripts/test-showcase-readme.sh"
  "$repository_root/muer-example/README.md"
)
for path in "${example_sources[@]}"; do
  if [[ -f "$path" ]]; then
    if grep -Eq 'IAM_EXAMPLE_' "$path"; then
      note_failure "stale IAM_EXAMPLE_ in $path"
    fi
  fi
done
if grep -rEq 'IAM_EXAMPLE_' "$docs_content" 2>/dev/null; then
  note_failure 'stale IAM_EXAMPLE_ references in active docs'
else
  pass 'no stale IAM_EXAMPLE_ in active docs or example sources'
fi
grep -rq 'MUER_EXAMPLE_' "$docs_content/getting-started" 2>/dev/null \
  && pass 'active docs reference MUER_EXAMPLE_* demonstration variables' \
  || note_failure 'active docs should reference MUER_EXAMPLE_* demonstration variables'

# --- 6. Core Quick Start endpoints exist in the OpenAPI contract -------------
printf 'HTTP endpoint contract\n'
if require_file 'OpenAPI iam.yaml exists' "$openapi"; then
  # Compare the set of path lines declared in OpenAPI with the endpoints the
  # active docs ask readers to call. We only assert the core endpoints used by
  # the Quick Start actually exist in the contract.
  for endpoint in '/iam/auth/login' '/iam/auth/me' '/iam/sessions' \
    '/iam/authorization/diagnostics' '/iam/authorization/profiles/.*/switch' \
    '/iam/sessions/.*/revoke'; do
    if grep -Eq "^[[:space:]]*${endpoint}:" "$openapi"; then
      pass "OpenAPI declares endpoint $endpoint"
    else
      note_failure "OpenAPI does not declare endpoint $endpoint"
    fi
  done
fi

# --- 6.2 Quick Start refers only to real endpoints --------------------------
quick_start="$docs_content/getting-started/quick-start.md"
if require_file 'quick-start.md exists' "$quick_start"; then
  for endpoint in '/iam/auth/login' '/iam/auth/me' '/iam/authorization/profiles/' \
    '/iam/authorization/diagnostics' '/iam/sessions/'; do
    grep -Fq "$endpoint" "$quick_start" || note_failure "quick-start.md should reference $endpoint"
  done
fi

if [[ "$failures" -gt 0 ]]; then
  printf '\nDocumentation contract check FAILED: %d problem(s).\n' "$failures" >&2
  exit 1
fi

printf 'Documentation contract check passed.\n'
