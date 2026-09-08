---
title: 从零接入 Muer
description: 手写宿主用户适配、权限声明、资源描述、授权接口与诊断闭环。
sidebar:
  order: 2
---

如果你只想先看到效果，请运行[10~15 分钟快速开始](/getting-started/quick-start/)。本页解释并实现 Quickstart 里的每个接入部件。

完整链路是：

```text
宿主用户模型
  → IdentityAuthenticator
  → IamPrincipal
  → PermissionDefinitionProvider
  → Template / Published Version / Profile / Scope
  → MvcResourceDescriptorResolver
  → ResourceHierarchyProvider
  → @RequirePermission
  → AuthorizationEngine / Diagnostics
```

完整成品位于 [`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)。下面的包名与代码结构和它一致。

## 1. 创建宿主应用

使用 Java 21、Spring Boot 4，并只添加 Muer Starter：

```xml
<dependency>
  <groupId>cloud.muer</groupId>
  <artifactId>muer-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Muer 使用宿主 `DataSource` 保存授权事实，使用 Redis 管理不透明 Token。最小配置：

```yaml
spring:
  datasource:
    url: ${MUER_JDBC_URL:jdbc:mysql://localhost:3306/iam_example}
    username: ${MUER_DB_USERNAME:iam}
    password: ${MUER_DB_PASSWORD:iam-secret}
  data.redis:
    host: ${MUER_REDIS_HOST:localhost}
    port: ${MUER_REDIS_PORT:6379}

muer:
  enabled: true
  schema.enabled: true
  token:
    ttl: 8h
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
  client-types: [WEB]
```

这些是 Starter 的公共配置。Quickstart 的 `muer.quickstart.seed-demo` 是示例自身属性，不属于 `MuerProperties`。

## 2. 宿主用户模型

Muer 不创建用户、不保存密码。先定义宿主自己的用户投影：

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

示例用内存 Map；真实系统应替换为已有用户服务：

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

`activeProfileId`、`templateVersionId` 必须引用有效授权投影。认证成功不等于 Muer 会替你创建 Profile。

## 3. 实现 IdentityAuthenticator

认证器只负责校验宿主凭据，并把成功结果投影为 `IamPrincipal`：

```java
@Configuration(proxyBeanMethods = false)
public class DemoIdentityAuthenticator {
    @Bean
    IdentityAuthenticator identityAuthenticator(DemoAccountService accounts) {
        return request -> accounts.findByUsername(request.username())
            .filter(account -> account.password().equals(request.password()))
            .map(account -> new IamPrincipal(
                account.id(),
                "identity-" + account.username(),
                account.identityDomain(),
                account.activeProfileId(),
                account.templateVersionId(),
                request.clientType(),
                account.authorizationVersion()));
    }
}
```

生产实现应使用安全密码校验，并且不把密码、原始 Token 或敏感设备信息写入 Principal。

## 4. 声明 Permission

Permission 是开发者维护的稳定业务能力代码：

```java
@Configuration(proxyBeanMethods = false)
public class MuerPermissionConfiguration {
    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
            new PermissionDefinition(
                "document:read", "Read a document", "Read a document inside a project"),
            new PermissionDefinition(
                "document:update", "Update a document", "Update a document inside a project"));
    }
}
```

应用启动时，Muer 注册这些定义。注册 Permission 不会创建 Template、Version、Profile 或 Scope，也不会直接给某个用户授权。

## 5. 理解管理投影

管理员按以下顺序组合开发者声明的 Permission：

```text
Permission
  → Permission Template
  → DRAFT Template Version（选择 Permission）
  → PUBLISHED Template Version（发布后不可变）
  → Authorization Profile（绑定 userId 与 Version）
  → Resource Scope
```

Quickstart 的 Reader 投影为：

```text
userId             101
profileId          401
templateVersionId  301
permission         document:read
scope              PROJECT / 101 / READ
```

本地学习可以开启示例 Seeder；受管环境使用 Admin Console / Management API。生产空库的第一个管理员必须使用[显式 Bootstrap Service](/management/bootstrap-first-admin/)，不能开启 Demo Seeder。

## 6. 创建业务资源

业务资源仍由宿主拥有：

```java
public record Document(String id, String projectId, String status) {}

public record DocumentUpdate(String status) {}
```

示例 `DocumentService` 保存两条数据：Document 1001 属于 Project 101，Document 2001 属于 Project 202。Muer 不查询这张业务表。

## 7. 把 HTTP 请求描述成资源

`MvcResourceDescriptorResolver` 把路径变量中的 Document ID 转换为 Muer 能理解的资源及父路径：

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
        Object value = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> variables)) return Optional.empty();
        Object id = variables.get("id");
        if (!(id instanceof String documentId)) return Optional.empty();

        return documents.find(documentId).map(document -> new ResourceDescriptor(
            "DOCUMENT",
            document.id(),
            List.of("PROJECT:" + document.projectId()),
            Map.of()));
    }
}
```

Resolver 只描述资源；是否落在 Scope 内由 hierarchy 决定。

## 8. 实现 ResourceHierarchyProvider

这个示例是两层结构 `PROJECT → DOCUMENT`：

```java
@Component
public class DocumentResourceHierarchyProvider
        implements ResourceHierarchyProvider {
    @Override
    public boolean isWithinScope(
            ResourceDescriptor resource, ResourceScope scope) {
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(
            scope.scopeType() + ":" + scope.scopeRefId());
    }
}
```

真实系统可以查询组织、租户、项目或目录关系，但必须返回确定性结果。不要用 Scope 绕过 Permission 检查。

## 9. 保护业务 Controller

读取需要 `document:read` 和读 Scope；更新还要求写 Scope：

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}")
    @RequirePermission("document:read")
    public ResponseEntity<Document> read(@PathVariable String id) {
        return documents.find(id).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}")
    @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
    public ResponseEntity<Document> update(
            @PathVariable String id, @RequestBody DocumentUpdate update) {
        return documents.find(id)
            .map(document -> ResponseEntity.ok(
                documents.update(document.id(), update.status())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

Muer 在方法体执行前完成授权，业务代码不需要手写角色判断。

## 10. 保护宿主业务路由

Starter 自动配置 `/iam/**` 安全链。宿主自己的 `/api/**` 需要显式加入 Bearer Filter：

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

## 11. 登录与验证

启动应用后：

1. `POST /iam/auth/login`，使用 `alice / demo-pass / WEB`；
2. 用返回 Token 调用 `GET /api/documents/1001`，预期 `200`；
3. 调用 `POST /api/documents/1001`，预期 `403`；
4. 调用 `GET /api/documents/2001`，预期 `403`。

完整 Bash 与 PowerShell 请求见 [Quick Start](/getting-started/quick-start/)。

## 12. Diagnostics

对 `POST /iam/authorization/diagnostics` 传入：

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

它只诊断当前 Principal，并返回 `allowed`、`decisionCode` 与每个决策步骤。不能用它读取其他用户的授权。

## 13. 从示例迁移到真实系统

替换边界，而不是复制演示数据：

- `DemoAccountService` → 你的用户目录；
- 明文示例密码 → 安全密码校验或既有身份源；
- 内存 `DocumentService` → 你的业务仓储；
- 示例 hierarchy → 你的租户/组织/资源关系；
- Quickstart Seeder → Admin Console、Management API 与显式 First Admin Bootstrap；
- 固定 Reader Profile → 宿主实际授权 Provisioning。

保留的公共接入点是 `IdentityAuthenticator`、`PermissionDefinitionProvider`、`ResourceHierarchyProvider`、`MvcResourceDescriptorResolver` 与 `@RequirePermission`。

接下来阅读[定义权限](/getting-started/define-permissions/)、[权限管理](/getting-started/permission-management/)和[生产环境检查清单](/operations/production-checklist/)。
