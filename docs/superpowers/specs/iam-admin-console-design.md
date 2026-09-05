# IAM Admin Console 设计（development）

> 目标分支：`feature/iam-admin-console`（基于 `main` = 0.1.0 release candidate）
> 版本：并入 `0.1.0-SNAPSHOT` 开发（2026-09-05 决定：版本保持在 0.1.0，撤销原计划的
> 0.2.0-SNAPSHOT bump；`git revert c9d2409`）
> 状态：已实现（READY FOR ADMIN CONSOLE REVIEW）

---

## 1. 目标

在既有 IAM Spring Boot Starter（Authentication / Authorization / Session / Audit /
Diagnostics + iam-management-web）之上，交付：

1. 一组**完整、可查询、可运维**的 Management API（全部向后兼容既有 `/iam/admin/**`）。
2. 一个**可独立部署的 IAM Admin Console**（`iam-admin-web/`，Vue 3 + TS + Vite +
   Element Plus），以 `iam-management-web/src/main/resources/openapi/iam.yaml` 为唯一接口契约。
3. 保证 **IAM 管理 IAM**：所有 admin 调用由 `AuthorizationEngine` 按
   `iam.admin.*` 细粒度权限重新校验，不引入 `ADMIN ROLE`/`SUPER_ADMIN` 之类旁路。

约束底线（优先级从高到低）：

```text
安全边界 > 领域模型正确 > OpenAPI 契约 > 管理 API 完整 > 前后端类型一致
         > 核心页面可用 > 自动测试 > UI 美观
```

---

## 2. 现状审计结论（Management API Gap Analysis）

### 2.1 已阅读模块与关键发现

| 模块 | 关键结论 |
| --- | --- |
| `iam-core` | `IamUser` / `Identity` / `IamPrincipal` 为不可变 record。`IamPrincipal` 强制携带 `activeProfileId` 与 `templateVersionId`。ports：`IamUserRepository`、`IdentityRepository`、`ResourceHierarchyProvider`。 |
| `iam-authorization` | `PermissionTemplateVersion` 是（templateId, versionNumber, status, permissions）不可变版本模型；`PermissionTemplateService.replace()` 已对 PUBLISHED 施加 **immutable 保护**（内容变化即抛 IllegalStateException）。DB 中存在 `iam_permission_template`（模板元数据）与 `iam_permission`（轻量 registry：permission_code/display_name/description/enabled）但**没有领域模型与查询能力**。`AuthorizationProfile` 已有完整读写。 |
| `iam-session` | `AuthSession` 字段完整（含 ip/userAgent/logoutAt/revokeReason，V6 迁移补齐）。Repository 仅支持 `activeForUser`/`findById`/`save`/`touch`，**缺少跨用户查询**。 |
| `iam-audit` | `AuditRecord` + `AuditSubjectLink` 写入完备，但 `AuditRepository` **只写不读**。 |
| `iam-authentication` | `AccountGovernanceService` 提供用户/Identity 管理，含 listUsers（cursor 分页）。 |
| `iam-diagnostics` | `AuthorizationDiagnosticsService` 直接基于 `AuthorizationEngine.decide` 输出可解释 decision steps。 |
| `iam-persistence-mybatis` | Mapper XML + Row + MyBatis Repository 一一对应 ports；schema 由 V1–V6 Flyway 式 SQL 管理。 |
| `iam-management-web` | 4 个 controller 实现 OpenAPI 生成的 `*Api` 接口；Admin 写接口逐方法 `AuthorizationEngine.decide` + `iam.admin.*` permission。 |

### 2.2 已有 API

- Authentication：`POST /iam/auth/login`、`POST /iam/auth/logout`、`GET /iam/auth/me`
- Session（本人）：`GET /iam/sessions`、`POST /iam/sessions/{sessionId}/revoke`、
  `POST /iam/sessions/revoke-others`
- Authorization（本人）：`GET /iam/authorization/profiles`、
  `POST /iam/authorization/profiles/{profileId}/switch`、
  `POST /iam/authorization/diagnostics`
- Administration（写为主）：`listUsers` / `saveUser` / `listUserIdentities` /
  `saveIdentity` / `savePermissionTemplateVersion` / `saveAuthorizationProfile` /
  `replaceAuthorizationProfileScopes` / `incrementAuthorizationVersion` /
  `forceRevokeSession` / `forceRevokeUserSessions`

### 2.3 缺口清单

| 前端页面需要 | 现状 | 处置 |
| --- | --- | --- |
| 用户详情 | 无 GET `/admin/users/{id}` | 新增 |
| 用户列表过滤 | listUsers 仅 cursor | listUsers 增加可选过滤，保留 afterUserId/limit |
| Identity 详情 / 列表按用户 | listUserIdentities 已有；详情缺 | 新增 GET `/admin/identities/{identityId}` |
| Identity 删除 | 无 | **不做硬删除**，用 `enabled=false`（理由见 §8） |
| Permission 浏览 | 无 | 新增 Permission Explorer（registry 聚合） |
| Template 列表/详情/版本 | 仅 PUT 版本保存 | 新增 templates/template/versions/template-versions 查询 |
| Profile 列表/详情/用户的 profiles | 仅保存 + 本人列表 | 新增 profiles 查询 + scopes 查询 |
| Scope 查看 | 无 | 新增 GET `/admin/profiles/{profileId}/scopes` |
| Session 管理列表/过滤 | 无 | 新增 admin sessions 查询（禁 token） |
| Audit 查询 | 无（repository 只写） | 新增只读 audit-events 查询 |
| Dashboard 摘要 | 无 | 新增 overview（仅简单 count） |
| 登录后菜单能力 | 无 | 新增 `GET /iam/auth/capabilities`（本人可见集合） |

---

## 3. 架构决策

### 3.1 契约单一来源

`iam.yaml` 仍是唯一契约：

```text
OpenAPI iam.yaml
   ├── (openapi-generator spring, interfaceOnly) ──▶ Java *Api 接口 + DTO
   └── (openapi-generator typescript-axios) ──▶ iam-admin-web/src/api/generated/
```

- 前端**不手写** DTO 接口。
- Java 侧新查询端点**不使用现有 `Administration` tag**，避免把十几个新方法与历史
  大 controller 揉在一起；改用面向功能域的 tag，产生独立 `*Api` 接口与独立
  controller，便于按依赖注入测试。

新 tag 划分：

| tag | 生成接口 | controller |
| --- | --- | --- |
| `Management Users` | `ManagementUsersApi` | `IamManagementUsersController` |
| `Management Identity` | `ManagementIdentityApi` | 并入 users controller |
| `Management Permissions` | `ManagementPermissionsApi` | `IamManagementPermissionsController` |
| `Management Templates` | `ManagementTemplatesApi` | `IamManagementTemplatesController` |
| `Management Profiles` | `ManagementProfilesApi` | `IamManagementProfilesController` |
| `Management Sessions` | `ManagementSessionsApi` | `IamManagementSessionsController` |
| `Management Audit` | `ManagementAuditApi` | `IamManagementAuditController` |
| `Management Overview` | `ManagementOverviewApi` | `IamManagementOverviewController` |
| `Capabilities` | `CapabilitiesApi` | `IamCapabilitiesController` |

> 说明：controller 划分偏细是为了让每个 controller 依赖最少、测试独立；HTTP 层语义
> 不受影响。后端注册全部发生在 `IamAutoConfiguration`（与现状一致），不使用组件扫描。

### 3.2 读能力落位

管理查询多数落在既有关联聚合上。开发线仍为 `0.1.0-SNAPSHOT`（PUBLIC_API 明示“稳定候选，
非二进制兼容承诺”），因此采用**在既有公开 port 上追加只读方法**，并同步更新
`docs/PUBLIC_API.md`：

| port | 追加 |
| --- | --- |
| `IamUserRepository` (core) | `search(afterUserId, limit, username, userType, enabled)` |
| `AuthorizationProfileRepository` | `search(afterProfileId, limit, filters…)` |
| `PermissionTemplateVersionRepository` | `findById`(Optional) / `findByTemplateId` / template 摘要 `listTemplates`、`findTemplate` |
| `SessionRepository` | `adminSearch(afterLoginAt, afterSessionId, limit, userId, clientType, active)` |
| `AuditRepository` | `findEvents` / `findByEventId` |
| （新）core port | `OverviewRepository.count()` 返回 `OverviewMetrics` |

同时**新增轻量领域模型**（不改变既有模型语义）：

- `io.github.iamstarter.authorization.PermissionTemplate`：模板元数据（DB 已存在表，
  补齐模型/查询；模板级读操作全部只读）。
- `io.github.iamstarter.authorization.PermissionTemplateSummary`：模板 + 最新版本投影，
  仅用于 Web 查询，不作为写模型。
- `io.github.iamstarter.audit.AuditEvent`：审计读模型（record + subjects），审计永远只读。
- `io.github.iamstarter.core.model.OverviewMetrics`：仪表盘 count 快照。

### 3.3 授权语义（本设计最核心）

所有 `/iam/admin/**`（含新增查询）复用既有方式——控制器内构造
`AuthorizationRequest` 调用 `AuthorizationEngine.decide`，命中 profile 权限
（template version 内的 permission code）且通过 scope 才放行：

- 读操作：`ScopeAccess.READ`
- 写/撤销操作：`ScopeAccess.WRITE`
- resourceType 沿用现有 token 风格（`IAM_USER`、`IAM_USER_COLLECTION`、
  `IAM_AUTHORIZATION_PROFILE` 等）。

不引入任何角色/超管旁路；是否放行始终由 `AuthorizationEngine` 裁决。
默认 `ResourceHierarchyProvider` 返回 false 的问题属于宿主接线职责（宿主需提供能识别
IAM 管理资源的 provider，详见 `docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md`），不在本模块
内引入“总是放行”逻辑。

### 3.4 Permission registry 结论

`iam_permission` 已是独立 registry 表（permission_code / display_name / description /
enabled），但**没有 domain 字段**，也没有领域层模型。为不破坏核心模型，本阶段：

- 新增 `GET /iam/admin/permissions` 只做 **Permission Explorer**：
  聚合 registry + template 中使用情况（in_use_count），展示 code/displayName/
  description/enabled/inUse。搜索：keyword / domain 字符串过滤（domain 非独立列，按
  code 前缀/包含过滤）、cursor `afterId`、`limit`。
- **不**新增 `PermissionDefinition` 写模型，不引入 domain 列迁移，避免污染 0.1.0
  稳定语义。

### 3.5 Template 版本不可变性审计

`PermissionTemplateService.replace()` 现有保护：

- PUBLISHED：内容变化 → 抛 IllegalStateException（不可变）；
- DRAFT：可编辑；
- RETIRED：当前仅比对 identity 不变；RETIRED 可被改回 PUBLISHED（重发布）。

HTTP 层：PUT 保存 PUBLISHED 内容一致时幂等 204；不一致时依赖 service 抛异常——现状
controller 未映射该异常（默认 500）。本轮**保留语义不变**，但把 service 异常统一映射为
Problem Detail `409 CONFLICT`（code `IAM_TEMPLATE_VERSION_IMMUTABLE`），不改状态机。
不做“未经测试的行为变更”，不新增 RETIRED → PUBLISHED 禁令。

### 3.6 Diagnostics 权限收紧

现有 `POST /iam/authorization/diagnostics` 仅要求认证。按 §3.6 要求，新增权限
`iam.admin.diagnostics`：该端点继续可用但必须命中该 permission。这属于 0.1.0 行为收紧，
会同步更新 docs（自服务诊断不在本轮提供，Authorization Playground 面向具备
`iam.admin.diagnostics` 的管理者）。

---

## 4. Management API 清单（Admin Console 新增/扩展）

> 全部新增操作不删除、不重定义既有 10 个 admin 端点。

### 4.1 用户（扩展既有 + 新查询）

| 操作 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| (改) `listUsers` | `GET /iam/admin/users` | user.read | 追加可选 `username` / `userType` / `enabled` 过滤，保留 `afterUserId` / `limit` |
| (新) `getUser` | `GET /iam/admin/users/{userId}` | user.read | 404 当不存在 |

响应（复用 `UserResponse`）：`userId / username / userType / enabled / authorizationVersion`。

### 4.2 Identity

| 操作 | 路径 | 权限 |
| --- | --- | --- |
| (新) `getIdentity` | `GET /iam/admin/identities/{identityId}` | identity.read |

保留 `GET /iam/admin/users/{userId}/identities` 与 `PUT /iam/admin/identities/{identityId}`。
Identity 元数据不得返回 password/hash/credential_secret（模型本身不包含此类字段）。

### 4.3 Permission Explorer

| 操作 | 路径 | 权限 | 参数 |
| --- | --- | --- | --- |
| (新) `listPermissions` | `GET /iam/admin/permissions` | permission.read | `keyword`、`domain`、`afterId`、`limit` |

响应项：`permissionCode / displayName / description / enabled / inUseCount`。

### 4.4 Template

| 操作 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| (新) `listTemplates` | `GET /iam/admin/templates` | template.read | cursor `afterTemplateId` / `limit`；返回摘要含最新版本号与状态 |
| (新) `getTemplate` | `GET /iam/admin/templates/{templateId}` | template.read | 404 |
| (新) `listTemplateVersions` | `GET /iam/admin/templates/{templateId}/versions` | template.read | 按版本号降序 |
| (新) `getTemplateVersion` | `GET /iam/admin/template-versions/{versionId}` | template.read | 404 |

### 4.5 Profile & Scope

| 操作 | 路径 | 权限 |
| --- | --- | --- |
| (新) `listProfiles` | `GET /iam/admin/profiles` | profile.read（过滤 userId/templateVersionId/enabled/revoked/clientType） |
| (新) `getProfile` | `GET /iam/admin/profiles/{profileId}` | profile.read |
| (新) `listUserProfiles` | `GET /iam/admin/users/{userId}/profiles` | profile.read |
| (新) `getProfileScopes` | `GET /iam/admin/profiles/{profileId}/scopes` | scope.read |

响应：`AuthorizationProfileResponse`（含 scopes）与 `ResourceScope[]`。
管理端只展示 `scopeType / scopeRefId / accessMode`，不解析业务名称。

### 4.6 Session

| 操作 | 路径 | 权限 |
| --- | --- | --- |
| (新) `adminListSessions` | `GET /iam/admin/sessions` | session.read（userId/clientType/active/after/limit） |
| (新) `listUserSessions` | `GET /iam/admin/users/{userId}/sessions` | session.read |
| (新) `getAdminSession` | `GET /iam/admin/sessions/{sessionId}` | session.read |

`SessionResponse` 追加只读字段 `status` 与 `revokeReason`（不破坏既有必需字段）。
**绝不返回 token/Redis key。**

### 4.7 Audit（只读）

| 操作 | 路径 | 权限 |
| --- | --- | --- |
| (新) `listAuditEvents` | `GET /iam/admin/audit-events` | audit.read |
| (新) `getAuditEvent` | `GET /iam/admin/audit-events/{eventId}` | audit.read |

过滤器：userId/identityId/eventType(actionCode)/resourceType/resourceId/sessionId/
from/to/after/limit。**无 PUT/DELETE audit。**

### 4.8 Overview

`GET /iam/admin/overview`（权限 `iam.admin.overview.read`）返回：
`totalUsers/enabledUsers/activeSessions/activeProfiles/templateVersions/auditEventsToday`。
全部是单表 COUNT（或加轻 where），**不做大表聚合重构**。

### 4.9 Capabilities（本人）

`GET /iam/auth/capabilities`（无需 admin 权限；只返回**当前 principal 自己**可见内容）：
principal + activeProfileId/templateVersionId + permissions（当前 profile 模板版本包含的
permission codes，按 `iam.admin.*` 归类）+ scopes。菜单据此渲染；安全边界始终在后端。

---

## 5. 权限代码表（iam.admin.*）

统一沿用 §4 并新增 2 个：

```text
iam.admin.user.read                    # 用户读/列表
iam.admin.user.write                   # 用户编辑/启停
iam.admin.identity.read
iam.admin.identity.write               # identity 启停（=保存 enabled=false）
iam.admin.permission.read
iam.admin.template.read
iam.admin.template.write
iam.admin.profile.read
iam.admin.profile.write
iam.admin.scope.read
iam.admin.scope.write
iam.admin.session.read
iam.admin.session.revoke
iam.admin.session.revoke-user
iam.admin.audit.read
iam.admin.diagnostics                  # Authorization Playground / 诊断求值
iam.admin.authorization-version.increment
iam.admin.overview.read                # 新增：dashboard 摘要
```

`PUT /iam/admin/identities/{identityId}` 沿用 `identity.write`；Identity 停用即
`enabled=false` 保存，语义等同“删除”。

---

## 6. 前端工程（iam-admin-web）

### 6.1 技术栈

Vue 3.5 + TypeScript + Vite 6 + Pinia + Vue Router 4 + Element Plus + Axios；
测试 Vitest + Vue Test Utils + jsdom；Node 22+；包管理 npm。

### 6.2 目录（§按需求原文）

```text
iam-admin-web/
  package.json / package-lock.json / vite.config.ts / tsconfig.json / index.html
  openapitools.json
  src/
    api/{client.ts, generated/}     # generated = openapi-generator typescript-axios
    assets/ components/ layouts/AdminLayout.vue
    router/ stores/{auth.ts,app.ts}
    views/{login,dashboard,users,permissions,templates,profiles,sessions,audit,
           diagnostics,account,error}
    App.vue  main.ts
  tests/
```

### 6.3 API Client 策略

- 唯一生成命令 `npm run api:generate`（openapi-generator-cli，generator 版本 7.6.0 与
  Java 侧一致；typescript-axios）。
- **生成物不提交**；CI 每次生成。设计采用需求原文的“生成物不提交”路线，并在
  admin-web CI 中先 `api:generate` 再 type-check/test/build。契约变化即类型变化，从而
  前后端 drift 在 CI 显式暴露。
- Axios 单例 + 拦截器：请求注入 `Authorization: Bearer`；响应 401 → auth store 登出并
  跳转 `/login`；403 → 路由 `/forbidden` 提示；**页面组件一律经 store/composable 调
  api client，禁止散落裸 axios**。
- 登录 token 存 `sessionStorage`（不默认 localStorage；理由：opaque token 等价会话凭证，
  会话关闭即失效，降低 XSS 持久窃取面）。

### 6.4 路由与权限

- 页面：`/login /dashboard /users /users/:id /permissions /templates /templates/:id
  /profiles /profiles/:id /sessions /audit /diagnostics /account /403 /404`。
- `beforeEach` 守卫：无 token → `/login`；有 token 按需拉 `/auth/me`+`/auth/capabilities`
  决定菜单；`/account` 无需 admin 权限。
- 菜单按 capabilities.permissions 中 `iam.admin.*` 渲染；未授权菜单隐藏仅是 UI 优化，
  后端 `/iam/admin/**` 强制 `AuthorizationEngine`。

### 6.5 页面要点

- 用户详情用 Tabs（基本信息/Identity/Profile/Session/Audit）。
- 诊断页展示 decision steps（✓/✗）与 scope/policy 阶段。
- Session 列表不展示任何 token 字段；撤销用 Dialog+原因+二次确认。
- 服务端内容一律文本插值渲染，禁止 `v-html`。
- Profile 页含只读 Effective Permissions（Template Version → Permission Codes）。

---

## 7. 测试策略

- Backend：每个新端点一个 `@WebMvcTest`-等价 standalone MockMvc 测试类，覆盖
  401(无 principal)/403(无权限)/200/204、404、参数校验、分页；管理查询**不只 happy path**。
- Persistence：`IamManagementQueryPersistenceContractTest`（Testcontainers MySQL）
  覆盖新增 mapper 查询（profile 过滤、session 过滤、audit 事件、template 摘要、overview、
  permission explorer）。
- 契约：OpenAPI Java 侧在 `mvn test` 中通过
  openapi-generator 重新生成完成编译，保证 Java↔yaml 一致。
- Frontend：Vitest 覆盖 auth store、API 401 处理、route guard、用户列表、profile 表单、
  session 撤销确认、diagnostics 决策渲染。
- E2E（Playwright）：**本轮延后并记录原因**——需要 MySQL+Redis+种子数据组成稳定后端；
  提供两个 smoke 场景清单，待独立管理端部署环境出现后补充（见 §12）。

---

## 8. 领域/数据决策记录

1. Identity 硬删除 **不提供**。理由：identity 参与审计 subject link 外键、登录事件关联，
   硬删会破坏审计链条与不可变性；停用（enabled=false）+ 授权版本自增即可使其立即失效。
2. Template RETIRED→PUBLISHED 现状保留（已有 service 不可变性保护不变），仅补 409 映射。
3. Permission registry 只读聚合，不新增 domain 列。
4. scope 展示只用 scopeType/scopeRefId/accessMode；业务名称由宿主扩展解释。
5. 仪表盘不做重统计：所有指标为 count/轻 where。

---

## 9. CI/CD 计划

- 保留 `verify.yml`（Verify IAM Starter）并保持全绿；管理端 backend 测试由其包含
  （`mvn -pl iam-management-web -am test` 作为验证步骤之一，或在 verify 中新增 job）。
- 新增 `.github/workflows/admin-web.yml`：push/PR main 与 feature/iam-admin-console 触发；
  setup Node 22 → `npm ci` → `npm run api:generate` → `type-check` → `test` → `build`
  （+lint 若配置）。
- Docs：`docs.yml` 继续验证 iam-docs；docs-pages 发布不受影响。
- 新增文档页“IAM Admin Console / Management Console”，纳入 Starlight sidebar 并保持
  `iam-docs`（技术文档）与 `iam-admin-web`（应用）职责独立。

---

## 10. 版本与分支

- `main` = 0.1.0 RC 线（`0.1.0-SNAPSHOT`）。开发分支 `feature/iam-admin-console`。
- 开发版本保持 `0.1.0-SNAPSHOT`（执行时通过 `git revert c9d2409` 撤销 0.2.0-SNAPSHOT
  bump，根 POM 与各子模块 parent 版本一致）；不修改 0.1.0 授权语义。
- 完成后停在 `READY FOR ADMIN CONSOLE REVIEW`，不自动 merge main、不打 tag、不建
  Release。

---

## 11. 安全审计检查单（实现后执行）

- [ ] token 不出现在日志 / console / URL query / audit metadata。
- [ ] 前端默认 escape；服务端内容不用 `v-html`。
- [ ] logout 调 `POST /iam/auth/logout`（不只清浏览器）。
- [ ] CORS 生产不放开 `*`；console 与 API 同源或显式白名单。
- [ ] 每个 admin 端点均过 `AuthorizationEngine`；无角色旁路。
- [ ] 401/403 统一由 problem+json 表达，前端统一处理，无 `alert(error)`。

---

## 12. 后续项（本轮记录、延后执行）

- Playwright E2E smoke（依赖稳定可种子化后端环境）。本轮记录的两个 smoke 场景：

  1. `login → dashboard → users → open user detail`：登录（WEB client type），断言
     dashboard 计数卡与最近 Audit 渲染，进入用户列表，打开某用户详情 Tabs。
  2. `login → sessions → revoke`：登录后进入 Session 管理，选择一条 ACTIVE Session，
     触发“撤销”Dialog 并确认，断言该行状态变为非 ACTIVE（列表刷新）。

  延后原因：需要一个可重复种子化的 MySQL + Redis 后端（含带 `iam.admin.*` 的 profile
  与覆盖管理目标的 scope）；当前示例数据面向业务 Demo，尚未提供“控制台管理员”种子。

- 完整英文 i18n（架构预留在 vite 层面，本轮默认中文）。
- 部署示例（Nginx/反向代理/静态托管）见 `docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md`
  （本轮交付）。
