#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
sandbox="$(mktemp -d)"
trap 'rm -rf "$sandbox"' EXIT

mkdir -p "$sandbox/src/main/java/example"
printf '%s\n' 'import io.github.muer.session.internal.Secret;' > "$sandbox/src/main/java/example/Illegal.java"

if bash "$project_root/scripts/verify-consumer-public-api.sh" "$sandbox/src/main/java"; then
    echo "expected internal API import to be rejected" >&2
    exit 1
fi

printf '%s\n' 'import io.github.muer.authentication.IdentityAuthenticator;' > "$sandbox/src/main/java/example/Legal.java"
rm "$sandbox/src/main/java/example/Illegal.java"
bash "$project_root/scripts/verify-consumer-public-api.sh" "$sandbox/src/main/java"

echo "CONSUMER_PUBLIC_API_CHECK_PASSED"
