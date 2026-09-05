# IAM 文档站页面创作规范

给子代理的权威规范。创作时必须严格遵守。

## 技术栈与环境

- 文档站：Astro 7.3.1 + @astrojs/starlight 0.42.0，位于仓库 worktree：
  `/home/wbh/projects/IAM-Spring-Boot-Starter/.worktrees/release-0.1.0/iam-docs`
- 内容源目录：`iam-docs/src/content/docs/<group>/<slug>.md`
- 内容文件使用 **Markdown（.md）**，不要用 .mdx。
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

sidebar（astro.config.mjs 中）引用这些 slug，**必须全部创建**，否则 build 报错：

- 首页：`index.md`（已有，勿改）
- intro：what-is-iam ✓(已有)、core-capabilities ✓(已有)、use-cases、architecture
- getting-started：quick-start、installation、configuration、project-structure
- concepts：identity-principal、permission、permission-template、profile、resource-scope、session、authorization-version
- authentication：login、identity-authenticator、client-type、current-user、opaque-token
- authorization：model、require-permission、mvc-resource-descriptor-resolver、resource-scope、authorization-engine、authorization-policy
- profile-session：profile、profile-switch、session-management、session-revoke、authorization-version
- diagnostics：authorization-diagnostics、audit、custom-error-handling
- migration：overview、shadow-mode、data-projection、rollback
- operations：mysql、redis、reverse-proxy、security-model、production-checklist
- reference：configuration、public-api、http-api、error-codes、modules
- resources：example、faq、release-notes、changelog、contributing

带 ✓ 的文件已存在（内容可参考但你可扩展，勿重复创建）。
同 slug 出现在不同分组（如 concepts/profile 与 profile-session/profile）是允许的——它们在各自目录下独立存在。

## 内容质量硬性要求

每页必须做到（缺一不可）：
1. 说明这个主题**解决什么问题**；
2. 解释**关键概念**；
3. 给出**真实、可复制的示例**（代码/curl/表）；
4. 提供到真实源码的**链接**（用 GitHub blob URL，仓库为 https://github.com/wbh123/iam ，分支 main ，源码路径可参考下方对照表）。

## 禁止

- 页面正文出现 `TODO`、`Coming soon`、`TBD`、`FIXME` 等占位词；
- 编造不存在的配置项、HTTP 端点、类名、字段；
- 编造「已发布到 Maven Central / 正式 0.1.0」；一律表述为 `0.1.0-SNAPSHOT`（Release Candidate），**尚未发布**。

## 版本与一致性措辞

- 当前版本写作：`0.1.0-SNAPSHOT`（Release Candidate）。
- 资源示例用 QuickStart 场景：用户 `alice` / 密码 `demo-pass` / `clientType=WEB`；文档 `1001`（project 101）、`2001`（project 202）；权限 `document:read`、`document:update`。
- 决策代码（真实，来自 DefaultAuthorizationEngine）：成功 `ALLOWED`；拒绝可能为 `PERMISSION_DENIED`、`SCOPE_DENIED`、`IDENTITY_DOMAIN_MISMATCH`、`CLIENT_TYPE_MISMATCH` 及 Profile 系列（见下文 Profile 页）。

## 关键事实速查（真实源码，勿改写）

### IamPrincipal（iam-core/.../core/model/IamPrincipal.java）
record，组件顺序：
`userId, identityId, identityDomain, activeProfileId(Long), templateVersionId(Long), clientType, authorizationVersion`。
校验：userId>0；identityId/identityDomain/clientType 非空；activeProfileId/templateVersionId>0；authorizationVersion>=0。

### LoginRequest（iam-authentication/.../LoginRequest.java）
record，组件：`username, password, clientType, clientInstance, ipAddress, userAgent, deviceType, osName, browserName, appVersion, requestId`。username/password/clientType 必填。便捷构造器 `(username,password,clientType)` 与 `(username,password,clientType,clientInstance)`。

### AuthenticationResult
record `(accessToken, sessionId, expiresAt(Instant), principal(IamPrincipal))`。这是 login 与 profile switch 的返回。

### IdentityAuthenticator（函数式接口）
`Optional<IamPrincipal> authenticate(LoginRequest request);`

### AuthorizationEngine（接口）
```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
default void require(IamPrincipal principal, AuthorizationRequest request) // 拒绝时抛 AuthorizationDeniedException
```

### AuthorizationRequest
record `(permissionCode, domain, clientType, resource(ResourceDescriptor), scopeAccess(ScopeAccess))`

### AuthorizationDecision
record `(allowed, decisionCode, steps)`；steps 为 `List<AuthorizationDecisionStep>`。
AuthorizationDecisionStep record `(code, passed, reason)`。

### ScopeAccess
enum：`READ`、`WRITE`。

### ResourceDescriptor
record `(resourceType, resourceId, parentPath(List<String>), attributes(Map<String,String>))`。parentPath 形如 `["PROJECT:101", "DEPARTMENT:1"]`。
### ResourceScope
record `(scopeType, scopeRefId, accessMode(ScopeAccess))`，形如 `("PROJECT","101",READ)`。

### ResourceHierarchyProvider（接口）
`boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);`

### AuthorizationProfile（record）
组件：`profileId, userId, profileName, templateVersionId, clientTypes(Set<String>), enabled, revoked, validFrom, validUntil, scopes(List<ResourceScope>)`。

### PermissionTemplateVersion
record `(versionId, templateId, versionNumber, status(TemplateVersionStatus), permissions(Set<String>))`。TemplateVersionStatus enum：`DRAFT/PUBLISHED/RETIRED`。

### AuthSession / TokenRecord（iam-session/.../session/）
- AuthSession record：`(sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt, lastSeenAt, expiresAt, revokedAt, logoutAt, revokeReason)`；方法 `revoked()/revoke(at,reason)/touch(at)`。
- TokenRecord record：`(sessionId, principal, expiresAt)`。

### RequirePermission（注解，autoconfigure/web/）
```java
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface RequirePermission { String value(); ScopeAccess access() default ScopeAccess.READ; }
```

### MvcResourceDescriptorResolver（函数式接口）
`Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod);`

### 授权拦截决策顺序（IamAuthorizationInterceptor，真实）
method 注解存在取 method，否则取 class 注解（**Method 优先于 Class**）。随后：
1. 无 principal → 401 `UNAUTHENTICATED`
2. 无 resource resolver → 500 `RESOURCE_RESOLUTION_UNAVAILABLE`
3. resolver 返回 empty → 404 `RESOURCE_NOT_FOUND`
4. engine.allowed()==false → 403 `ACCESS_DENIED`

### 默认 MVC 失败 code（ProblemDetail）
401=`IAM_UNAUTHENTICATED`，403=`IAM_ACCESS_DENIED`，404=`IAM_RESOURCE_NOT_FOUND`，500=`IAM_RESOURCE_RESOLUTION_UNAVAILABLE`。响应 `application/problem+json`。

### 配置（IamProperties，前缀 iam）
| 属性 | 类型 | 默认 | 约束 |
| iam.enabled | boolean | true | — |
| iam.token.ttl | Duration | 8h | 必须为正 |
| iam.token.redis-prefix | String | iam | 非空 |
| iam.session.enabled | boolean | true | — |
| iam.session.touch-interval | Duration | 10m | — |
| iam.schema.enabled | boolean | true | — |
| iam.schema.history-table | String | iam_flyway_schema_history | 非空 |
| iam.audit.enabled | boolean | true | — |
| iam.diagnostics.enabled | boolean | true | — |
| iam.client-types | List<String> | [WEB] | ≥1 非空；登录 clientType 必须精确匹配 |

注意：audit/diagnostics/session 的 enabled 仅是属性暴露，当前自动配置并未按此做条件化 bean——文档若涉及要按真实行为写（可注明「属性已暴露」）。

### HTTP 端点（真实，来自 iam-management-web OpenAPI）
- POST /iam/auth/login → 200/401
- POST /iam/auth/logout → 204/401
- GET /iam/auth/me → 200/401
- GET /iam/sessions → 200/401
- POST /iam/sessions/{sessionId}/revoke → 204/401/404
- POST /iam/sessions/revoke-others → 204/401
- GET /iam/authorization/profiles → 200
- POST /iam/authorization/profiles/{profileId}/switch → 200/401/404
- POST /iam/authorization/diagnostics → 200
- 管理：PUT /iam/admin/templates/{versionId}、GET /iam/admin/users、PUT /iam/admin/users/{userId}、GET /iam/admin/users/{userId}/identities、PUT /iam/admin/identities/{identityId}、PUT /iam/admin/profiles/{profileId}、PUT /iam/admin/profiles/{profileId}/scopes、POST /iam/admin/users/{userId}/authorization-version、POST /iam/admin/sessions/{sessionId}/revoke、POST /iam/admin/users/{userId}/sessions/revoke（管理接口做对应 iam.admin.* permission 授权检查）。

### 登录/切换响应体 schema
LoginResponse 必填字段：`accessToken`、`sessionId`、`expiresAt`、`principal`。principal 必填：`userId,identityId,identityDomain,clientType,authorizationVersion`；可空：`activeProfileId,templateVersionId`。
`GET /iam/sessions` 返回 `{ items: [ SessionResponse ] }`，SessionResponse 含 `sessionId,userId,clientType,loginAt,lastSeenAt,expiresAt` 等。

### 演示用户（iam-example ExampleIdentityAdapter，真实）
- alice 登录默认 → IamPrincipal(userId=101, activeProfileId=401, templateVersionId=301)
- author-a → 独立消费身份 app-user-101，editor 模板 302
- reader-b(userId 102)、disabled-c(disabled)、operator-a/b

### Seeder 数据（QuickStartDemoSeeder，dev profile + iam.example.seed-demo=true）
- permission：701 document:read、702 document:update
- template：201 quickstart-document-reader、202 quickstart-document-editor
- version：301（reader 模板 PUBLISHED，含 701）、302（editor 模板 PUBLISHED，含 701+702）
- profile：401 alice-reader-project-101（user101/template301/[WEB]/default）、402 alice-editor-project-101（user101/template302/[WEB]/非default）
- scope：401→(PROJECT,101,READ)；402→(PROJECT,101,READ)+(PROJECT,101,WRITE)
- 结论：alice 默认(401)只能读 project101 内文档，POST(需 WRITE)被拒 SCOPE_DENIED；切到 402 后具有 document:update + PROJECT101 WRITE，可写。

### 文档路由（iam-example DocumentController，真实）
- GET /public/health → 200 "ok"（无需认证）
- GET /api/documents/{id} @RequirePermission("document:read") → 200 Document / 404
- POST /api/documents/{id} @RequirePermission(value="document:update", access=WRITE) @RequestBody DocumentUpdate → 200 / 404
- Document JSON：`{"id","projectId","departmentId","status"}`；DocumentUpdate：`{"status"}`。
- resolver 把 {id} 解析成 ResourceDescriptor("DOCUMENT", id, parentPath=["PROJECT:"+projectId,"DEPARTMENT:"+departmentId], {})

### 源码对照表（GitHub blob 基址：https://github.com/wbh123/iam/blob/main/）
- IamPrincipal: iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java
- ScopeAccess: iam-core/src/main/java/io/github/iamstarter/core/model/ScopeAccess.java
- ResourceDescriptor/ResourceScope: iam-core/src/main/java/io/github/iamstarter/core/model/
- ResourceHierarchyProvider: iam-core/src/main/java/io/github/iamstarter/core/port/ResourceHierarchyProvider.java
- IdentityAuthenticator: iam-authentication/src/main/java/io/github/iamstarter/authentication/IdentityAuthenticator.java
- LoginRequest/AuthenticationResult/AuthorizationProfileSwitchService: iam-authentication/src/main/java/io/github/iamstarter/authentication/
- AuthorizationEngine/AuthorizationRequest/AuthorizationDecision/AuthorizationProfile/PermissionTemplateVersion: iam-authorization/src/main/java/io/github/iamstarter/authorization/
- AuthSession/TokenRecord/LoginResult: iam-session/src/main/java/io/github/iamstarter/session/
- RequirePermission/MvcResourceDescriptorResolver/IamAuthorizationInterceptor: iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/
- IamProperties/IamBearerTokenFilter: iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/
- Example 演示: iam-example/src/main/java/io/github/iamstarter/example/
- OpenAPI: iam-management-web/src/main/resources/openapi/iam.yaml

## 写作语言与风格

- 简体中文，技术术语可保留英文原名。
- 代码注释尽量中文或简洁。
- 每页控制在适度篇幅（300~800 字中文为宜，参考页可更详）。
- 页面之间用 Starlight 内部相对链接 `/组/slug/` 互链。
