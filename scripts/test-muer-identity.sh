#!/usr/bin/env bash
# 校验已迁移源码不会重新引入旧 Muer 前命名空间（品牌迁移守卫）。
#
# 使用可移植的 grep（而非 ripgrep），以便在任意 GitHub Actions runner 上执行。
# rg 在部分 ubuntu-latest 镜像上并不随镜像提供，导致此守卫此前以 exit 127 失败。
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# The final cutover forbids the transitional Java/Maven namespace in runtime
# code, POMs, generated imports, and active documentation. Keep the former
# literal split across variables so this guard does not match its own source.
former_namespace="io.github.$(printf '%s' 'muer')"
former_website="https://muer.$(printf '%s' 'github.io')"
former_repository="github.com/wbh123/$(printf '%s' 'iam')"
legacy_admin_dir="iam-$(printf '%s' 'admin-web')"
legacy_docs_dir="iam-$(printf '%s' 'docs')"
legacy_hits="$(
  grep -rInF "$former_namespace" "$repository_root" \
    --exclude-dir=.git --exclude-dir=.worktrees --exclude-dir=.workbuddy \
    --exclude-dir=.superpowers \
    --exclude-dir=node_modules \
    --exclude-dir=dist --exclude-dir=target --exclude-dir=.astro \
    --exclude-dir=scripts \
    2>/dev/null \
  | grep -v '/MIGRATION.md:' \
  || true
)"
if [ -n "$legacy_hits" ]; then
  echo 'former Java/Maven namespace remains in active project files:' >&2
  printf '%s\n' "$legacy_hits" >&2
  exit 1
fi

for obsolete in "$former_website" "$former_repository" "$legacy_admin_dir" "$legacy_docs_dir"; do
  if grep -rInF "$obsolete" "$repository_root" \
      --exclude-dir=.git --exclude-dir=.worktrees --exclude-dir=.workbuddy \
      --exclude-dir=.superpowers \
      --exclude-dir=node_modules \
      --exclude-dir=dist --exclude-dir=target --exclude-dir=.astro \
      --exclude-dir=scripts \
      2>/dev/null | grep -v '/MIGRATION.md:'; then
    echo "obsolete active identity remains: $obsolete" >&2
    exit 1
  fi
done

# 1) 禁止旧品牌 Java 命名空间出现在运行时代码与当前使用文档中。
#    允许的位置仅限历史迁移文档：MIGRATION.md、db 迁移。
#    旧命名空间 = 'io.github' + '.iamstarter'（此处不字面写出，避免自匹配）。
#    grep -I 忽略二进制；排除构建产物与其它 worktree，避免误报。
legacy_hits="$(
  grep -rInE 'io\.github\.iamstarter' "$repository_root" \
    --exclude-dir=.git \
    --exclude-dir=.worktrees \
    --exclude-dir=.workbuddy \
    --exclude-dir=.superpowers \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=target \
    --exclude-dir=.astro \
    2>/dev/null \
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
if ! grep -q '<groupId>cloud.muer</groupId>' "$repository_root/pom.xml"; then
    echo 'root POM groupId is not cloud.muer' >&2
    exit 1
fi

if ! grep -q '<url>https://muer.cloud</url>' "$repository_root/pom.xml"; then
    echo 'root POM website is not https://muer.cloud' >&2
    exit 1
fi
if ! grep -q 'repositoryName: wbh123/Muer-IAM-Framework' "$repository_root/metadata/project-metadata.yaml"; then
    echo 'metadata repositoryName is not wbh123/Muer-IAM-Framework' >&2
    exit 1
fi

# 3) AutoConfiguration imports 指向 MuerAutoConfiguration。
if ! grep -qx 'cloud.muer.autoconfigure.MuerAutoConfiguration' \
    "$repository_root/modules/muer-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"; then
    echo 'AutoConfiguration.imports does not name MuerAutoConfiguration' >&2
    exit 1
fi

# 4) 示例配置不使用旧的顶层 iam: 前缀（正式前缀为 muer:）。
if grep -rIn '^iam:' "$repository_root/examples/showcase/src/main/resources" 2>/dev/null; then
    echo 'legacy YAML configuration prefix remains' >&2
    exit 1
fi

echo 'MUER_IDENTITY_GUARD_OK'
