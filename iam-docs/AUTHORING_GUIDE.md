# IAM 文档站页面创作规范

给后续文档维护者的权威规范。创作时必须严格遵守。

## 技术栈与环境

- 文档站：Astro 7.3.1 + @astrojs/starlight 0.42.0；
- 内容源目录：`iam-docs/src/content/docs/<group>/<slug>.md`；
- 内容文件使用 **Markdown（.md）**；
- 每页必须有 frontmatter：

  ```md
  ---
  title: <中文标题>
  description: <一句话中文描述>
  sidebar:
    order: <数字，同组内排序>
  ---
  ```

## 已确定的文件路由（slug）

sidebar（`astro.config.mjs`）引用的 slug 必须存在，否则构建会失败：

- 首页：`index.md`
- intro：what-is-iam、core-capabilities、use-cases、architecture
- getting-started：quick-start、installation、configuration、manual-deployment、project-structure
- concepts：identity-principal、permission、permission-template、profile、resource-scope、session、authorization-version
- authentication：login、identity-authenticator、client-type、current-user、opaque-token
- authorization：model、require-permission、mvc-resource-descriptor-resolver、resource-scope、authorization-engine、authorization-policy
- profile-session：profile、profile-switch、session-management、session-revoke、authorization-version
- diagnostics：authorization-diagnostics、audit、custom-error-handling
- migration：overview、shadow-mode、data-projection、rollback
- operations：mysql、redis、reverse-proxy、security-model、production-checklist
- reference：configuration、public-api、http-api、error-codes、modules
- resources：example、faq、release-notes、changelog、contributing

同 slug 出现在不同分组（如 `concepts/profile` 与 `profile-session/profile`）是允许的。

## 内容质量硬性要求

每页应做到：

1. 说明这个主题**解决什么问题**；
2. 解释**关键概念**；
3. 给出真实示例（代码、JSON、配置、接口表或结果表）；
4. 重要实现结论提供到真实源码的链接；
5. 部署类文档优先说明“如何配置成功”，而不是要求使用者复现项目 CI。

## 部署文档原则

- Docker **不是** IAM 的安装或部署前置条件；
- MySQL / Redis 可以来自手动安装、内网服务、云服务、Docker 或 Kubernetes；
- 手动部署文档必须给出 MySQL 数据库账号、Spring `DataSource`、Redis 连接和 `iam.*` 的配置方法；
- 普通使用者只需确认应用、MySQL、Redis、Schema migration 和基础接口工作正常；
- Testcontainers、Independent Consumer、完整回归测试属于 IAM 仓库 CI，不是部署者必做步骤；
- Docker Compose 可以作为“没有现成基础设施时”的可选快速方案，但不能写成唯一方案。

## HTTP 示例风格

面向使用者的教程，尤其是 QuickStart，**不要堆叠大量 `curl` / `jq` / shell 变量脚本**。

优先使用：

- 请求方法；
- 请求路径；
- 是否需要 Bearer Token；
- JSON 请求体；
- 预期 HTTP 状态和关键响应字段；
- 必要时用表格对比不同 Profile / Scope 的结果。

例如：

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/auth/login` |
| Auth | 无 |
| Expected | `200`，返回 `accessToken`、`sessionId`、`principal` |

只有在故障排查、自动化脚本或某个命令本身就是主题时，才使用完整命令行示例。

## 禁止

- 页面正文出现 `TODO`、`Coming soon`、`TBD`、`FIXME` 等占位词；
- 编造不存在的配置项、HTTP 端点、类名、字段；
- 把 Docker 描述为 IAM 必须依赖；
- 要求普通接入用户跑 IAM 仓库完整测试来“证明安装成功”；
- 在 QuickStart 中使用长篇 `curl + jq + export` 流程代替清晰的接口说明；
- 编造“已发布到 Maven Central / 正式 0.1.0”；当前仍统一表述为 `0.1.0-SNAPSHOT`（Release Candidate），**尚未发布**。

## 版本与一致性措辞

- 当前版本写作：`0.1.0-SNAPSHOT`（Release Candidate）。
- QuickStart 示例：用户 `alice` / 密码 `demo-pass` / `clientType=WEB`；文档 `1001`（Project 101）、`2001`（Project 202）；权限 `document:read`、`document:update`。
- 决策代码来自 `DefaultAuthorizationEngine`：成功 `ALLOWED`；拒绝可能为 `PERMISSION_DENIED`、`SCOPE_DENIED`、`IDENTITY_DOMAIN_MISMATCH`、`CLIENT_TYPE_MISMATCH` 及 Profile 系列。

## 关键事实速查（真实源码，勿改写）

### IamPrincipal

`iam-core/.../core/model/IamPrincipal.java`，record 组件顺序：

`userId, identityId, identityDomain, activeProfileId(Long), templateVersionId(Long), clientType, authorizationVersion`。

校验：userId>0；identityId/identityDomain/clientType 非空；activeProfileId/templateVersionId>0；authorizationVersion>=0。

### LoginRequest

`iam-authentication/.../LoginRequest.java`，组件：

`username, password, clientType, clientInstance, ipAddress, userAgent, deviceType, osName, browserName, appVersion, requestId`。

username/password/clientType 必填。

### AuthenticationResult

record `(accessToken, sessionId, expiresAt(Instant), principal(IamPrincipal))`。登录与 Profile Switch 都返回该语义。

### IdentityAuthenticator

函数式接口：

```java
Optional<IamPrincipal> authenticate(LoginRequest request);
```

### AuthorizationEngine

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
default void require(IamPrincipal principal, AuthorizationRequest request)
```

### AuthorizationRequest

record `(permissionCode, domain, clientType, resource(ResourceDescriptor), scopeAccess(ScopeAccess))`。

### AuthorizationDecision

record `(allowed, decisionCode, steps)`；steps 为 `List<AuthorizationDecisionStep>`。

### ScopeAccess

enum：`READ`、`WRITE`。

### ResourceDescriptor / ResourceScope

- ResourceDescriptor：`(resourceType, resourceId, parentPath(List<String>), attributes(Map<String,String>))`
- ResourceScope：`(scopeType, scopeRefId, accessMode(ScopeAccess))`

### ResourceHierarchyProvider

```java
boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
```

### AuthorizationProfile

组件：`profileId, userId, profileName, templateVersionId, clientTypes(Set<String>), enabled, revoked, validFrom, validUntil, scopes(List<ResourceScope>)`。

### PermissionTemplateVersion

record `(versionId, templateId, versionNumber, status(TemplateVersionStatus), permissions(Set<String>))`。

`TemplateVersionStatus`：`DRAFT/PUBLISHED/RETIRED`。

### AuthSession / TokenRecord

- AuthSession：`(sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt, lastSeenAt, expiresAt, revokedAt, logoutAt, revokeReason)`；
- TokenRecord：`(sessionId, principal, expiresAt)`。

### RequirePermission

```java
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface RequirePermission {
    String value();
    ScopeAccess access() default ScopeAccess.READ;
}
```

### MvcResourceDescriptorResolver

```java
Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod);
```

### 授权拦截决策顺序

`IamAuthorizationInterceptor`：Method 注解优先于 Class 注解。随后：

1. 无 principal → 401 `UNAUTHENTICATED`
2. 无 resource resolver → 500 `RESOURCE_RESOLUTION_UNAVAILABLE`
3. resolver 返回 empty → 404 `RESOURCE_NOT_FOUND`
4. engine.allowed()==false → 403 `ACCESS_DENIED`

### 默认 MVC 失败 code

401=`IAM_UNAUTHENTICATED`，403=`IAM_ACCESS_DENIED`，404=`IAM_RESOURCE_NOT_FOUND`，500=`IAM_RESOURCE_RESOLUTION_UNAVAILABLE`。默认响应为 `application/problem+json`。

### 配置（IamProperties，前缀 iam）

| 属性 | 类型 | 默认 | 约束 |
| --- | --- | --- | --- |
| iam.enabled | boolean | true | — |
| iam.token.ttl | Duration | 8h | 必须为正 |
| iam.token.redis-prefix | String | iam | 非空 |
| iam.session.enabled | boolean | true | 属性已暴露 |
| iam.session.touch-interval | Duration | 10m | — |
| iam.schema.enabled | boolean | true | — |
| iam.schema.history-table | String | iam_flyway_schema_history | 非空 |
| iam.audit.enabled | boolean | true | 属性已暴露 |
| iam.diagnostics.enabled | boolean | true | 属性已暴露 |
| iam.client-types | List<String> | [WEB] | 至少一个非空值；登录 clientType 精确匹配 |

注意：audit/diagnostics/session 的 enabled 当前自动配置并未全部据此条件化 Bean。

### 基础设施配置边界

- MySQL 地址、账号、密码使用 Spring Boot `spring.datasource.*`；
- Redis 地址、端口、密码使用 Spring Boot `spring.data.redis.*`；
- IAM 不额外定义第二套 MySQL / Redis 地址属性；
- 默认 `iam.schema.enabled=true` 时使用宿主 DataSource 自动运行 IAM Flyway migration；
- Redis 无需预建 Key；多应用共享 Redis 时建议设置独立 `iam.token.redis-prefix`。

### HTTP 端点

- POST /iam/auth/login → 200/401
- POST /iam/auth/logout → 204/401
- GET /iam/auth/me → 200/401
- GET /iam/sessions → 200/401
- POST /iam/sessions/{sessionId}/revoke → 204/401/404
- POST /iam/sessions/revoke-others → 204/401
- GET /iam/authorization/profiles → 200
- POST /iam/authorization/profiles/{profileId}/switch → 200/401/404
- POST /iam/authorization/diagnostics → 200

管理端点以 `iam-management-web` OpenAPI 为准。

### 登录 / 切换响应体

LoginResponse 必填：`accessToken`、`sessionId`、`expiresAt`、`principal`。

Principal 必填：`userId,identityId,identityDomain,clientType,authorizationVersion`；可空：`activeProfileId,templateVersionId`。

### 演示用户

`iam-example` 中：

- alice 默认 → userId=101, activeProfileId=401, templateVersionId=301；
- 另有 author-a、reader-b、disabled-c、operator-a/b 等 Consumer Showcase 身份。

### QuickStartDemoSeeder

仅在 `dev` Profile + `iam.example.seed-demo=true` 时启用：

- permission：701 document:read、702 document:update；
- template：201 reader、202 editor；
- version：301 reader、302 editor；
- profile：401 alice-reader-project-101、402 alice-editor-project-101；
- scope：Reader → Project 101 READ；Editor → Project 101 READ + WRITE。

Seeder 会重置演示 IAM 投影，只能用于专用演示数据库。

### 文档路由

- GET /public/health → 200 `ok`
- GET /api/documents/{id} + `@RequirePermission("document:read")`
- POST /api/documents/{id} + `@RequirePermission(value="document:update", access=WRITE)`
- DocumentUpdate：`{"status":"..."}`

Resolver 把文档映射成 `ResourceDescriptor("DOCUMENT", id, parentPath=["PROJECT:"+projectId,"DEPARTMENT:"+departmentId], {})`。

### 源码对照表

GitHub blob 基址：`https://github.com/wbh123/iam/blob/main/`

- IamPrincipal: `iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java`
- ScopeAccess: `iam-core/src/main/java/io/github/iamstarter/core/model/ScopeAccess.java`
- ResourceDescriptor/ResourceScope: `iam-core/src/main/java/io/github/iamstarter/core/model/`
- ResourceHierarchyProvider: `iam-core/src/main/java/io/github/iamstarter/core/port/ResourceHierarchyProvider.java`
- IdentityAuthenticator: `iam-authentication/src/main/java/io/github/iamstarter/authentication/IdentityAuthenticator.java`
- AuthorizationEngine 等：`iam-authorization/src/main/java/io/github/iamstarter/authorization/`
- AuthSession/TokenRecord: `iam-session/src/main/java/io/github/iamstarter/session/`
- MVC 授权集成：`iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/`
- IamProperties / Bearer Filter: `iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/`
- Example: `iam-example/src/main/java/io/github/iamstarter/example/`
- OpenAPI: `iam-management-web/src/main/resources/openapi/iam.yaml`

## 写作语言与风格

- 简体中文，技术术语保留必要英文原名；
- 代码注释尽量简洁；
- 页面之间使用 Starlight 内部链接 `/组/slug/`；
- 优先让第一次接入的开发者看懂“怎么配、怎么启动、接口应该返回什么”；
- 框架维护者测试命令放在开发 / 贡献文档，不要塞进用户 QuickStart。
