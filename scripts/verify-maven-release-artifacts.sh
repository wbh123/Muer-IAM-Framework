#!/usr/bin/env bash
# Validate the local Maven Central release bundle without uploading it.
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

version="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)"
test -n "$version" || { echo 'Unable to read project version.' >&2; exit 1; }

publishable_modules=(
  modules/muer-core
  modules/muer-authentication
  modules/muer-authorization
  modules/muer-session
  modules/muer-audit
  modules/muer-diagnostics
  modules/muer-persistence-mybatis
  modules/muer-http-api
  modules/muer-spring-boot-autoconfigure
  modules/muer-spring-boot-starter
)

for module in "${publishable_modules[@]}"; do
  artifact_id="$(grep -o '<artifactId>[^<]*</artifactId>' "$module/pom.xml" | sed -n '2p' | sed 's/<[^>]*>//g')"
  test -n "$artifact_id" || { echo "Missing artifactId in $module/pom.xml" >&2; exit 1; }

  project_name="$(grep -o '<name>[^<]*</name>' "$module/pom.xml" | head -n 1 | sed 's/<[^>]*>//g' || true)"
  test -n "$project_name" || {
    echo "Missing Maven Central project name in $module/pom.xml" >&2
    exit 1
  }

  target="$module/target"
  for suffix in ".jar" "-sources.jar" "-javadoc.jar"; do
    file="$target/${artifact_id}-${version}${suffix}"
    test -f "$file" || { echo "Missing release artifact: $file" >&2; exit 1; }
  done

  for archive in "$target/${artifact_id}-${version}.jar" "$target/${artifact_id}-${version}-sources.jar" "$target/${artifact_id}-${version}-javadoc.jar"; do
    if jar tf "$archive" | grep -Eq '(^|/)(test|tests)/|(^|/)\.env($|\.)|credentials|\.superpowers'; then
      echo "Release artifact contains forbidden test/secret/planning content: $archive" >&2
      exit 1
    fi
  done
done

test "$(sed -n 's:.*<maven.deploy.skip>\([^<]*\)</maven.deploy.skip>.*:\1:p' tests/architecture/pom.xml | head -n 1)" = true

grep -q '<excludeArtifact>muer-architecture-tests</excludeArtifact>' pom.xml || {
  echo 'Central publishing configuration must explicitly exclude muer-architecture-tests.' >&2
  exit 1
}

if find examples apps test-apps tests/architecture \
    \( -path '*/target/central-staging' -o -path '*/target/*-sources.jar' -o -path '*/target/*-javadoc.jar' \) \
    -print | grep -q .; then
  echo 'A non-published example, app, consumer, or architecture-test project produced release artifacts.' >&2
  exit 1
fi

# central-publishing-maven-plugin may still collect a module even when
# maven.deploy.skip=true. Inspect any generated Central bundle as a final
# publication-boundary check so test-only modules can never reach the portal.
while IFS= read -r -d '' bundle; do
  if unzip -Z1 "$bundle" | grep -q 'muer-architecture-tests'; then
    echo "Central bundle unexpectedly contains muer-architecture-tests: $bundle" >&2
    exit 1
  fi
done < <(find . -type f -name 'central-bundle*.zip' -print0)

tracked_sensitive_files="$(git ls-files | grep -E '(^|/)(settings\.xml|\.env($|\.)|.*credentials.*|.*private.*key.*)$' | grep -Ev '(^|/)\.env\.example$' || true)"
if test -n "$tracked_sensitive_files"; then
  echo 'Tracked credential-like file found in repository.' >&2
  printf '%s\n' "$tracked_sensitive_files" >&2
  exit 1
fi

echo "MAVEN_RELEASE_ARTIFACTS_VERIFIED version=$version modules=${#publishable_modules[@]}"
