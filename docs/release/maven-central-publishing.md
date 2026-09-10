# Maven Central 发布准备

Muer 0.1.0 的 Maven 版本在正式版本冻结前保持 `0.1.0-SNAPSHOT`。本仓库只提交发布模型和 dry-run 验证，不保存凭据、不创建 Tag、不创建 GitHub Release，也不在普通 push 上自动发布。

## 发布模型

项目使用 Maven Central Portal 的 `org.sonatype.central:central-publishing-maven-plugin:0.11.0`，不使用旧 OSSRH/Nexus Staging 地址。Source/Javadoc/GPG 位于 `central-release` profile，Portal 扩展单独位于 `central-publish` profile，只有两者同时启用才会构造 Central bundle：

```bash
mvn -B -Pcentral-release,central-publish \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  clean deploy
```

上面的命令会生成主 Jar、Sources Jar、Javadoc Jar，并在本地构造 Central staging 内容；`central.skipPublishing=true` 保证不会上传。只验证发布制品而不解析 Portal 插件时可运行：

```bash
mvn -B -Pcentral-release -Dgpg.skip=true clean verify
```

普通开发仍使用：

```bash
mvn -B verify
```

普通构建不加载 Central 插件、不需要 GPG 私钥或 Central 凭据。

## 发布白名单

Central 发布集合只包含：

- `cloud.muer:muer-parent`（POM）
- `cloud.muer:muer-core`
- `cloud.muer:muer-authentication`
- `cloud.muer:muer-authorization`
- `cloud.muer:muer-session`
- `cloud.muer:muer-audit`
- `cloud.muer:muer-diagnostics`
- `cloud.muer:muer-persistence-mybatis`
- `cloud.muer:muer-http-api`（Advanced / framework composition）
- `cloud.muer:muer-spring-boot-autoconfigure`
- `cloud.muer:muer-spring-boot-starter`（Primary Consumer Artifact）

`tests/architecture`、`examples/*`、`apps/*`、`test-apps/*` 不属于发布集合。普通宿主应用仍只需要依赖 `cloud.muer:muer-spring-boot-starter`。

## Owner-only setup

仓库所有者在 Maven Central Portal 中完成以下操作，代码仓库不记录结果或敏感值：

1. 注册并验证 `cloud.muer` namespace。Central Portal 可能要求 DNS TXT 或其他所有权验证；当前状态应记录为 `VERIFIED` 或 `OWNER ACTION REQUIRED`，不能由代码推断。
2. 创建 Central user token，并在 GitHub Actions secrets 中配置：
   - `MAVEN_CENTRAL_USERNAME`
   - `MAVEN_CENTRAL_PASSWORD`
3. 创建用于发布的 OpenPGP 密钥，将 ASCII-armored private key 配置为 `MAVEN_GPG_PRIVATE_KEY`，将 passphrase 配置为 `MAVEN_GPG_PASSPHRASE`。
4. 将公钥发布到适当的 OpenPGP key server，并确认 Central 能够验证签名。

上述值不得写入仓库、`settings.xml`、`.env`、日志或示例配置。`release-maven.yml` 仅通过 GitHub Secrets 读取它们。

## 手动验证与正式发布

GitHub Actions 的 `Verify Muer Maven Release` 只允许 `workflow_dispatch`：

- `dry_run=true`（默认）：运行普通构建、生成 Sources/Javadoc、构造本地 Central staging，并执行 `scripts/verify-maven-release-artifacts.sh`；不会上传。
- `dry_run=false`：仅在版本已经冻结、Tag/Release 策略已获批准且所有 owner secrets 已配置后使用；该模式会导入签名密钥并执行 `mvn -Pcentral-release deploy`。

正式流程应为：

```text
RC 验收 → owner 完成 namespace/token/GPG 配置 → 版本冻结
→ release commit → v0.1.0 tag → GitHub Release
→ 手动发布工作流（dry_run=false）→ Central Portal 校验/发布
```

本轮不执行最后一行。

## 官方依据

- [Central Portal Maven Plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [Central Repository Requirements](https://central.sonatype.org/publish/requirements/)
- [Register a Namespace](https://central.sonatype.org/register/namespace/)

Central 要求非 POM 制品提供 Sources/Javadoc，并要求部署文件包含校验和和 GPG/PGP 签名；插件负责 Central Portal bundle/checksum 流程，但不会替项目自动生成全部发布前提条件，因此本仓库仍单独配置 source、Javadoc 和 signing profile。
