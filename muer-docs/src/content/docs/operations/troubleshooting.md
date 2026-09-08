---
title: 排障（Troubleshooting）
description: 按“症状 → 常见原因 → 检查方法”组织，覆盖启动、登录 401、业务 403、404/500、令牌突然失效、Actuator 缺 muer 等常见问题。
sidebar:
  order: 6
---

本文按“症状”组织，而不是按模块。如果你在排查一个具体的 401 / 403 / 启动失败，直接找对应行。

## 症状速查

| 症状 | 常见原因 | 先看哪节 |
| --- | --- | --- |
| Maven 找不到 `cloud.muer` artifact | Snapshot 未本地安装 | [Maven 找不到依赖](#maven-找不到-cloudmuer-artifact) |
| 应用启动连不上 MySQL | DataSource 配置 / 账号权限 | [启动失败](#启动失败) |
| Redis connection refused | Redis 地址 / 端口 / 密码 | [启动失败](#启动失败) |
| Flyway migration 失败 | Schema 权限 / 版本冲突 | [启动失败](#启动失败) |
| 启动报 `muer.client-types` 为空 | 配置错误 | [启动失败](#启动失败) |
| 登录返回 401 | IdentityAuthenticator / 凭据 / clientType | [登录 401](#登录-返回-401) |
| 登录成功但业务接口 403 | Permission / Profile / Scope | [业务接口 403](#业务接口-403) |
| 返回 `IAM_RESOURCE_NOT_FOUND`(404) | Resolver 解析到空 | [404 资源不存在](#404-iamresourcenotfound) |
| 返回 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`(500) | 未注册 Resolver | [500 无资源解析](#500-iamresourceresolutionunavailable) |
| 令牌“突然”失效 401 | TTL / Revoke / Authorization Version / Redis | [401 会话失效](#401-会话失效) |
| Admin Console 403 | 缺 `iam.admin.*` / Scope | [Admin Console 403](#admin-console-403) |
| Actuator 里没有 `muer` | 未暴露 health / 未引 Actuator | [Actuator 没有 muer](#actuator-没有-muer-health) |

## Maven 找不到 `cloud.muer` artifact

当前为 `0.1.0-SNAPSHOT`（Release Candidate），尚未发布到 Maven Central。如果 `cloud.muer:muer-spring-boot-starter` 解析失败，先把仓库本地安装：

```bash
git clone https://github.com/wbh123/Muer-IAM-Framework.git
cd Muer-IAM-Framework
mvn clean install -DskipTests
```

完成后该坐标会进入 `~/.m2/repository`。正式 0.1.0 发布到 Maven Central 后，这一步骤可删除。

## 启动失败

按应用日志区分：

- **MySQL**：核对 `spring.datasource.url/username/password`，账号需对目标库有建表与读写权限。确认 `muer.schema.enabled=true` 时 Flyway 能写入 `iam_flyway_schema_history`。
- **Redis**：核对 `spring.data.redis.host/port/password`。Redis 只是 opaque Token 索引，连不上会阻止签发令牌。
- **配置校验**：`MuerProperties` 启动时校验 `muer.token.ttl` 必须为正、`muer.client-types` 至少一个非空值、`muer.token.redis-prefix` 与 `muer.schema.history-table` 非空。配错会直接抛 `IllegalStateException`，日志会写明是哪一项。

启动后先打基础链路：`POST /iam/auth/login` 与 `GET /iam/auth/me`。

## 登录返回 401

登录 401 表示**未建立身份**，先按顺序核对：

1. `IdentityAuthenticator` 是否真的用你的用户源校验了凭据？可加日志确认它被调用。
2. 账号/密码是否匹配你的用户表？
3. 请求里的 `clientType` 是否与 `muer.client-types` 允许列表**精确匹配**（大小写敏感）？不匹配会 401 且不建会话。
4. `IdentityAuthenticator` 返回的 `IamPrincipal` 是否合法（`userId>0`、`identityId`、`identityDomain`、`clientType` 非空、`activeProfileId` 与 `templateVersionId` 为正）？构造失败会抛异常。

不要一遇到 401 就去“查密码是否正确”，401 常见原因是 clientType 或 Principal 投影问题。

## 业务接口 403

403 表示已认证但被拒。Muer 的排障流程固定是：**403 → Diagnostics → Principal？→ Profile？→ Permission？→ Scope？→ 策略？**。

先在接口的 profile 下调用诊断接口：

```bash
curl --fail-with-body -sS \
  -X POST http://127.0.0.1:8080/iam/authorization/diagnostics \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{"permissionCode":"document:read","domain":"EXAMPLE","clientType":"WEB","resourceType":"PROJECT","resourceId":"101","scopeAccess":"READ"}'
```

返回的 `AuthorizationDecision` 含 `steps`（每一步 `passed` / `reason`）与 `decisionCode`。对照 [错误码](/reference/error-codes/) 顺藤摸瓜：

- `IDENTITY_DOMAIN_MISMATCH` / `CLIENT_TYPE_MISMATCH` → Principal 的 `domain`/`clientType` 与请求不符。
- `PROFILE_*`（MISSING / DISABLED / REVOKED / CLIENT_DENIED / NOT_YET_VALID / EXPIRED / TEMPLATE_MISMATCH）→ 看活动 Profile 的状态、clientTypes、有效期与引用的模板版本。
- `PERMISSION_DENIED` → 当前 Profile 的已发布模板版本里没有该 `permissionCode`。
- `SCOPE_DENIED` → permission 存在，但请求的资源（`resourceType`/`resourceId`/`accessMode`）不在 Profile 的 Scope 内。

判断顺序本质是：

```text
Principal 是谁
   ↓ 身份域/客户端匹配？
Profile 是哪个、是否有效
   ↓
引用的模板版本里有没有这个 Permission
   ↓
资源是否落在 Scope 内
   ↓
（可选 AuthorizationPolicy）是否放行
```

## 404 `IAM_RESOURCE_NOT_FOUND`

`MvcResourceDescriptorResolver` 对请求返回了 empty，说明宿主把该请求路由到了“不存在的资源”，而不是“无权限”。通常是 Resolver 里根据 `{id}` 查业务对象查不到。核对 Resolver 是否读了正确的路径变量、以及资源归属（parentPath / scopeType / scopeRefId）是否正确。

## 500 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`

没有注册 `MvcResourceDescriptorResolver` Bean，但某条带 `@RequirePermission` 的 MVC 路由在访问。Muer 无法把请求解析成 `ResourceDescriptor`。实现一个 Resolver Bean 即可；无 Scope 需求的最小实现可返回描述该资源的 descriptor。

## 401 会话失效

“昨天还能用、今天 401”时，令牌失效来源有多种，**不要默认是密码问题**：

- **TTL 到期**：`muer.token.ttl`（默认 8h）到了，令牌自然失效，需重新登录。
- **Session 被撤销**：`POST /iam/sessions/{sessionId}/revoke` 会使其后旧令牌 401；同一用户的其他 Session 不受影响。
- **Authorization Version 递增**：`IamPrincipal.authorizationVersion` 改变会让按旧版本签发的授权失效。
- **Redis Token 数据丢失**：Redis 只是索引。若某 token 的 `TokenRecord` 在 Redis 中丢失，该令牌可能无法解析、需要重新登录；Muer **不会**从 MySQL 自动重建已签发令牌（MySQL 持久化的是授权事实，不是令牌本身）。
- **Profile Switch 后误用旧令牌**：切到新 Profile 得到新 Token；旧 Profile 的 Token 仍按其自身 Profile 授权，不会“被提升”。

排查时可调用 `GET /iam/auth/me`，看返回的 Principal（`activeProfileId`、`authorizationVersion`、`clientType`）与当前 Profile / 模板版本是否一致。

## Admin Console 403

Admin Console 属于管理能力，由 `iam.admin.*` 管理权限（如 `iam.admin.overview.read`、`iam.admin.profile.read`、`iam.admin.permission.read` 等）与对应 Scope 决定。403 时核对：当前登录身份是否绑定了一个含所需 `iam.admin.*` 权限 + 相应 Scope 的 Profile。演示用的 `admin-demo` 账号需以 `MUER_EXAMPLE_SEED_ADMIN=true` 的 dev profile 启动才会预置。

## Actuator 没有 `muer` health

先确认宿主引入了 Spring Boot Actuator，并且 `management.endpoint.health.show-details` 允许展示。Muer 的 health contributor 在存在 Actuator 时条件化注册（见 [可观测性](/operations/observability/)）。

注意 `muer=UP` **只表示 Muer 自动配置正常**，不代表 MySQL / Redis / 外部身份源一定正常——请同时看宿主的 `db`、`redis` health。

## 下一步

- 想理解每一种拒绝码：看[错误码](/reference/error-codes/)。
- 想验证健康检查与指标：看[可观测性](/operations/observability/)。
- 想核对上线前必查项：看[生产环境检查清单](/operations/production-checklist/)。
