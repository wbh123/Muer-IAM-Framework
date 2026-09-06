# Muer 0.1.0 Quick Start

本文面向第一次接入 Muer 的 Spring Boot 开发者。目标不是复现仓库的完整测试，而是在一个真实业务应用里完成最短闭环：

```text
Starter
→ MySQL / Redis
→ IdentityAuthenticator
→ PermissionDefinitionProvider
→ Resource Resolver
→ @RequirePermission
→ 登录 / 授权
→ 权限管理（可选 Console）
→ 运行状态（可选 Actuator / Micrometer）
```

当前版本仍为 `0.1.0-SNAPSHOT` Release Candidate，尚未创建正式 Maven Release。

> **Docker 不是前置条件。** MySQL 与 Redis 可以来自本机、局域网、云服务或容器。仓库中的 Docker Compose 只是一种可选的本地便利方式。

## 1. 前置条件

- Java 21；
- Spring Boot 4；
- Maven；
- MySQL 8.x（发布基线验证使用 MySQL 8.4）；
- Redis 7。

如果还要使用 IAM Admin Console，需要 Node.js 22+ 与 npm。

普通使用者不需要运行仓库中的 Testcontainers、Independent Consumer 或完整 Maven Reactor 验证。

## 2. 引入 Starter

业务应用通常只需要一个依赖：

```xml
<dependency>
    <groupId>io.github.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

不要为了接入 Muer 直接依赖 MyBatis Mapper、内部自动配置实现或 `internal` 包。

## 3. 准备 MySQL

Muer 默认复用宿主应用的 `DataSource` 保存 Permission、Template、Profile、Scope、Session 与 Audit 数据。

可以手工准备数据库，例如：

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

生产环境请使用真实的密码管理与最小权限策略，不要直接照搬示例凭据。

默认：

```yaml
muer:
  schema:
    enabled: true
```

Muer 会执行 `classpath:db/iam/migration` 中的 Flyway Migration，并使用独立的 `iam_flyway_schema_history`。因此首次接入通常不需要手工创建 `iam_*` 表。

如果组织已经由 DBA、Liquibase 或统一迁移平台管理同一套 Schema，再设置：

```yaml
muer:
  schema:
    enabled: false
```

## 4. 准备 Redis

Redis 用作 Opaque Token 的快速索引。已有 Redis 7 服务可以直接使用，不需要预创建 Key。

| 配置 | 示例 | 说明 |
| --- | --- | --- |
| Host | `127.0.0.1` | Redis 地址 |
| Port | `6379` | Redis 端口 |
| Password | 按环境设置 | 开启认证时配置 |
| Database | `0` | 可使用 Spring Boot 默认值 |
| Redis Prefix | `my-app:iam` | 多应用共享 Redis 时建议应用隔离 |

生产 Redis 不应直接暴露公网。

## 5. 配置 Spring Boot

最小配置示例：

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
      password: ${IAM_REDIS_PASSWORD:}

muer:
  enabled: true
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
  client-types:
    - WEB
```

Muer 是品牌与 Spring 配置身份；HTTP `/iam/**`、数据库 `iam_*` 和管理 Permission `iam.admin.*` 继续保留为 IAM 领域协议。

## 6. 接入宿主身份

Muer 不要求迁移你的用户表，也不保存宿主密码。业务系统继续负责验证自己的用户名、密码、员工账号、学生账号或其他身份源。

只需提供 `IdentityAuthenticator`：

```java
@Bean
IdentityAuthenticator identityAuthenticator(AccountGateway accounts) {
    return request -> accounts.verify(request.username(), request.password())
            .map(account -> new IamPrincipal(
                    account.id(),
                    account.identityKey(),
                    account.identityDomain(),
                    account.activeProfileId(),
                    account.templateVersionId(),
                    request.clientType(),
                    account.authorizationVersion()));
}
```

`muer.client-types` 由 Muer 统一校验；宿主 `IdentityAuthenticator` 不应再复制一份 Client Type 白名单。

## 7. 声明业务 Permission

业务权限的正式来源是代码中的 `PermissionDefinitionProvider`，而不是初始化 SQL 或后台随意创建的字符串。

```java
@Bean
PermissionDefinitionProvider documentPermissions() {
    return () -> List.of(
            new PermissionDefinition("document:read", "查看文档", "读取文档内容"),
            new PermissionDefinition("document:update", "编辑文档", "修改文档内容"));
}
```

应用就绪后，Muer 会：

- 创建缺失 Permission；
- 更新已存在 Permission 的显示元数据；
- 对相同 Code + 相同定义去重；
- 对相同 Code + 冲突定义 Fail Fast；
- 保留当前 Provider 没有声明的历史 Permission，不自动删除；
- 对 `@RequirePermission` 引用了未注册 Permission 的情况给出一致性警告。

开发者负责声明“系统有哪些能力”；管理员负责决定“谁拥有这些能力”。

## 8. 接入业务资源范围

如果需要 Department、Project、Order、Document 等资源范围授权，宿主提供 `ResourceHierarchyProvider`：

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

使用声明式 MVC 授权时，再提供 `MvcResourceDescriptorResolver`，把业务 URL / 参数映射为 `ResourceDescriptor`。

例如：

```text
Document 1001
→ belongs to
Project 101
```

Muer 不读取你的业务 Document / Project 表。宿主解释资源归属，Muer 只负责授权判断。

## 9. 保护业务接口

```java
@PostMapping("/api/documents/{id}")
@RequirePermission("document:update")
public Document update(@PathVariable String id, @RequestBody DocumentUpdate request) {
    return documentService.update(id, request);
}
```

最终 Allow / Deny 始终由 `AuthorizationEngine` 决定，不应该重新在 Controller 中写 Role 判断。

## 10. 启动应用

可以直接通过 IDE 或业务项目自己的 JAR / 服务方式启动。

首次启动建议确认：

- MySQL 可以连接；
- Redis 可以连接；
- `muer.schema.enabled=true` 时 IAM Schema Migration 成功；
- Permission Definition 注册完成；
- Spring Boot 应用正常启动。

到这里无需执行 Muer 仓库的完整自动测试。

## 11. 用 HTTP 接口确认认证链路

可以使用 Postman、Apifox、IDE HTTP Client、前端应用等工具，不要求 `curl` / `jq`。

### 登录

**方法**：`POST`  
**路径**：`/iam/auth/login`

请求体示例：

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

预期：

- 合法凭据返回 HTTP `200`；
- 响应包含 `accessToken`、`sessionId`、`expiresAt` 与 `principal`。

### 当前 Principal

**方法**：`GET`  
**路径**：`/iam/auth/me`  
**认证**：`Authorization: Bearer <accessToken>`

预期：HTTP `200`，返回当前 `IamPrincipal`。

## 12. 验证权限与 Resource Scope

`muer-example` 的 Document 场景可用于理解：

| 请求 | Reader | Editor | 含义 |
| --- | --- | --- | --- |
| `GET /api/documents/1001` | `200` | `200` | Project 101 范围内读取 |
| `POST /api/documents/1001` | `403` | `200` | 写入需要 `document:update` + WRITE Scope |
| `GET /api/documents/2001` | `403` | `403` | Document 2001 属于 Project 202，超出 Scope |

这说明 Permission 解决“能做什么”，Resource Scope 解决“能在哪里做”。

## 13. 切换 Profile

**方法**：`POST`  
**路径**：`/iam/authorization/profiles/{profileId}/switch`

预期：HTTP `200`，返回新的 Token 与新的 Session。

Profile Switch 不会提升或覆盖旧 Token；原 Token 继续保持原授权身份。

## 14. 查看授权诊断

**方法**：`POST`  
**路径**：`/iam/authorization/diagnostics`

请求示例：

```json
{
  "permissionCode": "document:update",
  "domain": "EXAMPLE",
  "clientType": "WEB",
  "resourceType": "PROJECT",
  "resourceId": "101",
  "scopeAccess": "WRITE"
}
```

预期：HTTP `200`，返回当前 Principal 的真实 `AuthorizationDecision`。

该接口始终是 **Authenticated Self Diagnostics**：只诊断当前登录 Principal，不是管理员专属接口，也不能指定别人。

## 15. 撤销 Session

**方法**：`POST`  
**路径**：`/iam/sessions/{sessionId}/revoke`

预期：成功返回 HTTP `204`；该 Session 的 Token 之后访问受保护接口返回 `401`，其他独立 Session 不被连带撤销。

## 16. 权限如何管理

接入 Muer 后，一般不需要自己再开发一套授权管理后端。

```text
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

两种正式管理方式：

1. 使用随 0.1.0 提供的 `iam-admin-web`；
2. 企业已有统一后台时，调用 Muer Management API 做自己的前端。

不要直接操作 `iam_*` 表。

## 17. 手工验收 IAM Admin Console（可选）

### 准备开发管理员

使用 `muer-example` 时，只有同时设置：

```text
SPRING_PROFILES_ACTIVE=dev
MUER_EXAMPLE_SEED_ADMIN=true
```

才会创建：

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

生产不会自动创建该账户，也不存在公开 Bootstrap Admin HTTP 接口。

### 启动前端

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

浏览器打开 Vite 输出地址，通常是 `http://localhost:5173`。

建议手工确认：登录、Dashboard、Users、Profiles、Scopes、Sessions、Audit、Diagnostics、403 路由和 Logout。

完整说明见 [IAM_ADMIN_CONSOLE_DEPLOYMENT.md](IAM_ADMIN_CONSOLE_DEPLOYMENT.md)。

## 18. 查看运行状态（可选）

如果宿主项目引入 Spring Boot Actuator / Micrometer，Muer 会条件化提供运行可观测能力；未引入时不会影响 Starter。

### Health

Muer 会贡献 `muer` Health Indicator，用来说明框架自动配置已加载。

MySQL / Redis 连通性继续由宿主 Spring Boot 的 DataSource / Redis Health 检查负责，Muer 不重复执行昂贵探测。

### Metrics

目前可记录：

```text
muer.authentication.attempts
muer.authorization.decisions
muer.authorization.duration
muer.sessions.created
muer.sessions.revoked
muer.token.lookups
```

不会使用 username、userId、sessionId、token 或 resourceId 作为指标 Tag。

详细说明见文档站 `Operations → Observability`。

## 19. 直接运行仓库示例

`muer-example` 提供 Alice / Reader / Editor 示例。

常用环境变量：

| 环境变量 | 示例 |
| --- | --- |
| `IAM_EXAMPLE_JDBC_URL` | `jdbc:mysql://127.0.0.1:3306/iam_example` |
| `IAM_EXAMPLE_DB_USERNAME` | `iam` |
| `IAM_EXAMPLE_DB_PASSWORD` | `your-password` |
| `IAM_EXAMPLE_REDIS_HOST` | `127.0.0.1` |
| `IAM_EXAMPLE_REDIS_PORT` | `6379` |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `MUER_EXAMPLE_SEED_DEMO` | `true` |

`QuickStartDemoSeeder` 会重置演示 IAM 投影，只能用于专用本地演示数据库。

没有现成 MySQL / Redis 时可以选择 `examples/quickstart/docker-compose.yml`，但它只是可选工具。

## 20. 什么时候算接入成功

普通 Starter 用户做到以下几点即可：

- 应用正常启动；
- MySQL、Redis 连接正常；
- IAM Schema Migration 成功或已经由组织部署流程管理；
- Permission 能从 `PermissionDefinitionProvider` 注册；
- 合法用户可以登录；
- `GET /iam/auth/me` 返回正确 Principal；
- 至少一个业务接口能够得到预期的 Allow / Deny；
- 如使用 Admin Console，主要管理页面与写操作可正常使用；
- 如启用 Actuator / Micrometer，可以查看 Muer Health 与运行指标。

不需要为了证明 Muer 能工作而执行仓库的全量测试。

更多内容见：

- [IAM_INTEGRATION_GUIDE.md](IAM_INTEGRATION_GUIDE.md)
- [PUBLIC_API.md](PUBLIC_API.md)
- [IAM_ADMIN_CONSOLE_DEPLOYMENT.md](IAM_ADMIN_CONSOLE_DEPLOYMENT.md)
- `iam-docs/` 文档站
