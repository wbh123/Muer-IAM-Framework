# Muer 0.1.0 发布阻塞清单

本清单只记录影响 0.1.0 事实一致性、首次接入安全性和产品闭环的事项。

| 级别 | 事项 | 状态 | 验证边界 |
| --- | --- | --- | --- |
| P0 | 无 | RESOLVED | 0 个 |
| P1 | Diagnostics 请求字段与运行时不一致 | RESOLVED | 文档与契约统一使用 `domain` |
| P1 | Principal Profile 标识可空 | RESOLVED | OpenAPI 要求正数 `activeProfileId` / `templateVersionId` |
| P1 | Management lifecycle 不完整 | RESOLVED | Template、Draft Version、Publish、Profile Create API 与 Console 已具备 |
| P1 | 全新生产库 First Administrator 闭环 | RESOLVED | 宿主显式调用 `MuerAdministrationBootstrapService`；Profile 含 `IAM_ADMIN / *` READ/WRITE Scope，无公开 Bootstrap HTTP 接口 |
| P1 | Quick Start Seeder 破坏 unrelated 数据 | RESOLVED | 固定 Demo 范围清理、ID/业务键冲突 fail-fast、H2 Safety Test |
| P2 | Quick Start 与 From Zero 内容重叠 | RESOLVED | Quick Start 运行现成示例；手写接入说明位于 From Zero |
| P2 | Admin Console 教程与 HEAD 漂移 | RESOLVED | 文档覆盖 Template、Version、Publish、Profile Create 与当前限制 |
| P2 | 重复公开教程漂移 | RESOLVED | `muer-docs/` 为事实源；根目录旧文档仅作薄兼容入口 |
| P2 | Observability 操作说明 | RESOLVED | 文档覆盖可选依赖、Actuator 暴露与验证 |

发布阻塞统计：

```text
P0: 0
P1: 0
P2 blocking release: 0
```

## Post-0.1.0

- `release/0.1.0` 分支同步应在 PR #5 合并决策后单独执行；本轮按约束不修改该分支。
- 已有 Profile 的 Template Version 切换尚未提供 Console 详情页操作；0.1.0 可通过 Management API 或新建 Profile 处理，不阻塞首次授权闭环。

## 发布验证门槛

- Starter tests；
- Management API 与 First Admin Bootstrap tests；
- Quick Start consumer 与 Seeder Safety tests；
- Admin Console OpenAPI 生成、lint/typecheck、Vitest 与生产构建；
- Astro/Starlight 文档检查与构建；
- PR HEAD 的 Verify Muer Starter、Verify Muer Admin Console、Verify Muer Documentation 全部成功。

HTTP 路径 `/iam/**`、数据库表 `iam_*` 与 Permission `iam.admin.*` 继续保留，因为它们表达 IAM 领域协议；Muer 是品牌、Java/Maven 命名空间和 Spring 配置身份。
