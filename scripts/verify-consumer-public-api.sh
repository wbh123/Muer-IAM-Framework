#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_root="${1:-$script_dir/../muer-example/src/main/java}"

if [[ ! -d "$source_root" ]]; then
    echo "consumer source directory does not exist: $source_root" >&2
    exit 2
fi

# Forbidden implementation packages: a consumer of the Muer starter must depend
# only on the public API/SPI. Internal MyBatis persistence and any
# internal/impl implementation packages under io.github.muer are off-limits.
forbidden_pattern='^[[:space:]]*import[[:space:]]+io\.github\.muer\.([a-z][a-zA-Z]*\.)*(internal|impl|persistence)(\.|;)'
if matches="$(grep -rInE --include='*.java' "$forbidden_pattern" "$source_root" 2>/dev/null)"; then
    echo "consumer production code must depend only on IAM public API/SPI:" >&2
    printf '%s\n' "$matches" >&2
    exit 1
fi

echo "CONSUMER_PUBLIC_API_VERIFIED"
