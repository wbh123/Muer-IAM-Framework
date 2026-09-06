#!/usr/bin/env bash
# 校验已迁移源码不会重新引入旧 Muer 前命名空间。
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if rg -n 'io\.github\.iamstarter' "$repository_root" \
    --glob '!docs/superpowers/**' \
    --glob '!MIGRATION.md' \
    --glob '!**/db/iam/migration/**'; then
    echo 'legacy Java namespace remains' >&2
    exit 1
fi

rg -q '<groupId>io.github.muer</groupId>' "$repository_root/pom.xml"
rg -q '^io.github.muer.autoconfigure.MuerAutoConfiguration$' \
    "$repository_root/muer-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

if rg -n '^iam:' "$repository_root/muer-example/src/main/resources"; then
    echo 'legacy YAML configuration prefix remains' >&2
    exit 1
fi
