#!/usr/bin/env bash
# Verify that a release branch is frozen to a non-SNAPSHOT Maven version.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

expected_version="0.1.0"
expected_tag="v${expected_version}"

root_version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)"
metadata_version="$(sed -n 's/^  version: //p' metadata/project-metadata.yaml | head -n 1)"

[[ "$root_version" == "$expected_version" ]] || { echo "Root Maven version must be $expected_version (found $root_version)." >&2; exit 1; }
[[ "$metadata_version" == "$expected_version" ]] || { echo "Metadata version must be $expected_version (found $metadata_version)." >&2; exit 1; }
grep -Fq "<tag>${expected_tag}</tag>" pom.xml || { echo "SCM tag must be ${expected_tag}." >&2; exit 1; }

release_files=(
  pom.xml
  metadata/project-metadata.yaml
  modules/muer-core/pom.xml
  modules/muer-authentication/pom.xml
  modules/muer-authorization/pom.xml
  modules/muer-session/pom.xml
  modules/muer-audit/pom.xml
  modules/muer-diagnostics/pom.xml
  modules/muer-persistence-mybatis/pom.xml
  modules/muer-http-api/pom.xml
  modules/muer-spring-boot-autoconfigure/pom.xml
  modules/muer-spring-boot-starter/pom.xml
  tests/architecture/pom.xml
  examples/quickstart/pom.xml
  examples/showcase/pom.xml
  test-apps/consumer-acceptance/pom.xml
)

if grep -nH 'SNAPSHOT' "${release_files[@]}"; then
  echo 'SNAPSHOT version remains in a release-critical file.' >&2
  exit 1
fi

printf 'MUER_RELEASE_VERSION_VERIFIED version=%s tag=%s\n' "$expected_version" "$expected_tag"
