# IAM 0.1.0 Public API Reference

本页只列出当前源码中供消费应用使用的稳定候选 API/SPI。版本为
`0.1.0-SNAPSHOT`，因此“稳定”表示本次开发候选的消费边界，而不是已经发布的二进制
兼容承诺。所有路径和状态以
[`iam.yaml`](../muer-management-web/src/main/resources/openapi/iam.yaml) 为准。

## 消费者 API 与 SPI

| 用途 | 类型 | 源码位置 |
| --- | --- | --- |
| 登录凭据校验 | `IdentityAuthenticator` | `muer-authentication/src/main/java/io/github/muer/authentication/IdentityAuthenticator.java` |
| 应用权限声明 | `PermissionDefinition`、`PermissionDefinitionProvider` | `muer-authorization/src/main/java/io/github/muer/authorization/` |
| 已认证主体 | `IamPrincipal` | `muer-core/src/main/java/io/github/muer/core/model/IamPrincipal.java` |
| 资源范围关系 | `ResourceHierarchyProvider`、`ResourceDescriptor`、`ResourceScope` | `muer-core/src/main/java/io/github/muer/core/port/ResourceHierarchyProvider.java` |
| 授权请求与决定 | `AuthorizationEngine`、`AuthorizationRequest`、`AuthorizationDecision` | `muer-authorization/src/main/java/io/github/muer/authorization/AuthorizationEngine.java` |
| 额外策略 | `AuthorizationPolicy`、`AuthorizationPolicyResult` | `muer-authorization/src/main/java/io/github/muer/authorization/AuthorizationPolicy.java` |
| MVC 声明式检查 | `@RequirePermission`、`MvcResourceDescriptorResolver` | `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/web/RequirePermission.java` |
| MVC 失败信封替换 | `IamAuthorizationFailureHandler` | `muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/web/IamAuthorizationFailureHandler.java` |
| 认证与会话模型 | `AuthenticationResult`、`AuthSession`、`TokenRecord` | `muer-authentication/src/main/java/io/github/muer/authentication/AuthenticationResult.java` |
| profile 与模板模型 | `AuthorizationProfile`、`PermissionTemplateVersion`、`TemplateVersionStatus` | `muer-authorization/src/main/java/io/github/muer/authorization/AuthorizationProfile.java` |
| 审计模型 | `AuditRecord`、`AuditSubjectLink`、`AuditSubjectRelation` | `muer-audit/src/main/java/io/github/muer/audit/AuditRecord.java` |
| 管理读侧 SPI（Admin Console） | `UserQueryRepository`、`OverviewRepository` | `muer-core/src/main/java/io/github/muer/core/port/UserQueryRepository.java` |
| 管理读侧 SPI（Admin Console） | `AuthorizationProfileQueryRepository`、`PermissionTemplateQueryRepository`、`PermissionTemplate`、`PermissionSummary` | `muer-authorization/src/main/java/io/github/muer/authorization/` |
| 管理读侧 SPI（Admin Console） | `SessionQueryRepository` | `muer-session/src/main/java/io/github/muer/session/SessionQueryRepository.java` |
| 管理读侧 SPI（Admin Console） | `AuditQueryRepository`、`AuditEvent`、`AuditEventFilter`、`AuditEventPage` | `muer-audit/src/main/java/io/github/muer/audit/` |

管理查询 SPI 与既有可写 port 相互独立：自行实现持久化的宿主无需修改既有 port 实现，
只需在需要管理读能力时提供上述新 SPI 的 bean（默认 MyBatis 实现使用同一个
`SqlSessionFactory`）。

直接使用 `AuthorizationEngine` 时，调用方提供 `IamPrincipal` 和
`AuthorizationRequest`，并读取 `AuthorizationDecision`。使用 MVC 注解时，
`@RequirePermission.value` 是 permission code，`access` 默认
`ScopeAccess.READ`。

## 默认 MVC 失败响应

标注 `@RequirePermission` 的 MVC 请求使用默认
`ProblemDetailIamAuthorizationFailureHandler` 时，返回 `application/problem+json`。
响应含标准 Problem Detail 字段、请求 `path`，以及下列稳定 `code`；详情为通用文本，
不包含 IAM 敏感对象。

| 条件 | HTTP 状态 | `code` |
| --- | --- | --- |
| 无 principal | 401 | `IAM_UNAUTHENTICATED` |
| 授权引擎拒绝 | 403 | `IAM_ACCESS_DENIED` |
| 资源 resolver 找不到资源 | 404 | `IAM_RESOURCE_NOT_FOUND` |
| 未注册资源 resolver | 500 | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` |

## 配置

配置前缀是 `muer`，源码为
`muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerProperties.java`。

| 属性 | 默认值 | 说明与约束 |
| --- | --- | --- |
| `muer.enabled` | `true` | `true` 时自动配置生效；缺省也生效。 |
| `muer.token.ttl` | `8h` | 必须为正 duration。 |
| `muer.token.redis-prefix` | `iam` | 必须非空；共享 Redis 时应使用宿主专属前缀。 |
| `muer.session.enabled` | `true` | `MuerProperties` 暴露的 session 标志；当前自动配置未按此属性声明条件化 bean。 |
| `muer.session.touch-interval` | `10m` | session touch 间隔。 |
| `muer.schema.enabled` | `true` | 启用 IAM schema migrator。 |
| `muer.schema.history-table` | `iam_flyway_schema_history` | 必须非空，且应独立于宿主 Flyway history。 |
| `muer.audit.enabled` | `true` | `MuerProperties` 暴露的 audit 标志；当前自动配置未按此属性声明条件化 bean。 |
| `muer.diagnostics.enabled` | `true` | `MuerProperties` 暴露的 diagnostics 标志；当前自动配置未按此属性声明条件化 bean。 |
| `muer.client-types` | `[WEB]` | 至少一个非空值；登录 `clientType` 必须精确匹配其中一项。 |

## HTTP contract

以下为 OpenAPI 中当前已声明的操作。认证接口的 token 是不透明 token；管理接口还会
进行对应 `iam.admin.*` permission 的授权检查，详见 OpenAPI 和 controller 实现。

| 分组 | 操作 | 路径 | 成功 | 其他声明状态 |
| --- | --- | --- | --- | --- |
| Authentication | `login` | `POST /iam/auth/login` | 200 | 401 |
| Authentication | `logout` | `POST /iam/auth/logout` | 204 | 401 |
| Authentication | `getCurrentPrincipal` | `GET /iam/auth/me` | 200 | 401 |
| Sessions | `listMySessions` | `GET /iam/sessions` | 200 | 401 |
| Sessions | `revokeMySession` | `POST /iam/sessions/{sessionId}/revoke` | 204 | 401, 404 |
| Sessions | `revokeOtherSessions` | `POST /iam/sessions/revoke-others` | 204 | 401 |
| Authorization | `listMyAuthorizationProfiles` | `GET /iam/authorization/profiles` | 200 | — |
| Authorization | `switchAuthorizationProfile` | `POST /iam/authorization/profiles/{profileId}/switch` | 200 | 401, 404 |
| Authorization | `evaluateAuthorization` | `POST /iam/authorization/diagnostics` | 200 | 401 |

> `evaluateAuthorization` 是 **Authenticated Self Diagnostics**：任意已认证 principal 都可
> 诊断**自己**的授权决策（只读投影，不改任何状态）。它只针对 SecurityContext 当前
> principal，不接受 `userId` / `profileId` / `principal` 来诊断他人；未认证 → 401。
> Admin Console 的 Diagnostics 页面是否展示由 capability `iam.admin.diagnostics`
> 控制 —— 页面展示与后端 self-diagnostics 访问条件是不同概念。
| Administration | `savePermissionTemplateVersion` | `PUT /iam/admin/templates/{versionId}` | 204 | 401, 403 |
| Administration | `listUsers` | `GET /iam/admin/users` | 200 | 401, 403 |
| Administration | `saveUser` | `PUT /iam/admin/users/{userId}` | 204 | 401, 403 |
| Administration | `listUserIdentities` | `GET /iam/admin/users/{userId}/identities` | 200 | 401, 403 |
| Administration | `saveIdentity` | `PUT /iam/admin/identities/{identityId}` | 204 | 401, 403 |
| Administration | `saveAuthorizationProfile` | `PUT /iam/admin/profiles/{profileId}` | 204 | 400, 401, 403 |
| Administration | `replaceAuthorizationProfileScopes` | `PUT /iam/admin/profiles/{profileId}/scopes` | 204 | 401, 403 |
| Administration | `incrementAuthorizationVersion` | `POST /iam/admin/users/{userId}/authorization-version` | 200 | 401, 403 |
| Administration | `forceRevokeSession` | `POST /iam/admin/sessions/{sessionId}/revoke` | 204 | 401, 403, 404 |
| Administration | `forceRevokeUserSessions` | `POST /iam/admin/users/{userId}/sessions/revoke` | 204 | 401, 403 |

`GET /iam/sessions` 的 `userId` query 参数已经 deprecated 且被忽略；当前 principal
始终决定所列 session 的归属。请求与响应 schema、参数限制和完整描述见
[`iam.yaml`](../muer-management-web/src/main/resources/openapi/iam.yaml)。

### 管理查询端点（Admin Console 支撑）

| 分组 | 操作 | 路径 | 权限 |
| --- | --- | --- | --- |
| Capabilities | `getCurrentCapabilities` | `GET /iam/auth/capabilities` | 本人可见，无需 admin 权限 |
| Management Users | `getUser` | `GET /iam/admin/users/{userId}` | `iam.admin.user.read` |
| Management Identity | `getIdentity` | `GET /iam/admin/identities/{identityId}` | `iam.admin.identity.read` |
| Management Permissions | `listPermissions` | `GET /iam/admin/permissions` | `iam.admin.permission.read` |
| Management Templates | `listPermissionTemplates` | `GET /iam/admin/templates` | `iam.admin.template.read` |
| Management Templates | `getPermissionTemplate` | `GET /iam/admin/templates/{templateId}` | `iam.admin.template.read` |
| Management Templates | `listPermissionTemplateVersions` | `GET /iam/admin/templates/{templateId}/versions` | `iam.admin.template.read` |
| Management Templates | `getPermissionTemplateVersion` | `GET /iam/admin/template-versions/{versionId}` | `iam.admin.template.read` |
| Management Profiles | `listAuthorizationProfiles` | `GET /iam/admin/profiles` | `iam.admin.profile.read` |
| Management Profiles | `getAuthorizationProfile` | `GET /iam/admin/profiles/{profileId}` | `iam.admin.profile.read` |
| Management Profiles | `getAuthorizationProfileScopes` | `GET /iam/admin/profiles/{profileId}/scopes` | `iam.admin.scope.read` |
| Management Profiles | `listUserAuthorizationProfiles` | `GET /iam/admin/users/{userId}/profiles` | `iam.admin.profile.read` |
| Management Sessions | `adminListSessions` | `GET /iam/admin/sessions` | `iam.admin.session.read` |
| Management Sessions | `getAdminSession` | `GET /iam/admin/sessions/{sessionId}` | `iam.admin.session.read` |
| Management Sessions | `listUserSessions` | `GET /iam/admin/users/{userId}/sessions` | `iam.admin.session.read` |
| Management Audit | `listAuditEvents` | `GET /iam/admin/audit-events` | `iam.admin.audit.read` |
| Management Audit | `getAuditEvent` | `GET /iam/admin/audit-events/{eventId}` | `iam.admin.audit.read` |
| Management Overview | `getAdminOverview` | `GET /iam/admin/overview` | `iam.admin.overview.read` |

`GET /iam/admin/users` 追加了可选过滤 `username` / `userType` / `enabled`；既有的
`afterUserId` / `limit` 保持不变。管理查询仅返回状态与元数据：Session 永不包含 token /
Redis key；Audit 事件只读。`SessionResponse` 追加只读字段 `status` 与 `revokeReason`。

## 非稳定边界

下列内容不是消费应用的稳定 API：

- `muer-persistence-mybatis` 中的 MyBatis repositories、rows、mappers 及 XML mapper；
- 任何 `internal` package；
- `IamSchemaMigrator`、迁移 SQL、Flyway history 细节和持久化表结构；
- 自动配置内部 bean 的实现类与 Spring MVC controller 构造器。

消费应用应通过上表的 SPI、模型、OpenAPI 合约和 starter 配置进行集成。持久化替换需
提供对应 public port 的实现，不应依赖默认 MyBatis 实现的类或 mapper。
