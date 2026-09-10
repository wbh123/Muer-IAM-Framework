# Muer 0.1.0 发布阻塞清单

本清单区分软件缺陷、发布工程证据和仓库所有者操作。所有者操作未完成
时，不应把版本标记为可冻结；但它们不等同于运行时软件缺陷。

| 级别 | 事项 | 状态 | 验证边界 |
| --- | --- | --- | --- |
| P0 | 无 | RESOLVED | 当前候选未发现阻断性安全或正确性缺陷 |
| P1 | Diagnostics 请求字段与运行时不一致 | RESOLVED | 文档与契约统一使用 `domain` |
| P1 | Principal Profile 标识可空 | RESOLVED | OpenAPI 要求正数 `activeProfileId` / `templateVersionId` |
| P1 | Management lifecycle 不完整 | RESOLVED | Template、Draft Version、Publish、Profile Create API 与 Console 已具备 |
| P1 | 全新生产库 First Administrator 闭环 | RESOLVED | 宿主显式调用 `MuerAdministrationBootstrapService`；无公开 Bootstrap HTTP 接口 |
| P1 | Quick Start Seeder 破坏 unrelated 数据 | RESOLVED | 固定 Demo 范围清理、冲突 fail-fast、Safety Test |
| P2 | Quick Start 与 From Zero 内容重叠 | RESOLVED | Quick Start 运行现成示例；手写接入说明位于 From Zero |
| P2 | Admin Console 教程与 HEAD 漂移 | RESOLVED | 文档覆盖当前 Template、Version、Publish、Profile Create 流程 |
| P2 | 重复公开教程漂移 | RESOLVED | 文档事实源和兼容入口已统一 |
| P2 | Observability 操作说明 | RESOLVED | 文档覆盖可选依赖、Actuator 暴露与验证 |
| P2 | Maven Sources/Javadoc dry-run 证据 | OPEN | 配置已提交；首次 workflow 已完成 Maven staging 但在制品检查阶段失败，修复后仍需重新验证 |
| P2 | 发布制品检查器误判 `.env.example` | RESOLVED | 首次 dry-run `34450935438` 暴露误报，已在 `c3dacba` 放行文档示例文件；需重新执行 dry-run |
| P2 | 修复后的 Maven Central dry-run | OPEN | 重新运行 `Verify Muer Maven Release` 并确认 Sources/Javadoc、白名单和 Central staging 全部通过 |
| P2 | Central namespace、token、GPG key | OWNER ACTION REQUIRED | 需由仓库所有者在 Central Portal/GitHub Secrets 完成，代码仓库不保存秘密 |

## 当前候选基线

```text
main                     c3dacba0e96683239129f5fb262668091c8c5ebd
release/0.1.0             c3dacba0e96683239129f5fb262668091c8c5ebd
release-prep/maven-central-0.1.0
                           c3dacba0e96683239129f5fb262668091c8c5ebd
```

`release/0.1.0` 已重新对齐当前可信 `main` 基线。未创建 `v0.1.0` 标签、
GitHub Release，也未向 Maven Central 上传。

## 发布阻塞统计

```text
P0: 0
P1 software blockers: 0
P2 software blockers: 0
Owner-only release setup: OPEN
Evidence gate (Sources/Javadoc/signature dry-run): OPEN
```

## 发布验证门槛

- Root Starter reactor、Management API、QuickStart、Consumer Acceptance 和
  Showcase 验证全部通过；
- Admin Console 和 Astro/Starlight 文档工作流在候选 SHA 上成功；
- Maven Central 发布 profile 生成主 Jar、Sources Jar、Javadoc Jar，并由
  artifact guard 检查发布白名单和敏感文件边界；
- `cloud.muer` namespace 已由所有者验证，Central token 和 GPG secrets 已
  配置；
- 手动 `Verify Muer Maven Release` workflow 以 `dry_run=true` 成功完成；
- 以上证据齐全后，才可冻结版本并另行批准 tag、GitHub Release 和正式上传。

HTTP 路径 `/iam/**`、数据库表 `iam_*` 与 Permission `iam.admin.*` 继续保留，
因为它们表达 IAM 领域协议；Muer 是品牌、Java/Maven 命名空间和 Spring 配置
身份。
