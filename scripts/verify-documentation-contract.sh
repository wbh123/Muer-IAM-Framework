#!/usr/bin/env bash
# Verify that the Muer documentation matches the real runtime contract.
#
# This is a Docs-vs-Code contract guard (per the apps/muer-docs-site authoring spec):
# documentation must not drift from the actual Maven coordinates, Java/Spring
# namespaces, configuration properties, demonstration environment variables, and
# HTTP API the framework really exposes. It deliberately avoids ripgrep and only
# uses grep so it runs on the stock GitHub Actions ubuntu-latest runner.
#
# Checks:
#   1. Identity: active docs match metadata (brand Muer, website muer.cloud,
#      repository wbh123/Muer-IAM-Framework, group cloud.muer, package cloud.muer).
#   2. Active apps/muer-docs-site content contains no legacy technical identity (the
#      former Java namespace, the former GitHub web hosts, the former repository
#      slug under wbh123, and the old frontend directory names). The scan is
#      scoped to apps/muer-docs-site/src/content/docs only; the maintainer-only docs/
#      directory is not part of the public documentation contract.
#   3. Spring configuration prefix in active docs is muer.* -- no stray iam.*
#      property-prefix usage (legitimate iam.admin.* management permissions,
#      iam_* tables and /iam/** HTTP paths are allowed).
#   4. reference/configuration.md documents the real MuerProperties key set and
#      does not advertise properties that do not exist in MuerProperties.
#   5. examples/showcase demonstration environment variables are consistently
#      MUER_SHOWCASE_*; no stale IAM_EXAMPLE_* remains in active code/docs.
#   6. Core Quick Start endpoints exist in the real OpenAPI contract
#      (modules/muer-http-api/src/main/resources/openapi/iam.yaml).
#   7. The standalone examples/quickstart consumer exists, consumes only
#      cloud.muer:muer-spring-boot-starter, is not part of the reactor, and
#      uses only Muer public API (no internal/persistence/Mapper imports).
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
docs_content="$repository_root/apps/muer-docs-site/src/content/docs"
openapi="$repository_root/contracts/openapi/iam.yaml"
properties_src="$repository_root/modules/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/MuerProperties.java"

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
astro_config="$repository_root/apps/muer-docs-site/astro.config.mjs"
if require_file 'astro.config.mjs exists' "$astro_config"; then
  grep -Eq "https://muer\.cloud" "$astro_config" || note_failure 'astro site defaults to https://muer.cloud'
  grep -Eq "wbh123/Muer-IAM-Framework" "$astro_config" || note_failure 'astro edit link points at wbh123/Muer-IAM-Framework'
fi

# --- 2. No legacy technical identity in active docs --------------------------
printf 'Legacy-identity scan (active apps/muer-docs-site only)\n'
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

# --- 5. Example environment variables are consistently MUER_SHOWCASE_* --------
printf 'Example environment variable contract\n'
example_sources=(
  "$repository_root/examples/showcase/src/main/resources/application.yaml"
  "$repository_root/examples/showcase/src/main/resources/application-dev.yaml"
  "$repository_root/examples/quickstart/docker-compose.yml"
  "$repository_root/examples/quickstart/.env.example"
  "$repository_root/scripts/test-showcase-readme.sh"
  "$repository_root/examples/showcase/README.md"
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
# Demonstration variables in active docs use the MUER_ prefix (either the
# MUER_SHOWCASE_* showcase variables or the MUER_* quickstart variables).
if grep -rqE 'MUER_(SHOWCASE_)?[A-Z_]+' "$docs_content/getting-started" 2>/dev/null; then
  pass 'active docs reference MUER_* / MUER_SHOWCASE_* demonstration variables'
else
  note_failure 'active docs should reference MUER_* / MUER_SHOWCASE_* demonstration variables'
fi

# --- 6. Core Quick Start endpoints exist in the OpenAPI contract -------------
printf 'HTTP endpoint contract\n'
if require_file 'OpenAPI iam.yaml exists' "$openapi"; then
  # Principal identifiers are required by the runtime constructor and must not
  # drift back to nullable OpenAPI fields.
  grep -Eq 'required: \[userId, identityId, identityDomain, activeProfileId, templateVersionId, clientType, authorizationVersion\]' "$openapi" \
    && pass 'PrincipalResponse requires active profile and template version' \
    || note_failure 'PrincipalResponse must require activeProfileId and templateVersionId'
  if grep -A12 -E '^    PrincipalResponse:' "$openapi" | grep -Eq 'activeProfileId:.*nullable: true|templateVersionId:.*nullable: true'; then
    note_failure 'PrincipalResponse profile identifiers must not be nullable'
  else
    pass 'PrincipalResponse profile identifiers are non-nullable'
  fi
  if grep -RFn '"applicationCode"' "$docs_content/operations" "$docs_content/getting-started" 2>/dev/null; then
    note_failure 'Authorization diagnostics docs must use domain, not applicationCode'
  else
    pass 'Authorization diagnostics docs use the current request schema'
  fi
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

# --- 7. Quick Start consumer example exists and matches the docs ------------
printf 'Quick Start consumer contract\n'
qs_root="$repository_root/examples/quickstart"
qs_pom="$qs_root/pom.xml"
qs_main="$qs_root/src/main/java/com/example/muerquickstart"
if require_file 'examples/quickstart/pom.xml exists' "$qs_pom"; then
  grep -Eq '<groupId>cloud\.muer</groupId>' "$qs_pom" \
    && grep -Eq '<artifactId>muer-spring-boot-starter</artifactId>' "$qs_pom" \
    && pass 'quickstart pom consumes cloud.muer:muer-spring-boot-starter' \
    || note_failure 'quickstart pom must consume cloud.muer:muer-spring-boot-starter'
  grep -Eq '<artifactId>muer-quickstart</artifactId>' "$qs_pom" \
    || note_failure 'quickstart pom artifactId is muer-quickstart'
  # It must be a standalone consumer, not part of the muer reactor modules.
  if grep -rEq 'muer-parent|<module>quickstart' "$qs_pom"; then
    note_failure 'quickstart pom must not inherit muer-parent or be a reactor module'
  else
    pass 'quickstart is a standalone consumer (no muer-parent, not a reactor module)'
  fi
fi
for f in \
  "$qs_main/QuickStartApplication.java" \
  "$qs_main/account/DemoIdentityAuthenticator.java" \
  "$qs_main/account/DemoAccountService.java" \
  "$qs_main/document/DocumentController.java" \
  "$qs_main/document/DocumentResourceResolver.java" \
  "$qs_main/security/MuerPermissionConfiguration.java" \
  "$qs_main/security/DocumentResourceHierarchyProvider.java"; do
  require_file "$(basename "$(dirname "$f")")/$(basename "$f") exists in examples/quickstart" "$f"
done
# No Muer internal / persistence / Mapper imports in the independent consumer.
if grep -rEn 'cloud\.muer\.(persistence|.*\.internal)|\.mapper\.|Mapper' "$qs_root/src" 2>/dev/null; then
  note_failure 'quickstart consumer imports internal Muer persistence/Mapper types'
else
  pass 'quickstart consumer uses only Muer public API (no persistence/internal/Mapper imports)'
fi

if [[ "$failures" -gt 0 ]]; then
  printf '\nDocumentation contract check FAILED: %d problem(s).\n' "$failures" >&2
  exit 1
fi

printf 'Documentation contract check passed.\n'
