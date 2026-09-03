#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_root="${1:-$script_dir/../iam-example/src/main/java}"

if [[ ! -d "$source_root" ]]; then
    echo "consumer source directory does not exist: $source_root" >&2
    exit 2
fi

forbidden_pattern='^[[:space:]]*import[[:space:]]+io\.github\.iamstarter\..*(\.internal|\.impl|\.persistence)(\.|;)'
if matches="$(rg -n --glob '*.java' "$forbidden_pattern" "$source_root" 2>/dev/null)"; then
    echo "consumer production code must depend only on IAM public API/SPI:" >&2
    printf '%s\n' "$matches" >&2
    exit 1
fi

echo "CONSUMER_PUBLIC_API_VERIFIED"
