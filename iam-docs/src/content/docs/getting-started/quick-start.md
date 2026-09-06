---
title: 快速开始
description: 手动准备 MySQL 与 Redis，完成 IAM Starter 的最小配置，并通过少量接口确认认证与授权行为。
sidebar:
  order: 1
---

这篇 QuickStart 的目标不是让使用者复现 IAM 仓库的完整测试，而是帮助你把 Starter 接进一个 Spring Boot 应用并成功启动。

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

业务应用通常只依赖这个 Starter，不要直接依赖 IAM 的 Mapper、Repository 实现或 `internal` 包。

## 2. 准备 MySQL

IAM 使用宿主应用的 `DataSource` 保存 Session、Profile、Permission Template、Scope 和审计数据。

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
iam:
  schema:
    enabled: true
```

Starter 会自动执行 `classpath:db/iam/migration` 中的 Flyway migration，并使用独立的 `iam_flyway_schema_history`。因此一般**不需要手工创建 IAM 表**。

如果你的团队已经由 DBA 或统一迁移平台管理 IAM schema，再设置 `iam.schema.enabled=false`。

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

多应用共享 Redis 时，建议为不同应用设置不同的 `iam.token.redis-prefix`。生产 Redis 应放在受控网络中，不要直接暴露公网。

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

MySQL / Redis 在其他机器时只需替换地址、端口和凭据。IAM 不要求基础设施通过 Docker 部署。

## 5. 提供 IdentityAuthenticator

宿主继续拥有自己的用户表和认证来源。IAM 只要求宿主验证凭据后投影一个 `IamPrincipal`。

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

`iam.client-types` 由 IAM 层统一检查，`IdentityAuthenticator` 不需要重复维护 Client Type 白名单。

进一步阅读：[IdentityAuthenticator](/authentication/identity-authenticator/)。

## 6. 配置业务资源范围

使用 Scope 时，宿主实现 `ResourceHierarchyProvider`：

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

使用声明式 MVC 授权时，再提供 `MvcResourceDescriptorResolver`，把业务对象映射成 IAM 的 `ResourceDescriptor`。

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) {
    return documentService.get(id);
}
```

IAM 不查询你的 Document / Project 表。宿主告诉 IAM “这个资源是什么、属于哪里”，IAM 再判断“当前 Principal 能不能访问”。

## 7. 启动应用

可以直接在 IDE 中运行 Spring Boot 主类，也可以使用你现有项目的 JAR / 服务部署方式。

首次启动且 `iam.schema.enabled=true` 时，确认：

- MySQL 连接成功；
- Redis 连接成功；
- IAM Flyway migration 成功；
- Spring Boot 应用正常启动。

普通使用者无需运行 IAM 仓库的 Testcontainers、Consumer Acceptance 或完整测试套件；这些由项目 CI 持续验证。

## 8. 登录

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

## 9. 读取当前 Principal

**接口**：`GET /iam/auth/me`

**请求方式**：Bearer Token。

**预期结果**：HTTP `200`，返回当前 `IamPrincipal`。

到这里已经可以确认基础认证链路接入成功。

## 10. 验证声明式权限

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

## 11. 切换 Profile

**接口**：`POST /iam/authorization/profiles/{profileId}/switch`

`muer-example` 的 Editor Profile ID 为 `402`。

**预期结果**：HTTP `200`，返回新的 Token 和新的 Session。

Profile Switch 不会修改原 Reader Token，因此：

- 新 Editor Token 可以更新 Document 1001；
- 原 Reader Token 仍然不能更新 Document 1001。

## 12. 查看授权诊断

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

## 13. 撤销 Session

**接口**：`POST /iam/sessions/{sessionId}/revoke`

**预期结果**：成功返回 HTTP `204`。该 Session 对应的 Token 之后访问受保护接口返回 `401`，同一用户的其他独立 Session 不受影响。

## 14. 直接运行 muer-example

如果想体验上述 Alice 场景，可以手动准备 MySQL / Redis，然后在 IDE Run Configuration 或服务环境中配置：

| 环境变量 | 示例 |
| --- | --- |
| `IAM_EXAMPLE_JDBC_URL` | `jdbc:mysql://127.0.0.1:3306/iam_example` |
| `IAM_EXAMPLE_DB_USERNAME` | `iam` |
| `IAM_EXAMPLE_DB_PASSWORD` | `your-password` |
| `IAM_EXAMPLE_REDIS_HOST` | `127.0.0.1` |
| `IAM_EXAMPLE_REDIS_PORT` | `6379` |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `IAM_EXAMPLE_SEED_DEMO` | `true` |

`QuickStartDemoSeeder` 会重置演示 IAM 投影，只能用于专用本地演示数据库。

如果没有现成 MySQL / Redis，也可以选择仓库提供的 Docker Compose。这只是可选工具，详见[手动部署](/getting-started/manual-deployment/)。

## 15. 什么时候算接入成功

普通使用者达到下面几项即可：

- 应用可以正常启动；
- MySQL、Redis 连接正常；
- IAM schema 成功迁移或已由你的部署流程管理；
- 合法用户可以登录；
- `GET /iam/auth/me` 能读取当前 Principal；
- 至少一个业务接口能够按配置得到预期的允许或拒绝结果。

不需要为了“证明 IAM 能工作”再运行项目仓库的全量测试。

接下来建议阅读：[手动部署](/getting-started/manual-deployment/)、[基础配置](/getting-started/configuration/)、[@RequirePermission](/authorization/require-permission/) 与[生产检查清单](/operations/production-checklist/)。
