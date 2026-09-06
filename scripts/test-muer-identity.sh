#!/usr/bin/env bash
# 校验已迁移源码不会重新引入旧 Muer 前命名空间（品牌迁移守卫）。
#
# 使用可移植的 grep（而非 ripgrep），以便在任意 GitHub Actions runner 上执行。
# rg 在部分 ubuntu-latest 镜像上并不随镜像提供，导致此守卫此前以 exit 127 失败。
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 1) 禁止旧品牌 Java 命名空间出现在运行时代码与当前使用文档中。
#    允许的位置仅限历史迁移/设计文档：docs/superpowers/、MIGRATION.md、db 迁移。
#    旧命名空间 = 'io.github' + '.iamstarter'（此处不字面写出，避免自匹配）。
#    grep -I 忽略二进制；排除构建产物与其它 worktree，避免误报。
legacy_hits="$(
  grep -rInE 'io\.github\.iamstarter' "$repository_root" \
    --exclude-dir=.git \
    --exclude-dir=.worktrees \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=target \
    --exclude-dir=.astro \
    2>/dev/null \
  | grep -v '^[^:]*/docs/superpowers/' \
  | grep -v '/MIGRATION.md:' \
  | grep -v '/db/iam/migration/' \
  || true
)"
if [ -n "$legacy_hits" ]; then
    echo 'legacy Java namespace remains:' >&2
    printf '%s\n' "$legacy_hits" >&2
    exit 1
fi

# 2) 根 POM 使用 Muer groupId。
if ! grep -q '<groupId>io.github.muer</groupId>' "$repository_root/pom.xml"; then
    echo 'root POM groupId is not io.github.muer' >&2
    exit 1
fi

# 3) AutoConfiguration imports 指向 MuerAutoConfiguration。
if ! grep -qx 'io.github.muer.autoconfigure.MuerAutoConfiguration' \
    "$repository_root/muer-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"; then
    echo 'AutoConfiguration.imports does not name MuerAutoConfiguration' >&2
    exit 1
fi

# 4) 示例配置不使用旧的顶层 iam: 前缀（正式前缀为 muer:）。
if grep -rIn '^iam:' "$repository_root/muer-example/src/main/resources" 2>/dev/null; then
    echo 'legacy YAML configuration prefix remains' >&2
    exit 1
fi

echo 'MUER_IDENTITY_GUARD_OK'
