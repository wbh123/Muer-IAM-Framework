# IAM 0.1.0 Quick Start

本文面向第一次接入 `IAM Spring Boot Starter` 的 Spring Boot 开发者，目标是完成一条最短使用路径：

1. 准备可连接的 MySQL 与 Redis；
2. 配置 Spring Boot 应用；
3. 接入宿主身份与资源范围 SPI；
4. 启动应用；
5. 通过几个 HTTP 接口确认认证与授权行为符合预期；
6. 如需可视化管理，再启动 0.1.0 随附的可选 IAM Admin Console。

当前版本仍是 `0.1.0-SNAPSHOT`（Release Candidate），尚未作为正式 Maven 发行版发布。

> Docker **不是**使用 IAM 的前置条件。你可以使用本机安装的 MySQL / Redis、局域网服务、云数据库或容器。`examples/quickstart/docker-compose.yml` 只是为没有现成基础设施的开发者提供的可选便利方案。

## 1. 前置条件

- Java 21；
- Spring Boot 4；
- Maven；
- MySQL 8.x（项目发布基线验证使用 MySQL 8.4）；
- Redis 7。

使用 IAM 不要求安装 Docker，也不要求运行框架仓库中的 Testcontainers 或完整集成测试。

如果还要使用 Admin Console，需要额外准备 Node.js 22+ 与 npm。

## 2. 准备 MySQL

IAM 默认使用宿主应用的 `DataSource` 持久化 Session、Profile、Permission Template、Scope 与审计数据。

如果你已经有 MySQL，可以直接创建一个供当前应用使用的数据库和账号。例如：

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

如果应用与 MySQL 不在同一台机器，请把 `localhost` 换成实际应用主机或受控网段，并通过防火墙限制访问范围；生产环境不要直接使用示例密码。

默认情况下：

```yaml
iam:
  schema:
    enabled: true
```

Starter 会从 `classpath:db/iam/migration` 自动执行 IAM 的 Flyway migration，并使用独立的历史表 `iam_flyway_schema_history`。因此首次接入通常**不需要手工建 IAM 表**。

只有当你的团队已经通过 DBA、Liquibase、统一 Flyway 流程等方式显式管理同一套 IAM schema 时，才设置：

```yaml
iam:
  schema:
    enabled: false
```

## 3. 准备 Redis

Redis 用于保存不透明 Token 的快速索引。使用现有 Redis 7 服务即可，不需要预先创建 Key 或数据结构。

最小连接信息包括：

| 配置 | 示例 | 说明 |
| --- | --- | --- |
| Host | `127.0.0.1` | Redis 地址 |
| Port | `6379` | Redis 端口 |
| Password | 按环境设置 | Redis 开启认证时配置 |
| Database | `0` | 可使用 Spring Boot 默认值 |
| IAM Key Prefix | `iam` | 多应用共享 Redis 时建议使用应用独立前缀 |

生产环境不要把 Redis 直接暴露到公网。应用与 Redis 跨主机部署时，建议使用私网、访问控制和认证。

## 4. 配置 Spring Boot

最小的 MySQL、Redis 与 IAM 配置如下：

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

iam:
  enabled: true
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  token:
    ttl: 8h
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
  client-types:
    - WEB
```

如果 MySQL 或 Redis 在其他服务器，只需要替换对应的地址、端口和凭据。IAM 本身不要求基础设施以容器方式运行。

## 5. 引入 Starter

生产应用通常只需要依赖聚合 Starter：

```xml
<dependency>
    <groupId>io.github.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 6. 接入宿主身份

IAM 不要求迁移你现有的用户表。宿主系统继续负责用户名、密码、员工账号、学生账号或其他身份源，只需要实现 `IdentityAuthenticator` 并返回 `IamPrincipal`。

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

`iam.client-types` 是 IAM 层的客户端类型白名单。宿主 `IdentityAuthenticator` 只负责凭据认证，不需要复制这段白名单判断。

## 7. 接入资源范围

如果业务需要 Department、Project、Order、Document 等资源范围授权，宿主提供：

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

使用声明式 MVC 授权时，再实现 `MvcResourceDescriptorResolver`，把业务请求映射为 `ResourceDescriptor`。

例如：

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) {
    return documentService.get(id);
}
```

IAM 负责“这个 Principal 是否有权访问这个 Resource”，宿主系统负责“这个 Document 是什么、属于哪个 Project”。

## 8. 启动应用

你可以直接在 IDE 中运行 Spring Boot 主类，也可以按项目自己的标准方式构建并启动 JAR。

首次启动时，若 `iam.schema.enabled=true`，应能看到 IAM schema migration 正常完成，随后应用成功启动。

使用者不需要为了接入 IAM 再执行仓库中的完整自动化测试；这些测试由 IAM 项目的 CI 负责。

## 9. 用接口确认接入结果

可以使用 Postman、Apifox、IDE HTTP Client、浏览器插件或任何 HTTP 客户端。无需依赖 `curl` / `jq`。

### 9.1 登录

**接口**：`POST /iam/auth/login`

**认证**：无需 Bearer Token。

**请求体示例**：

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

**预期结果**：

- 合法凭据：HTTP `200`；
- 返回 `accessToken`、`sessionId`、`expiresAt` 和 `principal`；
- 非法凭据或不允许的 `clientType`：认证失败。

### 9.2 获取当前 Principal

**接口**：`GET /iam/auth/me`

**认证**：`Authorization: Bearer <accessToken>`。

**预期结果**：HTTP `200`，返回当前 `IamPrincipal`。

### 9.3 声明式权限示例

以 `muer-example` 的 Document 场景为例：

| 请求 | Reader Profile | Editor Profile | 说明 |
| --- | --- | --- | --- |
| `GET /api/documents/1001` | `200` | `200` | Project 101 范围内读取 |
| `POST /api/documents/1001` | `403` | `200` | 写入需要 `document:update` + WRITE Scope |
| `GET /api/documents/2001` | `403` | `403` | Document 2001 属于 Project 202，超出 Project 101 Scope |

更新文档时的请求体示例：

```json
{
  "status": "PUBLISHED"
}
```

### 9.4 切换 Profile

**接口**：`POST /iam/authorization/profiles/{profileId}/switch`

**认证**：Bearer Token。

**预期结果**：HTTP `200`，返回新的 `accessToken` 与新的 `sessionId`。

Profile Switch **不会提升或覆盖原 Token**。原 Reader Token 仍然保持 Reader 权限，新 Token 才使用目标 Profile。

### 9.5 授权诊断

**接口**：`POST /iam/authorization/diagnostics`

**认证**：Bearer Token。

示例请求：

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

**预期结果**：HTTP `200`，返回当前 Principal 的真实 `AuthorizationDecision`。例如 Reader 缺少 `document:update` 时，`allowed=false`，常见 `decisionCode=PERMISSION_DENIED`。

### 9.6 撤销 Session

**接口**：`POST /iam/sessions/{sessionId}/revoke`

**认证**：Bearer Token。

**预期结果**：成功撤销返回 HTTP `204`；对应 Token 随后访问受保护接口应返回 `401`。同一用户的其他独立 Session 不应被连带撤销。

## 10. 如果想直接运行仓库示例

`muer-example` 已经提供 Alice / Reader / Editor 的演示数据。你可以：

1. 手动准备一个空 MySQL 数据库和一个可用 Redis；
2. 配置以下环境变量或在 IDE Run Configuration 中填写同等配置；
3. 启用 `dev` Profile 和演示种子；
4. 启动 `IamExampleApplication`。

常用配置：

| 环境变量 | 示例 |
| --- | --- |
| `IAM_EXAMPLE_JDBC_URL` | `jdbc:mysql://127.0.0.1:3306/iam_example` |
| `IAM_EXAMPLE_DB_USERNAME` | `iam` |
| `IAM_EXAMPLE_DB_PASSWORD` | `your-password` |
| `IAM_EXAMPLE_REDIS_HOST` | `127.0.0.1` |
| `IAM_EXAMPLE_REDIS_PORT` | `6379` |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `IAM_EXAMPLE_SEED_DEMO` | `true` |

`QuickStartDemoSeeder` 只允许用于专用演示数据库，因为它会重置 IAM 演示投影。不要对共享数据库或生产数据库启用它。

如果本机没有 MySQL / Redis，也可以选择使用 `examples/quickstart/docker-compose.yml` 快速启动它们；这是可选方案，不是 IAM 的部署要求。

## 11. 启动并手工验收 IAM Admin Console（可选）

0.1.0 同时提供 `iam-admin-web`，但它只是可选管理客户端，不影响 Starter 的独立使用。

### 11.1 准备本地管理员

若使用 `muer-example` 验收控制台，可在**专用开发数据库**中显式开启管理员种子：

```text
SPRING_PROFILES_ACTIVE=dev
IAM_EXAMPLE_SEED_ADMIN=true
```

同时使用前面的 MySQL/Redis 配置启动 `IamExampleApplication`。只有 `dev` Profile 与 `seed-admin=true` 同时存在时才会创建：

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

生产环境不会自动创建这个账号，也不存在公开的管理员 bootstrap HTTP 接口。

### 11.2 启动前端

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

浏览器打开 Vite 输出地址，通常为 `http://localhost:5173`。

### 11.3 建议手工验收路径

无需跑仓库完整自动化测试。浏览器中按下面路径确认即可：

1. 使用 `admin-demo / demo-pass / WEB` 登录，进入 Dashboard；
2. 打开用户列表与用户详情，Identity/Profile/Session 信息可以正常读取；
3. 打开 Permission、Template、Profile 页面，确认列表和详情能够加载；
4. 修改一个开发 Profile 或 Scope，保存后刷新仍能读取新值；
5. 在 Session 页面撤销一个测试 Session，确认目标会话失效；
6. 在 Audit 页面确认相关管理操作产生可查询审计记录；
7. 在 Diagnostics 页面输入 Permission/Resource 条件，能够显示 ALLOW/DENY 和决策步骤；
8. 使用缺少某项 `iam.admin.*` Capability 的测试 Profile 访问对应路由，前端应进入 403，后端接口也必须返回 403；
9. 点击退出后，前端清理会话且后端 Session/Token 不再可继续使用。

管理控制台页面隐藏、菜单和路由 Guard 只是用户体验层；真正授权始终由后端 `AuthorizationEngine` 执行。

完整部署、Nginx、CSP、生产首个管理员初始化见 [IAM_ADMIN_CONSOLE_DEPLOYMENT.md](IAM_ADMIN_CONSOLE_DEPLOYMENT.md)。

## 12. 什么时候算接入成功

对于普通 Starter 使用者，完成以下几点即可：

- Spring Boot 应用能正常启动；
- IAM Flyway migration 成功，或你已经按组织流程手动管理 schema；
- MySQL 与 Redis 连接正常；
- 合法用户可以通过 `POST /iam/auth/login` 登录；
- 携带 Token 访问 `GET /iam/auth/me` 能得到正确 Principal；
- 你的一个业务接口能按 `@RequirePermission` / `AuthorizationEngine` 得到符合预期的允许或拒绝结果。

如果同时使用 Admin Console，再额外确认：

- 控制台可以登录并加载主要管理页面；
- 管理写操作可持久化；
- Session revoke、Audit、Diagnostics、403 权限边界符合预期。

无需运行 IAM 仓库的完整 Testcontainers 验收套件。

更多内容见 [IAM_INTEGRATION_GUIDE.md](IAM_INTEGRATION_GUIDE.md)、[PUBLIC_API.md](PUBLIC_API.md)、[IAM_ADMIN_CONSOLE_DEPLOYMENT.md](IAM_ADMIN_CONSOLE_DEPLOYMENT.md) 与文档站。
