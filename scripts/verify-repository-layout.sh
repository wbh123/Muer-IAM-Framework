#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0

fail() {
  printf 'LAYOUT_FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

require_path() {
  [[ -e "$repository_root/$1" ]] || fail "missing required path: $1"
}

require_path contracts/openapi/iam.yaml
require_path modules/muer-http-api
require_path apps/muer-admin-console
require_path apps/muer-docs-site
require_path examples/quickstart
require_path examples/showcase
require_path test-apps/consumer-acceptance
require_path tests/architecture

legacy_dirs=(muer-management-web muer-admin-web muer-example muer-docs muer-tests)
for directory in "${legacy_dirs[@]}"; do
  [[ ! -e "$repository_root/$directory" ]] || fail "legacy root directory exists: $directory"
done

mapfile -t contracts < <(find "$repository_root" -type f -name 'iam.yaml' -not -path '*/target/*' -not -path '*/node_modules/*' | sort)
if [[ "${#contracts[@]}" -ne 1 || "${contracts[0]}" != "$repository_root/contracts/openapi/iam.yaml" ]]; then
  fail "formal OpenAPI contract is not unique at contracts/openapi/iam.yaml"
fi

if grep -Eq '<module>(examples|apps|test-apps)(/|<)' "$repository_root/pom.xml"; then
  fail 'root Maven Reactor contains an application, example, or test-app module'
fi

for artifact in muer-management-web muer-example muer-tests; do
  if find "$repository_root" -name pom.xml -not -path '*/target/*' -exec grep -l "<artifactId>$artifact</artifactId>" {} + | grep -q .; then
    fail "legacy Artifact ID remains in a POM: $artifact"
  fi
done

for pom in "$repository_root/examples/quickstart/pom.xml" "$repository_root/examples/showcase/pom.xml" "$repository_root/test-apps/consumer-acceptance/pom.xml"; do
  if grep -q '<artifactId>muer-parent</artifactId>' "$pom"; then
    fail "external consumer inherits muer-parent: ${pom#$repository_root/}"
  fi
done

grep -Fq '../../contracts/openapi/iam.yaml' "$repository_root/apps/muer-admin-console/package.json" \
  || fail 'Admin Console generator does not use the shared contract'

if (( failures > 0 )); then
  exit 1
fi
printf 'REPOSITORY_LAYOUT_VERIFIED\n'
