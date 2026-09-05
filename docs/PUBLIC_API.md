# IAM 0.1.0 Public API Reference

本页只列出当前源码中供消费应用使用的稳定候选 API/SPI。版本仍为
`0.1.0-SNAPSHOT`，因此“稳定”表示本次发布候选的消费边界，而不是已经发布的二进制
兼容承诺。所有路径和状态以
[`iam.yaml`](../iam-management-web/src/main/resources/openapi/iam.yaml) 为准。

## 消费者 API 与 SPI

| 用途 | 类型 | 源码位置 |
| --- | --- | --- |
| 登录凭据校验 | `IdentityAuthenticator` | `iam-authentication/src/main/java/io/github/iamstarter/authentication/IdentityAuthenticator.java` |
| 已认证主体 | `IamPrincipal` | `iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java` |
| 资源范围关系 | `ResourceHierarchyProvider`、`ResourceDescriptor`、`ResourceScope` | `iam-core/src/main/java/io/github/iamstarter/core/port/ResourceHierarchyProvider.java` |
| 授权请求与决定 | `AuthorizationEngine`、`AuthorizationRequest`、`AuthorizationDecision` | `iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationEngine.java` |
| 额外策略 | `AuthorizationPolicy`、`AuthorizationPolicyResult` | `iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationPolicy.java` |
| MVC 声明式检查 | `@RequirePermission`、`MvcResourceDescriptorResolver` | `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/RequirePermission.java` |
| MVC 失败信封替换 | `IamAuthorizationFailureHandler` | `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationFailureHandler.java` |
| 认证与会话模型 | `AuthenticationResult`、`AuthSession`、`TokenRecord` | `iam-authentication/src/main/java/io/github/iamstarter/authentication/AuthenticationResult.java` |
| profile 与模板模型 | `AuthorizationProfile`、`PermissionTemplateVersion`、`TemplateVersionStatus` | `iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationProfile.java` |
| 审计模型 | `AuditRecord`、`AuditSubjectLink`、`AuditSubjectRelation` | `iam-audit/src/main/java/io/github/iamstarter/audit/AuditRecord.java` |

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

配置前缀是 `iam`，源码为
`iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamProperties.java`。

| 属性 | 默认值 | 说明与约束 |
| --- | --- | --- |
| `iam.enabled` | `true` | `true` 时自动配置生效；缺省也生效。 |
| `iam.token.ttl` | `8h` | 必须为正 duration。 |
| `iam.token.redis-prefix` | `iam` | 必须非空；共享 Redis 时应使用宿主专属前缀。 |
| `iam.session.enabled` | `true` | `IamProperties` 暴露的 session 标志；当前自动配置未按此属性声明条件化 bean。 |
| `iam.session.touch-interval` | `10m` | session touch 间隔。 |
| `iam.schema.enabled` | `true` | 启用 IAM schema migrator。 |
| `iam.schema.history-table` | `iam_flyway_schema_history` | 必须非空，且应独立于宿主 Flyway history。 |
| `iam.audit.enabled` | `true` | `IamProperties` 暴露的 audit 标志；当前自动配置未按此属性声明条件化 bean。 |
| `iam.diagnostics.enabled` | `true` | `IamProperties` 暴露的 diagnostics 标志；当前自动配置未按此属性声明条件化 bean。 |
| `iam.client-types` | `[WEB]` | 至少一个非空值；登录 `clientType` 必须精确匹配其中一项。 |

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
| Authorization | `evaluateAuthorization` | `POST /iam/authorization/diagnostics` | 200 | — |
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
[`iam.yaml`](../iam-management-web/src/main/resources/openapi/iam.yaml)。

## 非稳定边界

下列内容不是消费应用的稳定 API：

- `iam-persistence-mybatis` 中的 MyBatis repositories、rows、mappers 及 XML mapper；
- 任何 `internal` package；
- `IamSchemaMigrator`、迁移 SQL、Flyway history 细节和持久化表结构；
- 自动配置内部 bean 的实现类与 Spring MVC controller 构造器。

消费应用应通过上表的 SPI、模型、OpenAPI 合约和 starter 配置进行集成。持久化替换需
提供对应 public port 的实现，不应依赖默认 MyBatis 实现的类或 mapper。
