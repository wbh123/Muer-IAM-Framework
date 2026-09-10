# Muer Repository Architecture Refactor Design

## Goal

在 Muer 0.1.0 发布前，将仓库目录、Maven 模块、共享 OpenAPI 契约、用户示例、CI 验收应用、前端应用和文档站按产品职责重新组织，同时保持现有 HTTP、数据库、认证、授权和会话语义不变。

## Scope

本次重构仅处理仓库架构和构建边界：

- 将可发布 Java 模块统一放入 `modules/`。
- 将唯一 OpenAPI Source of Truth 移到 `contracts/openapi/iam.yaml`。
- 将 `muer-management-web` 改名为 `muer-http-api`，并在未发布窗口内将实现包从 `cloud.muer.web` 对齐为 `cloud.muer.http`。
- 将 `muer-example` 按面向用户的 Showcase 和 CI-only 外部 Consumer 拆分。
- 将 Admin Console 和 Docs Site 归入 `apps/`。
- 将架构测试归入 `tests/architecture`，并禁止其发布。
- 同步 Maven、Node、CI、文档、脚本和结构校验。

不在范围内：新增授权模型、RBAC/ABAC、OAuth/OIDC、Tenant、数据库迁移、Spring Boot 或 Maven 替换、`/iam/**` 路径变化、产品品牌变化、GitHub Pages 设置变更和旧 Artifact 兼容层。

## Target Layout

```text
modules/
  muer-core/
  muer-authentication/
  muer-authorization/
  muer-session/
  muer-audit/
  muer-diagnostics/
  muer-persistence-mybatis/
  muer-http-api/
  muer-spring-boot-autoconfigure/
  muer-spring-boot-starter/
contracts/openapi/iam.yaml
apps/muer-admin-console/
apps/muer-docs-site/
examples/quickstart/
examples/showcase/
test-apps/consumer-acceptance/
tests/architecture/
docs/
metadata/
scripts/
```

根 Maven Reactor 只包含 `modules/*` 和 `tests/architecture`。示例、应用和 CI-only Consumer 通过安装到本地 Maven 仓库的公开 Starter 坐标独立构建。

## Contract Ownership

`contracts/openapi/iam.yaml` 是唯一受版本控制的正式 HTTP 契约。Java OpenAPI Generator、Admin Console TypeScript Generator、文档和契约校验脚本都从该路径读取。生成的 Java/TypeScript 源码继续按当前约定在构建时生成，不成为第二事实源。

迁移前后对 `paths`、HTTP methods、`operationId`、请求/响应 schema、状态码和 security declaration 做规范化 diff；预期只发生物理路径变化。

## Java Boundary

发布 Artifact 清单保持 `cloud.muer` groupId，并将 HTTP 适配器 Artifact 改为 `muer-http-api`。Starter 依赖链保持：

```text
muer-spring-boot-starter
  -> muer-spring-boot-autoconfigure
  -> muer-http-api
```

HTTP Adapter 仍负责认证、当前用户、Session、授权诊断、Capabilities 和 Administration Management API，但不被称为前端或管理控制台。普通 Consumer 的推荐入口始终是 `cloud.muer:muer-spring-boot-starter`。

## Consumer Separation

`examples/quickstart` 保持最小，仅展示登录、一个允许、Permission Deny 和 Scope Deny。`examples/showcase` 保留可供人探索的完整能力和可读示例；它不继承 Muer Parent、不进入 Reactor，只消费公开 Starter。`test-apps/consumer-acceptance` 只服务 CI，验证真实第三方消费、MySQL/Redis Testcontainers、认证授权、Session、Profile Switch 和必要管理 API，不作为教程。

## Application Boundary

`apps/muer-admin-console` 只通过 HTTP API 工作，不引用 Java 模块内部路径；其 `api:generate` 只读取 `contracts/openapi/iam.yaml`。`apps/muer-docs-site` 保持现有 URL slug、`https://muer.cloud`、CNAME、base 和 Pages 发布语义；本次不启用或修改 GitHub Pages 设置。

## Verification and Compatibility

阶段验证包括 Java Reactor、独立 Quick Start、独立 Showcase、Consumer Acceptance、Public API Boundary、Admin Console、Docs Site、旧路径清零、单一 OpenAPI 来源和结构脚本。最终必须确认：

```text
HTTP Paths Changed: NO
OpenAPI Semantics Changed: NO
Database Schema Changed: NO
Authentication Behavior Changed: NO
Authorization Behavior Changed: NO
Session Behavior Changed: NO
```

未来是否拆分 `muer-http-api` 为 runtime 和 management 两个 Artifact，本轮只记录为 0.2.x 评估项，不在缺乏边界证据时扩大范围。

## Delivery

实施按以下独立主题提交：共享契约、Java 模块、示例/Consumer、应用目录、架构测试、CI、仓库文档。所有提交仅在 `codex/repository-architecture-refactor` 分支完成；本次不合并、不推送，除非后续收到明确指令。
