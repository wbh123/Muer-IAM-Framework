#!/usr/bin/env bash
# Verify that the 0.1.0 release candidate deliberately remains a SNAPSHOT.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

expected_version="0.1.0-SNAPSHOT"

root_version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)"
metadata_version="$(sed -n 's/^  version: //p' metadata/project-metadata.yaml | head -n 1)"

[[ "$root_version" == "$expected_version" ]] || { echo "Root Maven version must remain $expected_version (found $root_version)." >&2; exit 1; }
[[ "$metadata_version" == "$expected_version" ]] || { echo "Metadata version must remain $expected_version (found $metadata_version)." >&2; exit 1; }
grep -Fq '<tag>HEAD</tag>' pom.xml || { echo 'SCM tag must remain HEAD before formal version freeze.' >&2; exit 1; }

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

if grep -nH -E '<version>0\.1\.0</version>|<muer.version>0\.1\.0</muer.version>|^  version: 0\.1\.0$' "${release_files[@]}"; then
  echo 'A final 0.1.0 coordinate was introduced while the release candidate must remain SNAPSHOT.' >&2
  exit 1
fi

printf 'MUER_RELEASE_CANDIDATE_VERSION_VERIFIED version=%s scmTag=HEAD\n' "$expected_version"
