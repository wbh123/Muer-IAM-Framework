# Muer 0.1.0 Release Notes

## 状态

当前候选版本仍为 `0.1.0-SNAPSHOT`。仓库尚未创建 `v0.1.0` Tag、GitHub Release 或 Maven 正式发布；这些动作必须在最终 Release Gate 与人工批准之后执行。

0.1.0 采用“首版即形成完整产品闭环”的方案：**Starter、Management API、IAM Admin Console、Permission Registration、Runtime Observability 与文档站都属于 0.1.0 首发范围。**

其中 Admin Console 和 Actuator/Micrometer 集成都保持可选：只需要基础认证与授权能力的宿主应用仍然只消费 `muer-spring-boot-starter`。

## 正式技术身份

```text
Brand:             Muer / 木耳
Maven Group ID:    io.github.muer
Starter Artifact:  muer-spring-boot-starter
Java Package Root: io.github.muer
Spring Prefix:     muer
Website:           https://muer.github.io
License:           Apache License 2.0
```

出于领域协议与兼容性考虑，HTTP `/iam/**`、数据库表 `iam_*` 和管理权限 `iam.admin.*` 继续保留。

## 0.1.0 能力范围

### Authentication & Session

- 宿主身份认证适配 `IdentityAuthenticator`；
- Opaque Token 登录与 Bearer Principal 解析；
- MySQL 持久 Session + Redis Token 快速索引；
- Session 查询、独立撤销、撤销其他会话；
- Profile Switch 生成替换 Token / Session，不提升原 Token 权限；
- Authorization Version 支持权限变化后的失效治理。

### Permission Registration

业务权限不再依赖初始化 SQL 作为正式接入方式。0.1.0 新增稳定候选 SPI：

```text
PermissionDefinition
PermissionDefinitionProvider
```

宿主代码声明业务能力后，Muer 在应用就绪阶段进行校验、去重和幂等注册：

- 缺失 Permission 自动创建；
- 已存在 Permission 的显示元数据可同步更新；
- 多 Provider 对同一 Code 给出相同定义时去重；
- 同一 Code 给出冲突定义时 Fail Fast；
- 当前 Provider 未声明的历史 Permission **不会被自动删除**；
- `@RequirePermission` 引用了未注册 Permission 时提供一致性警告，而不是默认阻止启动。

管理员负责把已经声明的 Permission 组合进 Template / Profile / Scope，而不是在后台随意创造业务 Permission Code。

### Authorization

- Permission、Permission Template Version、Authorization Profile、Resource Scope 统一授权模型；
- Published Template Version 保持可追踪的版本化语义；
- READ / WRITE Scope；
- `AuthorizationEngine` 直接调用；
- `@RequirePermission` Servlet MVC 声明式授权；
- `ResourceHierarchyProvider` 和 `MvcResourceDescriptorResolver` 保持业务资源归属由宿主解释；
- Role 仍只是可选元数据，不是最终授权输入。

### Audit & Diagnostics

- 通用 Audit Event 与多 Subject 关联；
- 当前 Principal 的可解释 Authorization Diagnostics；
- `POST /iam/authorization/diagnostics` 保持 **Authenticated Self Diagnostics** 语义，不要求管理员权限，也不能指定其他用户/Profile。

### Management API & Admin Console

OpenAPI 驱动的 `/iam/**` Management API 覆盖：

```text
Users / Identities
Permissions
Permission Templates / Versions
Profiles / Scopes
Sessions
Audit
Overview
Capabilities
Diagnostics
```

`iam-admin-web` 是随 0.1.0 提供的可选 Vue 3 + TypeScript + Element Plus 管理客户端。TypeScript Client 由同一份 `muer-management-web/.../iam.yaml` 自动生成。

后台菜单和路由 Capability Guard 只负责用户体验；所有 `/iam/admin/**` 请求仍由后端 `AuthorizationEngine` 使用细粒度 `iam.admin.*` Permission 重新授权，不存在 Role / Super Admin 旁路。

### Runtime Observability

0.1.0 提供可选的 Spring Boot Actuator / Micrometer 集成。

宿主未引入对应能力时，Muer 仍然正常运行；检测到相关类时自动贡献：

- `muer` Health Indicator，用于说明 Muer 框架自动配置已经可用；
- `muer.authentication.attempts`；
- `muer.authorization.decisions`；
- `muer.authorization.duration`；
- `muer.sessions.created`；
- `muer.sessions.revoked`；
- `muer.token.lookups`。

MySQL / Redis 连通性继续由宿主 Spring Boot 的 DataSource / Redis Health 负责，Muer 不重复执行昂贵探测。运行指标不使用用户名、用户 ID、Token、Session ID、资源 ID 等高基数或敏感 Tag。

### Documentation

`iam-docs` 使用 Astro + Starlight，当前文档体系覆盖：

- Quick Start；
- MySQL / Redis 手动配置；
- `IdentityAuthenticator`；
- Permission Definition / Registration；
- Resource Resolver 与 `@RequirePermission`；
- Template / Profile / Scope 权限管理；
- Admin Console；
- Session / Audit / Diagnostics；
- 手动部署；
- Runtime Observability；
- Public API 与迁移说明。

Quick Start 不要求使用大量 `curl` / `jq`，接口示例主要按请求方式、路径、请求体和预期结果描述。Docker 不是用户部署前置条件。

## 管理员初始化边界

`muer-example` 可以显式创建开发演示管理员，但必须同时满足开发 Profile 与显式 Seed 开关。

演示账号：

```text
admin-demo / demo-pass / WEB
```

该账号不会在生产环境自动创建，Muer 也不提供公开的管理员 Bootstrap HTTP 后门。生产第一个管理员必须通过受控 SQL / Migration / Deployment Seeder 或宿主 Initial Provisioning 建立。

## 依赖与运行基线

- Java 21；
- Spring Boot 4；
- MySQL 8.x（发布验证使用 MySQL 8.4）；
- Redis 7；
- Admin Console：Node.js 22+；
- Docs：Node.js 22+。

普通 Starter 使用者不需要安装 Docker，也不需要执行仓库 Testcontainers 或完整 Maven Reactor 验证。

## 已知边界与非目标

- 0.1.0 不新增 OAuth 2.0、OpenID Connect、SAML、LDAP 或单点登录协议；
- 不把 Muer 拆成强制独立身份服务；
- 默认 `ResourceHierarchyProvider` 不猜测业务资源关系，使用 Scope 前必须由宿主适配；
- `/iam/**` 使用独立 Stateless Security Chain；宿主业务路由仍由宿主 Security Chain 管理；
- Admin Console 当前具备后端集成测试、OpenAPI 生成、TypeScript 类型检查、Vitest 和生产构建，完整 Playwright 浏览器端到端测试延后；
- Actuator / Micrometer 是可选运行时集成，不是 Starter 强制依赖。

## Release Gate

正式 `v0.1.0` 前，最终目标 HEAD 至少必须证明以下远程检查全部成功：

- Verify IAM Starter；
- Verify IAM Management API；
- Independent Consumer acceptance；
- Docker/Testcontainers consumer showcase；
- Verify IAM Admin Console；
- Verify IAM Documentation。

并确认：

```text
Apache License 2.0      ✅
Muer namespace          ✅
Permission Registration ✅
Runtime Observability   ✅
Admin Console           ✅
Public Documentation    ✅
P0                      0
P1                      0
```

使用从 [Quick Start](QUICK_START.md) 开始；完整消费边界见 [Public API Reference](PUBLIC_API.md)，管理控制台部署见 [IAM Admin Console Deployment](IAM_ADMIN_CONSOLE_DEPLOYMENT.md)。
