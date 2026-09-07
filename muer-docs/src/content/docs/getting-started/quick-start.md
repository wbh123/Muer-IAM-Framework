---
title: 快速开始
description: 手动准备 MySQL 与 Redis，把 Muer 接入 Spring Boot 应用，并完成权限声明、认证授权与运行状态验证。
sidebar:
  order: 1
---

这篇 Quick Start 的目标不是让你复现 Muer 仓库的完整测试，而是帮助你把 Starter 接进一个 Spring Boot 应用并成功完成一条最小业务链路：

```text
添加 Starter
→ MySQL / Redis
→ IdentityAuthenticator
→ PermissionDefinitionProvider
→ Resource Resolver
→ @RequirePermission
→ 登录与授权
→ Admin Console（可选）
→ Actuator / Metrics（可选）
```

你只需要准备：

- Java 21；
- Spring Boot 4；
- Maven；
- 可连接的 MySQL 8.x（发布基线使用 MySQL 8.4）；
- Redis 7。

:::note[Docker 不是前置条件]
MySQL 和 Redis 可以来自本机安装、局域网服务器、云服务或容器。仓库中的 Docker Compose 只是可选的本地便利方案。
:::

## 1. 添加 Starter

当前版本为 `0.1.0-SNAPSHOT`（Release Candidate），尚未发布到 Maven Central。

```xml
<dependency>
    <groupId>io.github.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

业务应用通常只依赖这个 Starter，不要直接依赖 Muer 的 Mapper、Repository 实现或 `internal` 包。

## 2. 准备 MySQL

Muer 使用宿主应用的 `DataSource` 保存 Session、Profile、Permission Template、Scope 和审计数据。

如果你已经有 MySQL，只需要为应用准备一个可以建表和读写数据的数据库账号。例如：

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

如果应用与数据库不在同一台机器，请把 `localhost` 改成真实应用主机或受控网段。生产环境不要使用示例密码，也不要为了方便直接开放公网数据库访问。

默认：

```yaml
muer:
  schema:
    enabled: true
```

Starter 会自动执行 `classpath:db/iam/migration` 中的 Flyway migration，并使用独立的 `iam_flyway_schema_history`。因此一般**不需要手工创建 IAM 表**。

如果你的团队已经由 DBA 或统一迁移平台管理 IAM schema，再设置 `muer.schema.enabled=false`。

完整说明见[手动部署](/getting-started/manual-deployment/)和[MySQL 存储](/operations/mysql/)。

## 3. 准备 Redis

Redis 用作不透明 Token 的快速索引。已有 Redis 7 可以直接使用，不需要预创建 Key。

你需要知道：

| 项目 | 示例 |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `6379` |
| Password | 由实际环境决定 |
| Database | 默认 `0` 即可 |
| IAM Key Prefix | 默认 `iam` |

多应用共享 Redis 时，建议为不同应用设置不同的 `muer.token.redis-prefix`。生产 Redis 应放在受控网络中，不要直接暴露公网。

## 4. 配置 application.yml

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
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
  client-types:
    - WEB
```

MySQL / Redis 在其他机器时只需替换地址、端口和凭据。Muer 不要求基础设施通过 Docker 部署。

## 5. 提供 IdentityAuthenticator

宿主继续拥有自己的用户表和认证来源。Muer 只要求宿主验证凭据后投影一个 `IamPrincipal`。

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

`muer.client-types` 由 Muer 统一检查，`IdentityAuthenticator` 不需要重复维护 Client Type 白名单。

进一步阅读：[IdentityAuthenticator](/authentication/identity-authenticator/)。

## 6. 声明业务 Permission

开发者负责定义“系统有哪些能力”，管理员负责决定“谁拥有这些能力”。不要把业务 Permission 当成手工初始化 SQL。

```java
@Bean
PermissionDefinitionProvider documentPermissions() {
    return () -> List.of(
            new PermissionDefinition("document:read", "查看文档", "读取文档内容"),
            new PermissionDefinition("document:update", "编辑文档", "修改文档内容"));
}
```

应用就绪后，Muer 会幂等注册 Permission：创建缺失项、更新显示元数据，但不会因为当前 Provider 未声明某个历史 Permission 就自动删除它。

完整规则见[定义权限](/getting-started/define-permissions/)和[权限管理](/getting-started/permission-management/)。

## 7. 配置业务资源范围

使用 Scope 时，宿主实现 `ResourceHierarchyProvider`：

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

使用声明式 MVC 授权时，再提供 `MvcResourceDescriptorResolver`，把业务对象映射成 Muer 的 `ResourceDescriptor`。

例如：

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) {
    return documentService.get(id);
}
```

Muer 不查询你的 Document / Project 表。宿主告诉 Muer “这个资源是什么、属于哪里”，Muer 再判断“当前 Principal 能不能访问”。

## 8. 启动应用

可以直接在 IDE 中运行 Spring Boot 主类，也可以使用你现有项目的 JAR / 服务部署方式。

首次启动且 `muer.schema.enabled=true` 时，确认：

- MySQL 连接成功；
- Redis 连接成功；
- IAM Flyway migration 成功；
- Permission Definition 注册完成；
- Spring Boot 应用正常启动。

普通使用者无需运行仓库的 Testcontainers、Consumer Acceptance 或完整测试套件；这些由项目 CI 持续验证。

## 9. 登录

可以使用 Postman、Apifox、IDE HTTP Client 等任意 HTTP 客户端。

**接口**：`POST /iam/auth/login`

**请求体**：

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

**预期结果**：

- 合法凭据返回 HTTP `200`；
- 响应包含 `accessToken`、`sessionId`、`expiresAt`、`principal`；
- 后续受保护接口使用 `Authorization: Bearer <accessToken>`。

> `alice / demo-pass` 是 `muer-example` 的本地演示账号。自己的业务系统应使用自己的身份源。

## 10. 读取当前 Principal

**接口**：`GET /iam/auth/me`

**请求方式**：Bearer Token。

**预期结果**：HTTP `200`，返回当前 `IamPrincipal`。

到这里已经可以确认基础认证链路接入成功。

## 11. 验证声明式权限

`muer-example` 提供一个简单 Document 场景：

- Document `1001` 属于 Project `101`；
- Document `2001` 属于 Project `202`；
- Reader Profile 只有 `document:read` 和 Project 101 READ Scope；
- Editor Profile 额外具有 `document:update` 和 Project 101 WRITE Scope。

| 接口 | 方法 | Reader 预期 | Editor 预期 |
| --- | --- | --- | --- |
| `/api/documents/1001` | GET | `200` | `200` |
| `/api/documents/1001` | POST | `403` | `200` |
| `/api/documents/2001` | GET | `403` | `403` |

更新文档时，请求体示例：

```json
{
  "status": "PUBLISHED"
}
```

这一组结果同时说明 Permission 与 Resource Scope 都在生效。

## 12. 切换 Profile

**接口**：`POST /iam/authorization/profiles/{profileId}/switch`

`muer-example` 的 Editor Profile ID 为 `402`。

**预期结果**：HTTP `200`，返回新的 Token 和新的 Session。

Profile Switch 不会修改原 Reader Token，因此：

- 新 Editor Token 可以更新 Document 1001；
- 原 Reader Token 仍然不能更新 Document 1001。

## 13. 查看授权诊断

**接口**：`POST /iam/authorization/diagnostics`

请求体示例：

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

**预期结果**：HTTP `200`，返回真实 `AuthorizationDecision`。Reader 缺少更新权限时，`allowed=false`，常见 `decisionCode` 为 `PERMISSION_DENIED`。

该接口只诊断当前已认证 Principal，不是管理员专属接口，也不能指定其他用户/Profile。

## 14. 撤销 Session

**接口**：`POST /iam/sessions/{sessionId}/revoke`

**预期结果**：成功返回 HTTP `204`。该 Session 对应的 Token 之后访问受保护接口返回 `401`，同一用户的其他独立 Session 不受影响。

## 15. 管理 Permission / Profile / Scope（可选）

接入 Muer 后，一般不需要再自己编写一套权限管理后端：

- 直接使用随 0.1.0 提供的 IAM Admin Console；或
- 如果企业已有统一后台，调用 Muer Management API 集成自己的页面。

不要直接操作 `iam_*` 表。业务 Permission 由开发者通过 Provider 声明，管理员负责把它们配置进 Template、Profile 和 Scope。

详情见[权限管理](/getting-started/permission-management/)和[管理控制台](/management/console/)。

## 16. 查看运行状态（可选）

如果宿主使用 Spring Boot Actuator / Micrometer，Muer 会条件化贡献运行信息；没有这些依赖时不会影响 Starter 正常使用。

Actuator Health 中的 `muer` 表示 Muer 框架自动配置可用；MySQL / Redis 健康仍由宿主 Spring Boot 自带的 DataSource / Redis Health 负责。

Micrometer 当前可记录认证尝试、授权 Allow/Deny、授权耗时、Session 创建/撤销以及 Token Lookup 等指标。

详情见[可观测性](/operations/observability/)。

## 17. 直接运行 muer-example

如果想体验上述 Alice 场景，可以手动准备 MySQL / Redis，然后在 IDE Run Configuration 或服务环境中配置：

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

如果没有现成 MySQL / Redis，也可以选择仓库提供的 Docker Compose。这只是可选工具，详见[手动部署](/getting-started/manual-deployment/)。

## 18. 什么时候算接入成功

普通使用者达到下面几项即可：

- 应用可以正常启动；
- MySQL、Redis 连接正常；
- IAM schema 成功迁移或已由你的部署流程管理；
- 业务 Permission 可以被 Muer 注册并在管理端看到；
- 合法用户可以登录；
- `GET /iam/auth/me` 能读取当前 Principal；
- 至少一个业务接口能够按配置得到预期的允许或拒绝结果；
- 如启用 Actuator，可以看到 Muer 与宿主基础设施 Health。

不需要为了“证明 Muer 能工作”再运行项目仓库的全量测试。

接下来建议阅读：[手动部署](/getting-started/manual-deployment/)、[定义权限](/getting-started/define-permissions/)、[权限管理](/getting-started/permission-management/)、[@RequirePermission](/authorization/require-permission/)、[可观测性](/operations/observability/)与[生产检查清单](/operations/production-checklist/)。
