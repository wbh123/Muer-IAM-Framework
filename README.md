# 木耳 Muer

**Identity infrastructure that grows naturally with your applications.**

让身份能力，在每个系统中自然生长。  
Grow quietly. Connect steadily.

Muer 是面向 Spring Boot 应用的可嵌入式身份与访问管理（Identity and Access Management，IAM）框架。它把认证、细粒度授权、权限范围、会话治理、审计、授权诊断与管理能力收敛到一套统一模型中，同时保留宿主应用对用户、密码和业务资源的所有权。

> 当前仓库仍是 `0.1.0-SNAPSHOT` Release Candidate。正式 `v0.1.0` Tag、GitHub Release 与 Maven 发布尚未创建。

## Why Muer

业务系统在持续演进后，很容易出现这些问题：

- Controller、Service、数据库里各自维护角色与权限判断；
- 同一个“管理员”在不同系统里含义不同；
- 权限能判断，却很难解释“为什么被拒绝”；
- Token、Session 与权限变更之间缺少统一治理；
- 权限分配页面、审计页面和下线能力在每个系统里重复开发。

Muer 将它们收敛为一条清晰链路：

```text
开发者声明 Permission
        ↓
Muer 注册 Permission
        ↓
管理员配置 Template / Profile / Scope
        ↓
AuthorizationEngine 做每一次授权判断
        ↓
Audit / Diagnostics 解释发生了什么
```

## Core capabilities

- **Authentication**：凭据验证交给宿主 `IdentityAuthenticator`，Muer 负责登录结果、Opaque Token 与会话。
- **Permission Registration**：业务代码通过 `PermissionDefinitionProvider` 声明原子权限，启动后自动幂等注册到 Muer。
- **Authorization**：统一评估 Permission、Profile、Resource Scope 与扩展 Policy；Role 只是可选元数据，不是最终放行条件。
- **Permission Template**：通过不可变版本组织权限集合，使授权变更可追踪。
- **Profile**：把用户、模板版本、客户端类型和有效期组合成实际授权身份。
- **Resource Scope**：把“能做什么”和“能在哪些资源上做”分离，并区分 READ / WRITE。
- **Session**：MySQL 保存持久状态，Redis 保存 Token 快速索引，支持独立撤销和授权版本失效。
- **Audit & Diagnostics**：记录关键操作，并返回真实授权决策步骤。
- **Management API**：通过 `/iam/**` 提供用户、Identity、Permission、Template、Profile、Scope、Session、Audit 等管理接口。
- **Admin Console**：可选 Vue 3 管理控制台；不用控制台也可以只消费 Starter 或直接调用 Management API。
- **Observability**：可选接入 Spring Boot Actuator 与 Micrometer，提供 Muer Health 与认证/授权/会话等运行指标。

## Quick links

- [快速开始](docs/QUICK_START.md)
- [接入指南](docs/IAM_INTEGRATION_GUIDE.md)
- [Public API / SPI](docs/PUBLIC_API.md)
- [Admin Console 部署](docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md)
- [0.1.0 Release Notes](docs/RELEASE_NOTES_0.1.0.md)
- 文档站源码：[`iam-docs/`](iam-docs/)；正式站点目标：`https://muer.github.io`

## Five-minute integration

### 1. 引入 Starter

```xml
<dependency>
    <groupId>io.github.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

普通宿主应用只需要依赖 `muer-spring-boot-starter`。`iam-admin-web` 与 `iam-docs` 都不是运行时依赖。

### 2. 配置 MySQL、Redis 与 Muer

Docker 不是前置条件。可以使用本机、局域网、云服务或容器中的 MySQL 8.x / Redis 7。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
  data:
    redis:
      host: 127.0.0.1
      port: 6379

muer:
  enabled: true
  schema:
    enabled: true
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  client-types:
    - WEB
```

默认 `muer.schema.enabled=true` 时，Muer 使用独立 Flyway history 自动创建所需 `iam_*` 表；不要求接入者手工维护框架内部表结构。

### 3. 接入宿主身份

Muer 不接管你的用户密码表。业务系统继续验证自己的用户名、密码、员工账号或其他身份源，只需提供：

```java
@Bean
IdentityAuthenticator identityAuthenticator(AccountGateway accounts) {
    return request -> accounts.verify(request.username(), request.password())
            .map(account -> account.toIamPrincipal(request.clientType()));
}
```

### 4. 声明业务 Permission

开发者负责定义“系统有哪些能力”，管理员负责决定“谁拥有这些能力”。

```java
@Bean
PermissionDefinitionProvider documentPermissions() {
    return () -> List.of(
            new PermissionDefinition("document:read", "查看文档", "读取文档内容"),
            new PermissionDefinition("document:update", "编辑文档", "修改文档内容"));
}
```

应用就绪后，Muer 会创建缺失 Permission、更新显示元数据，并保持幂等；不会因为当前 Provider 未声明某个历史 Permission 就自动删除它。

Admin Console 的 Permission Explorer 展示的是已经注册到 Muer 的权限。**管理员负责选择和分配 Permission，而不是随意发明新的 Permission Code。**

### 5. 描述业务资源并保护接口

Muer 不读取宿主业务表。宿主通过 `ResourceHierarchyProvider` / `MvcResourceDescriptorResolver` 告诉 Muer 业务资源之间的关系，例如：

```text
Document 1001
    ↓ belongs to
Project 101
```

Controller 可以直接声明：

```java
@PostMapping("/api/documents/{id}")
@RequirePermission("document:update")
public Document update(@PathVariable String id, @RequestBody DocumentUpdate request) {
    return documentService.update(id, request);
}
```

最终判断仍由 `AuthorizationEngine` 完成，而不是前端菜单、Role 名称或 Controller 中的手工 `if`。

## How permissions are managed

接入 Muer 后，通常**不需要再为每个业务系统自己开发一套角色/授权后端**。

```text
业务代码
PermissionDefinitionProvider
        ↓
Permission Registry
        ↓
Permission Template Version
        ↓
Authorization Profile
        ↓
Resource Scope
        ↓
User / Session
```

管理员有两种正式方式：

1. 使用随 0.1.0 提供的 **IAM Admin Console**；
2. 如果企业已有统一后台，直接调用 **Muer Management API** 集成到自己的页面。

不建议直接修改 `iam_*` 表；MyBatis Mapper、数据库表结构和自动配置内部 Bean 不属于稳定消费边界。

## IAM Admin Console

`iam-admin-web/` 是 0.1.0 随附的**可选管理客户端**，使用 Vue 3 + TypeScript + Element Plus，并从 `muer-management-web/src/main/resources/openapi/iam.yaml` 自动生成 TypeScript Client。

当前覆盖：

```text
Dashboard
Users / Identities
Permissions
Permission Templates
Profiles / Scopes
Sessions
Audit
Diagnostics
Account
```

所有 `/iam/admin/**` 后端请求仍会重新通过 `AuthorizationEngine` 检查细粒度 `iam.admin.*` 权限。菜单隐藏和路由 Guard 只是用户体验，不是安全边界。

### 本地手工验收

使用 `muer-example` 时，开发演示管理员必须显式启用：

```text
SPRING_PROFILES_ACTIVE=dev
MUER_EXAMPLE_SEED_ADMIN=true
```

登录：

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

生产环境不会自动创建该账号，也不存在公开的管理员 Bootstrap HTTP 后门。

前端启动：

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

完整 Nginx、首个生产管理员与安全配置见 [IAM Admin Console Deployment](docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md)。

## Runtime observability

Admin Console Dashboard 和 Actuator 解决的是两个不同问题：

- **Admin Console**：查看用户、Session、Profile、Audit 等 IAM 治理状态；
- **Actuator / Micrometer**：查看框架是否加载、认证/授权行为和运行指标。

宿主项目没有 Actuator / Micrometer 时，Muer 仍可正常工作；相关集成是条件化、可选的。

当 Spring Boot Health 能力存在时，Muer 会贡献 `muer` Health Indicator。数据库与 Redis 连通性仍由宿主 Spring Boot 自带的 DataSource / Redis Health 检查负责，Muer 不重复做昂贵探测。

Micrometer 可用时，目前会记录：

```text
muer.authentication.attempts
muer.authorization.decisions
muer.authorization.duration
muer.sessions.created
muer.sessions.revoked
muer.token.lookups
```

指标只使用有限、低基数维度，不把用户名、用户 ID、Session ID、Token 或资源 ID作为 Tag。

详细说明见文档站 `Operations → Observability`。

## HTTP entry points

快速开始不要求使用 `curl`。可以用 Postman、Apifox、IDE HTTP Client 或业务前端调用：

| 用途 | 方法 | 路径 | 最小预期 |
| --- | --- | --- | --- |
| 登录 | POST | `/iam/auth/login` | `200` + opaque token / session |
| 当前 Principal | GET | `/iam/auth/me` | `200` |
| 当前能力 | GET | `/iam/auth/capabilities` | `200` |
| Profile 切换 | POST | `/iam/authorization/profiles/{profileId}/switch` | `200` + 新 token/session |
| 自身授权诊断 | POST | `/iam/authorization/diagnostics` | `200` + AuthorizationDecision |
| 我的 Sessions | GET | `/iam/sessions` | `200` |

`POST /iam/authorization/diagnostics` 始终是**当前已认证 Principal 的自诊断**，不是管理员专属接口，也不能指定其他用户/Profile。

## Modules

应用通常只消费 Starter：

```text
muer-core
muer-authentication
muer-authorization
muer-session
muer-audit
muer-diagnostics
muer-persistence-mybatis
muer-management-web
muer-spring-boot-autoconfigure
muer-spring-boot-starter   ← 普通业务应用入口
muer-example
muer-tests

iam-admin-web              ← 可选管理客户端
iam-docs                   ← 文档站
```

HTTP 路径 `/iam/**`、数据库表 `iam_*` 与管理 Permission `iam.admin.*` 继续保留，因为它们表达 IAM 领域协议；Muer 是品牌、Java/Maven 命名空间和 Spring 配置身份。

## Verification boundary

普通接入者不需要运行仓库的完整 CI、Docker 或 Testcontainers。框架维护侧会验证：

- Muer 项目身份与遗留标识扫描；
- Spring Boot AutoConfiguration 与 Starter Smoke；
- Management API；
- Independent Consumer；
- MySQL + Redis Testcontainers Showcase；
- Admin Console OpenAPI 生成、类型检查、Vitest、生产构建；
- Astro/Starlight 文档检查与静态构建。

用户侧只需要确认自己的应用：MySQL / Redis 可连接、应用启动成功、登录成功，以及至少一个受保护接口的 Allow / Deny 结果符合预期。

## Documentation

建议阅读顺序：

1. [Quick Start](docs/QUICK_START.md)
2. [IAM Integration Guide](docs/IAM_INTEGRATION_GUIDE.md)
3. 文档站“定义权限 / 权限管理”
4. 文档站“运维 / Observability”
5. [Public API Reference](docs/PUBLIC_API.md)

## Project maintenance

- [Contributing](CONTRIBUTING.md)
- [Release policy](docs/RELEASE_POLICY.md)
- [Security policy](SECURITY.md)
- [Migration guide](MIGRATION.md)

## License

Muer is licensed under the [Apache License 2.0](LICENSE).
