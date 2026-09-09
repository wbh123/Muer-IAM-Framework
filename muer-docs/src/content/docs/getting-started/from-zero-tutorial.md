---
title: 从零接入自己的 Spring Boot
description: 手写宿主用户适配、权限声明、资源描述、授权接口与诊断闭环——每一步都讲清为什么、值从哪来、执行后发生什么、怎么判断做对了。
sidebar:
  order: 2
---

如果你只想先看到效果，先跑一遍[10~15 分钟快速开始](/getting-started/quick-start/)。本页**解释并手写实现** Quickstart 里的每一个接入部件——它回答的不是“代码怎么写”，而是“为什么要写这一段、关键值从哪来、执行后 Muer 内部发生了什么、我怎么知道自己做对了”。

## 本页完成后你会得到什么

一个能跑通的宿主应用，它实现：

```text
宿主用户模型 DemoAccount
  → IdentityAuthenticator（登录翻译器）
  → IamPrincipal（Muer 认识的身份）
  → PermissionDefinitionProvider（声明 document:read / document:update）
  → Template / Published Version / Profile / Scope（管理员投影）
  → MvcResourceDescriptorResolver（把请求翻译成资源）
  → ResourceHierarchyProvider（决定资源落在哪个 Scope）
  → @RequirePermission（保护业务接口）
  → AuthorizationEngine / Diagnostics（统一决策与排障）
```

并且你**理解了每一层为什么存在**。完整成品见 [`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)，下面的包名与代码结构和它一致。

## 阶段里程碑：走到哪里可以停

本页较长，但**每一步都是一个可停下的成功点**。你可以按需停下，不必一次走完：

| 阶段 | 达到的标志 | 可以停在这里吗？ |
| --- | --- | --- |
| **阶段 1：登录已经跑通** | `POST /iam/auth/login` 返回 200 拿到 token；`GET /iam/auth/me` 能读到当前用户 | ✅ 如果项目目前只需要认证，可以在此停下 |
| **阶段 2：业务权限已经注册** | 应用启动后，`document:read` / `document:update` 等 permission 已被声明（见第 4 步） | 想先只接认证，可暂缓 |
| **阶段 3：用户授权完成** | Alice 有一个指向已发布模板版本的 Profile，登录后 principal 带出正确的 `activeProfileId` / `templateVersionId`（见第 5~6 步） | 想做「谁拥有什么」的细粒度授权时再继续 |
| **阶段 4：资源级授权完成** | `@RequirePermission` 接口能区分「项目内可读」与「跨项目 403」 | 需要 Scope / 资源级控制时再继续 |

> 建议最小路径：**阶段 1** → 跑通登录后停一下，确认基础链路没问题，再决定是否按 2→3→4 往上加。不要因为本页很长就被劝退——**先跑通登录，你已经完成了 80% 的接入工作**。

## 为什么需要一个“投影”而不直接存你的用户

Muer **不创建用户、不保存密码**。它只需要每当你的人登录时，知道“谁登录了、他按哪套授权身份进来”。所以你需要一个**翻译器**把你的用户翻译成 Muer 能理解的对象——见第 3 步。

---

## 1. 创建宿主应用

### 为什么只引一个 Starter

普通使用者**只消费一个依赖**：`muer-spring-boot-starter`。它会把 Muer 的认证、授权、Session、持久化、管理 API 等自动配置入口一并带进你的应用。

你不应该分别去依赖 `muer-core`、`muer-authorization`、`muer-persistence-mybatis`……那些是框架内部模块，直接依赖它们容易踩到自动配置顺序与版本不一致的问题。对外，你只面向 Starter 暴露的公共类型（见后面各节的 `IdentityAuthenticator`、`PermissionDefinitionProvider`、`@RequirePermission` 等）。

在 `pom.xml` 里加：

```xml
<dependency>
  <groupId>cloud.muer</groupId>
  <artifactId>muer-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

（示例使用 Java 21 与 Spring Boot 4。版本号由你的 BOM / Dependency Management 决定，这里仅为演示。）

### 配置前先理解它依赖什么

`application.yml` 里的三类配置各有明确职责：

```text
MySQL   → 保存 Permission / Template / Profile / Scope / Session 等持久授权状态
Redis   → 保存 opaque Token 的快速查找状态
muer.*  → 控制 Starter 自己的行为（是否启用、Token TTL、允许的客户端类型等）
```

MySQL 与 Redis 是运行必需（Muer 需要数据源做授权事实、需要 Redis 管理 Token）。**muer 配置没有这些连接信息**，它只描述 Starter 行为。最小配置：

```yaml
spring:
  datasource:                      # 让 Muer 能持久化授权事实的数据库连接
    url: ${MUER_JDBC_URL:jdbc:mysql://localhost:3306/iam_example}
    username: ${MUER_DB_USERNAME:iam}
    password: ${MUER_DB_PASSWORD:iam-secret}
  data:
    redis:                         # 让 Muer 能存放 opaque Token 的缓存连接
      host: ${MUER_REDIS_HOST:localhost}
      port: ${MUER_REDIS_PORT:6379}

muer:
  enabled: true                    # 打开 Starter 的主开关
  schema:
    enabled: true                  # 启动时让 Muer 用 Flyway 管理自己的表结构
    history-table: iam_flyway_schema_history
  token:
    ttl: 8h                        # 登录后 Token 的最长有效时间
    redis-prefix: iam              # Redis 物理 Key 前缀，只是存储命名空间
  session:
    enabled: true                  # 登录后创建可管理/可撤销的 Session
    touch-interval: 10m            # Session 活跃时间多久刷新一次
  client-types:
    - WEB                          # 允许哪些客户端类型参与登录
```

> 这些是 Starter 的**公共配置**。Quickstart 的 `muer.quickstart.seed-demo` 是示例自身属性，不属于 `MuerProperties`；生产环境不要开启（见第 13 节）。

关键行为注解：`schema.enabled` 决定 Muer 是否替你建表；`client-types` 是登录层的“允许名单”，凡是列表外类型都会在登录时被 Muer 拒绝（你在 IdentityAuthenticator 里**不需要**再重复判断，见第 3 步）。

### 执行后发生什么

应用启动时 Muer 会连接 MySQL/Redis，若 `schema.enabled` 则自动跑迁移建表，然后装配好登录与授权基础设施。**此时代码里还没有任何业务接入**，所以即使起来也还不能授权任何业务接口。

## 2. 宿主用户模型

### 先分清两种身份

登录要用到两类信息，别混在一起：

```text
认证身份（你是谁）
  username
  password

授权身份（你当前按哪套授权进入系统）
  activeProfileId        当前 Profile
  templateVersionId      Profile 引用的权限模板版本
  authorizationVersion   授权版本（旧 Token 失效控制）
```

Muer 登录需要**同时知道**“你是谁”和“你按哪套授权身份进来”。前者用于校验密码，后者用于之后每次请求该给你放行什么。`DemoAccount` 一个 record 同时携带两者，方便你的 IdentityAuthenticator 一次读全：

```text
Alice
  ├── 认证身份
  │   ├── username = "alice"
  │   └── password = "demo-pass"     // 仅演示；生产不许明文
  │
  └── 授权身份
      ├── activeProfileId      = 401
      ├── templateVersionId   = 301
      └── authorizationVersion = 1
```

定义你自己的投影 record（名字可随意）：

```java
public record DemoAccount(
        long id,
        String username,
        String password,
        String identityDomain,
        long activeProfileId,
        long templateVersionId,
        long authorizationVersion) {
}
```

示例用内存 Map 当作“用户表”；真实系统应替换为已有的用户服务 / LDAP / 企业用户中心：

```java
@Service
public class DemoAccountService {
    private static final Map<String, DemoAccount> ACCOUNTS = Map.of(
        "alice", new DemoAccount(
            101L, "alice", "demo-pass", "EXAMPLE", 401L, 301L, 1L));

    public Optional<DemoAccount> findByUsername(String username) {
        return Optional.ofNullable(ACCOUNTS.get(username));
    }
}
```

### 每个字段为什么存在

| 字段 | 来源 | 为什么需要 |
| --- | --- | --- |
| `id` | 宿主用户系统 | Muer 里稳定关联用户（= `IamPrincipal.userId`），> 0 |
| `username` | 宿主 | 登录标识 |
| `password` | Quick Start Demo | 仅演示；生产不应明文保存 |
| `identityDomain` | 宿主设计 | 区分身份域，如 `EXAMPLE`/`LDAP`；请求的 `domain` 必须与它匹配 |
| `activeProfileId` | Muer Provisioning 结果 | 当前授权 Profile |
| `templateVersionId` | Profile | 该 Profile 引用的权限模板版本 |
| `authorizationVersion` | 授权投影 | 授权变更时递增，可让旧 Token 失效 |

尤其注意：**Profile ID / Version ID 不是让你随便编的数字**。示例里固定 `401` / `301` 只是 Quick Start Seeder 预先建好的演示数据的 ID。真实环境里这两个值来自管理员通过 Console / Management API / Bootstrap 建好的 Profile，你要做的是把它**读出来填进投影**，而不是现场发明一个。

> `activeProfileId` / `templateVersionId` 必须指向真实存在的授权投影。**认证成功不等于 Muer 会替你创建 Profile**——Profile 是管理侧先行预置的，登录只负责引用。

## 3. 实现 IdentityAuthenticator

### 为什么需要这个“翻译器”

你的 `DemoAccountService` 知道 Alice 密码对不对，**Muer 不知道你的用户表长什么样**。所以要有一个翻译器，在“登录请求”和“Muer 能理解的身份”之间架桥：

```text
POST /iam/auth/login
    ↓
Muer 调用 IdentityAuthenticator
    ↓
宿主校验 Alice（DemoAccountService）
    ↓
返回 IamPrincipal
    ↓
Muer
```

IdentityAuthenticator **不负责**：创建用户、创建 Profile、改权限、生成 Token。它只做一件事：把“校验通过”翻译成 `IamPrincipal`。

```java
@Configuration(proxyBeanMethods = false)
public class DemoIdentityAuthenticator {
    @Bean
    IdentityAuthenticator identityAuthenticator(DemoAccountService accounts) {
        return request -> accounts.findByUsername(request.username())
            .filter(account -> account.password().equals(request.password()))
            .map(account -> new IamPrincipal(
                account.id(),
                // 宿主用户稳定 ID。不要用数据库临时索引或显示昵称代替。
                "identity-" + account.username(),
                // 该身份在宿主体系中的外部标识（账号名/key）。仅演示前缀；真实系统用你的业务唯一键。

                account.identityDomain(),
                // 身份域，如 EXAMPLE / LDAP。授权请求里的 domain 必须与它匹配。

                account.activeProfileId(),
                // 当前授权 Profile，必须已由 Provisioning/Seeder/Bootstrap 建好。

                account.templateVersionId(),
                // 当前 Profile 引用的已发布 Permission Template Version。

                request.clientType(),
                // 直接采用登录请求里的客户端类型。Muer 会按 muer.client-types 统一校验，
                // 这里不要复制相同的允许名单判断，避免两处规则漂移。

                account.authorizationVersion()
                // 授权变更时递增，可让按旧授权签发的 Token 失效。
            ));
    }
}
```

生产实现应使用安全密码校验，并且不要把密码、原始 Token 或敏感设备信息写进 `IamPrincipal`。

### 执行后发生什么

```text
POST /iam/auth/login
    ↓
Muer 调用你的 IdentityAuthenticator
    ↓
宿主校验 Alice（DemoAccountService）
    ↓
返回 IamPrincipal（成功）或 Optional.empty()（失败）
    ↓
Muer 检查 clientType 是否在 muer.client-types 允许名单
    ↓
生成 opaque Token，创建 Session
    ↓
客户端拿到 accessToken
```

校验失败返回 `Optional.empty()` → HTTP 401，且**不创建 Session**。

## 4. 声明 Permission

### 为什么由代码声明

`document:read` 是**应用代码本身的一项能力**。如果管理员能在后台随手造 `document:super-read`，但代码里从没用过，它就没意义。所以能力目录由开发者用代码声明，管理员只负责把已声明的能力**组合进模板并分发给用户**：

```text
开发者   → 定义系统有什么能力（PermissionDefinitionProvider）
管理员   → 决定谁获得哪些能力（Template / Profile / Scope）
```

因此 `PermissionDefinitionProvider` **≠ 给用户授权**。它只注册能力目录。

创建 `security/MuerPermissionConfiguration.java`：

```java
@Configuration(proxyBeanMethods = false)
public class MuerPermissionConfiguration {
    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
            new PermissionDefinition(
                "document:read",   // 稳定机器代码（资源:动作），业务与接口共用
                "Read a document", // 管理界面显示名称
                "Read a document inside a project"), // 给管理员看的说明
            new PermissionDefinition(
                "document:update",
                "Update a document",
                "Update a document inside a project"));
    }
}
```

字段以真实 API 为准：`PermissionDefinition(code, displayName, description)`。

### 执行后发生什么

应用启动时 Muer 把这些定义注册进 **Permission Registry**（可在 Console 的 Permissions 页看到，只读）。**注册不会**创建 Template、Version、Profile 或 Scope，也**不会**直接给任何用户放行——那属于第 5 步的管理员投影。

## 5. 理解管理投影（用 Alice 串起来）

Permission 只是“能力名”。要让 Alice 真的有权限，管理员要把它们投影成一条授权。完整投影链路是：

```text
Permission（document:read）
  → Permission Template（Document Reader）
  → DRAFT Template Version（勾选 Permission）
  → PUBLISHED Template Version（发布后不可变）
  → Authorization Profile（绑定 userId=101 与 Version）
  → Resource Scope（PROJECT / 101 / READ）
```

Quickstart 预置的 Reader 投影落到具体值：

```text
userId             101
profileId          401
templateVersionId  301
permission         document:read
scope              PROJECT / 101 / READ
```

各层的意义（为什么 Template / Version / Profile / Scope 层层叠叠）见[权限管理](/getting-started/permission-management/)。这里先记住结论：**Profile 绑定了一个 PUBLISHED 的模板版本 + 一组 Scope**；第 2 步的 `DemoAccount` 里那两个数字 `401/301`，指的就是这条投影。

### 本地学习 vs 生产

- 本地学习可开启 Quick Start Seeder，让它自动建好这条 Reader 投影（省去手工点按）；
- 受管环境使用 Admin Console / Management API；
- **生产空库的第一个管理员**必须用显式 [Bootstrap Service](/management/bootstrap-first-admin/)，**不能开启** Demo Seeder。

## 6. 创建业务资源

业务资源（Document）仍归宿主所有，Muer 不维护：

```java
public record Document(String id, String projectId, String status) {}

public record DocumentUpdate(String status) {}
```

示例 `DocumentService` 里预置两条：Document 1001 属于 Project 101，Document 2001 属于 Project 202。**Muer 从不查询这张业务表**——它只消费 Resolver 交给它的 `ResourceDescriptor`（见下一步）。

## 7. 把 HTTP 请求描述成资源

### 为什么需要 Resolver

引擎收到 `GET /api/documents/1001`，**不知道** 1001 属于哪个 Project。它需要有人告诉它“这个请求在访问哪片资源、挂在哪个父节点下”。这个翻译就是 `MvcResourceDescriptorResolver`。

创建 `DocumentResourceResolver`：

```java
@Component
public class DocumentResourceResolver implements MvcResourceDescriptorResolver {
    private final DocumentService documents;

    public DocumentResourceResolver(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(
            HttpServletRequest request, HandlerMethod handlerMethod) {
        // ① 从 Spring MVC 请求属性里读出路径模板变量 {id}
        Object value = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> variables)) return Optional.empty();
        Object id = variables.get("id");
        if (!(id instanceof String documentId)) return Optional.empty();

        // ② 拿到 documentId 后查询宿主业务数据，找到它属于哪个 Project
        return documents.find(documentId).map(document -> new ResourceDescriptor(
            // ③ 构造 Muer 能理解的资源：类型 + ID + 父层级链
            "DOCUMENT",                                  // resourceType
            document.id(),                               // resourceId
            List.of("PROJECT:" + document.projectId()),  // parentPath：挂到哪个父节点
            Map.of()));                                  // attributes（可选附加信息）
    }
}
```

要传达的核心：

```text
HTTP Request  GET /api/documents/1001
    ↓
DocumentService.find("1001")
    ↓
Document(projectId=101)
    ↓
ResourceDescriptor("DOCUMENT", "1001", parentPath=["PROJECT:101"])
```

**Muer 不会自动查询 Document 数据库。** Resolver 是宿主告诉 Muer“这个 HTTP 请求正在访问什么业务资源”。Resolver 只负责**描述**资源；资源是否落在某个 Scope 内，由 hierarchy 决定（下一步）。

## 8. 实现 ResourceHierarchyProvider

### 为什么需要它

有了“资源 = DOCUMENT/1001，父 = PROJECT:101”，还需要回答：Alice 的 Scope `PROJECT/101/READ` 包不包含这个资源？这个“层级包含”判断由宿主实现，因为只有宿主清楚自己资源的树状关系。

示例是两层 `PROJECT → DOCUMENT`：

```java
@Component
public class DocumentResourceHierarchyProvider
        implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(
            ResourceDescriptor resource, ResourceScope scope) {
        // 资源本身就是 Scope 指向的节点（如 Scope 直接是 DOCUMENT/1001）
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        // 否则看资源是否挂在 Scope 所指父节点下（parentPath 含 "PROJECT:101"）
        return resource.parentPath().contains(
            scope.scopeType() + ":" + scope.scopeRefId());
    }
}
```

真实系统可以查询组织、租户、项目或目录关系，但**必须返回确定性结果**。不要用宽 Scope 绕过 Permission 检查——能力与范围是 AND 关系。

> 关于 `parentPath` 的更多案例，以及“为什么 PROJECT Scope 能盖住 DOCUMENT 资源”，见[资源作用域](/authorization/resource-scope/)。

## 9. 保护业务 Controller

现在用 `@RequirePermission` 声明每个接口需要的能力与访问方式。真实决策仍由 `AuthorizationEngine` 统一完成，业务代码不需要手写角色判断。

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}")
    @RequirePermission("document:read")   // 读：需要 document:read + 读 Scope
    public ResponseEntity<Document> read(@PathVariable String id) {
        return documents.find(id).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}")
    @RequirePermission(value = "document:update", access = ScopeAccess.WRITE) // 写：还需要写 Scope
    public ResponseEntity<Document> update(
            @PathVariable String id, @RequestBody DocumentUpdate update) {
        return documents.find(id)
            .map(document -> ResponseEntity.ok(
                documents.update(document.id(), update.status())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

`@RequirePermission` **不只是检查 Permission**：它声明的 `value` + `access` 会交给引擎，连同 Resolver 解析出的资源一起，做 **Permission + Resource + Scope** 的联合判断（见第 11 步验证，能直观看到差异）。

## 10. 保护宿主业务路由

Starter 自动配置 `/iam/**` 安全链（登录、me、Session 等）。宿主自己的 `/api/**` 需要显式加一个 Bearer Filter，让它能读 Token：

```java
@Bean
@Order(100)
SecurityFilterChain apiSecurityFilterChain(
        HttpSecurity http, IamBearerTokenFilter bearerFilter) throws Exception {
    return http
        .securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(
            SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
            (request, response, failure) -> response.sendError(401)))
        .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class)
        .build();
}
```

前端隐藏按钮不是安全边界；服务端始终重新鉴权。

## 11. 登录与验证（完成检查）

启动应用后，按下面顺序验证——每一步都对应一个**能解释的原因**：

| 步骤 | 操作 | 预期 | 说明 |
| --- | --- | --- | --- |
| 1 | `POST /iam/auth/login`，`alice / demo-pass / WEB` | 200 + token | 登录成功，创建 Session |
| 2 | 用 token 调 `GET /api/documents/1001` | 200 | `document:read` ✅ 且 `PROJECT/101/READ` ✅ |
| 3 | 调 `POST /api/documents/1001` | 403 | Alice Reader 没有 `document:update` |
| 4 | 调 `GET /api/documents/2001` | 403 | `document:read` 有，但 2001 属于 PROJECT 202，超出 Scope |

> 第 3、4 步都是 403，但**原因不同**：第 3 步缺 Permission，第 4 步超 Scope。用第 12 步的 Diagnostics 就能区分（`PERMISSION_DENIED` vs `SCOPE_DENIED`）。

完整 Bash 与 PowerShell 请求见 [Quick Start](/getting-started/quick-start/)。

## 12. Diagnostics

对 `POST /iam/authorization/diagnostics` 传入下面请求，它会告诉你**当前登录用户**对某个资源为何放行/拒绝：

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

它只诊断当前 Principal，返回 `allowed`、`decisionCode` 与每个决策步骤（例如先过 `IDENTITY_DOMAIN`、`CLIENT_TYPE`、`ACTIVE_PROFILE`，再到 `ATOMIC_PERMISSION` 失败）。**不能用它读取其他用户的授权。**

## 13. 从示例迁移到真实系统

替换边界，而不是复制演示数据：

- `DemoAccountService` → 你的用户目录 / LDAP / 企业用户中心；
- 明文示例密码 → 安全密码校验或既有身份源；
- 内存 `DocumentService` → 你的业务仓储；
- 示例 hierarchy → 你的租户 / 组织 / 资源关系；
- Quick Start Seeder → Admin Console、Management API 与显式 First Admin Bootstrap（生产不开 Seeder）；
- 固定 Reader Profile → 宿主实际授权 Provisioning。

保留的公共接入点是 `IdentityAuthenticator`、`PermissionDefinitionProvider`、`ResourceHierarchyProvider`、`MvcResourceDescriptorResolver` 与 `@RequirePermission`。

## 下一步

- [权限管理](/getting-started/permission-management/)——把第 5 步的管理投影做成完整操作链路。
- [资源作用域](/authorization/resource-scope/)——第 8 步 Scope / parentPath 的深入理解。
- [定义权限](/getting-started/define-permissions/)与[生产环境检查清单](/operations/production-checklist/)。
