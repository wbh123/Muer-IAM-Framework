---
title: 15 分钟快速开始
description: 从零创建一个 Spring Boot 应用，接上 Muer，做一个受权限保护的 Document 业务接口，并跑通 200 / 403 与授权诊断。
sidebar:
  order: 1
---

这篇教程不是“能力总览”，而是一套你可以从头照做的**完整业务案例**。它和仓库里的可运行工程
[`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)
一一对应：文档里写的每一段代码，就是这个工程里的真实代码。照着做大约需要 15 分钟，前提是你本机已经装好 Java 21、Maven 和 Docker（Docker 只用于可选地拉起本地 MySQL / Redis）。

## 0. 你最终会得到什么

先看终点，你才知道每一步在为什么而做。学完这篇，你会得到：

```text
应用 Document System（一次真实接入）
│
└── 用户 alice (demo-pass)
    ├── Permission      document:read
    └── Scope           PROJECT / 101 / READ
```

然后能验证出下面一组**真实授权结果**：

| 请求 | alice（Reader）预期 | 切换 Editor 后预期 |
| --- | --- | --- |
| `GET    /api/documents/1001` | ✅ `200` | ✅ `200` |
| `POST   /api/documents/1001` | ❌ `403` | ✅ `200` |
| `GET    /api/documents/2001` | ❌ `403` | ❌ `403` |

为什么会这样？

- `1001` 属于 Project `101`，`alice` 的 Scope 恰好覆盖 Project 101 的读 → 能读；
- `alice` 只声明了 `document:read`，没有 `document:update` → 不能改 1001；
- `2001` 属于 Project `202`，不在 `alice` 的 Scope 内 → 即使有 `document:read` 也读不到。

文档中的每一步，都是为了让你能亲手得到这一张表。

---

## 1. 准备 Spring Boot 项目

最小要求：

- **Java 21**
- **Spring Boot 4**（Muer 0.1.0 针对它验证）
- **Maven**
- **Spring Web**（由 Muer Starter 传递引入，不必手动加）
- **MySQL / Redis**（见第 4 步）

从 [start.spring.io](https://start.spring.io/) 创建一个空项目，或手动建一个 Maven 工程。包名建议：

```text
com.example.muerquickstart
```

> 下面所有 Java 文件都放在这个包下。

:::note[我想直接看成品]
如果你不想手工建工程，直接打开
[`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)
——它就是这篇教程的完整可运行实现。
:::

**完成检查**

- [ ] 本机 `java -version` 输出 21；
- [ ] `mvn -v` 可用；
- [ ] 能创建一个空 Spring Boot 工程，主类放在 `com.example.muerquickstart`。

---

## 2. 安装当前 Muer Release Candidate

Muer 0.1.0 当前是 `0.1.0-SNAPSHOT`，**尚未发布到 Maven Central**。要本地使用，先从 GitHub 把框架安装到本机 Maven 仓库：

```bash
git clone https://github.com/wbh123/Muer-IAM-Framework.git
cd Muer-IAM-Framework
mvn clean install -DskipTests
```

这条命令会把下面的坐标安装到 `~/.m2/repository/cloud/muer/`：

```text
cloud.muer:muer-spring-boot-starter:0.1.0-SNAPSHOT
```

> 正式 0.1.0 发布到 Maven Central 之后，这一步会被删除，改成直接拉依赖即可。见[安装](/getting-started/installation/)。

**完成检查**

- [ ] 目录 `~/.m2/repository/cloud/muer/muer-spring-boot-starter/0.1.0-SNAPSHOT/` 存在。

---

## 3. 添加 Starter

在空工程的 `pom.xml` 里，添加唯一的 Muer 依赖。你不需要再手动加 Spring Web、JDBC、Redis 或 MySQL 驱动，这些都由 Starter 传递引入。

```xml
<dependency>
    <groupId>cloud.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

> 业务应用通常只依赖这一个 Starter。不要直接依赖 Muer 的 Mapper、Repository 实现或 `internal` 包。

**完成检查**

- [ ] `mvn -q compile` 通过，说明坐标已能解析。

---

## 4. 启动 MySQL 与 Redis

Muer 用宿主应用的 `DataSource` 保存 Session、Profile、Template、Scope 与审计数据，用 Redis 做不透明 Token 的快速索引。

**已经有 MySQL / Redis？** 直接填连接信息即可，跳到第 5 步。下面的 Docker Compose 只是**可选工具**，不是主流程。

**没有基础设施？** 使用 `examples/quickstart/docker-compose.yml`：

```bash
cd examples/quickstart
docker compose up -d
```

它会起 MySQL 8.4（库名 `iam_example`、用户 `iam`、密码 `iam-secret`）和 Redis 7，都监听 `localhost`。

**完成检查**

- [ ] `docker compose ps` 里 mysql、redis 都是 healthy；
- [ ] 已有基础设施时，你能提供 JDBC URL、用户名、密码和 Redis 地址。

---

## 5. 配置 application.yml

现在创建：

```text
src/main/resources/application.yml
```

下面是一份**完整可复制**的配置。所有 `muer.*` 键都来自真实的 `MuerProperties`，没有杜撰：

```yaml
spring:
  datasource:
    url: ${MUER_JDBC_URL:jdbc:mysql://localhost:3306/iam_example}
    username: ${MUER_DB_USERNAME:iam}
    password: ${MUER_DB_PASSWORD:iam-secret}
  data:
    redis:
      host: ${MUER_REDIS_HOST:localhost}
      port: ${MUER_REDIS_PORT:6379}

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
  quickstart:
    seed-demo: ${MUER_QUICKSTART_SEED_DEMO:false}
```

要点：

- 首次启动且 `muer.schema.enabled=true` 时，Starter 会**自动执行 Flyway migration 创建 IAM 表**，一般不需要手工建表；
- `muer.client-types` 由 Muer 统一检查，`IdentityAuthenticator` 不用重复维护白名单；
- `muer.quickstart.seed-demo` 是**仅限本地演示**的授权数据开关，先保持默认关，后面第 13 步再打开。

**完成检查**

- [ ] `application.yml` 已创建；
- [ ] MySQL / Redis 地址与你本地一致；
- [ ] `muer.client-types` 包含 `WEB`。

---

## 6. 创建演示用户服务

Muer **不拥有你的用户**。你先给自己一个账号来源。为避免引入用户表设计，教程用一个内存 `Map`。

现在创建：

```text
src/main/java/com/example/muerquickstart/account/DemoAccount.java
```

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

再创建：

```text
src/main/java/com/example/muerquickstart/account/DemoAccountService.java
```

```java
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;

@Service
public class DemoAccountService {

    private static final Map<String, DemoAccount> ACCOUNTS = Map.of(
            "alice", new DemoAccount(101L, "alice", "demo-pass",
                    "EXAMPLE", 401L, 301L, 1L));

    public Optional<DemoAccount> findByUsername(String username) {
        return Optional.ofNullable(ACCOUNTS.get(username));
    }
}
```

:::note[我有真实用户体系怎么办？]
到真实系统里，你只需要把 `DemoAccountService` 换成你自己的 `UserService` / LDAP / 企业用户中心，**Muer 其余代码一行都不用改**。接入心智是：Muer 只认你验证完凭据后返回的那个“授权投影”。
:::

> `401` / `301` 这两个 ID 不是魔法数字，它们在第 13 步创建的 Profile 与 Template Version 一致。现在先记住：alice 登录时默认落到 Reader Profile（401）。

**完成检查**

- [ ] `DemoAccountService` 存在并能被 Spring 管理；
- [ ] 账号 `alice` / `demo-pass` 已在内。

---

## 7. 接入 IdentityAuthenticator

`IdentityAuthenticator` 是 Muer 问“谁在登录”的唯一入口。你校验凭据，然后投影一个 `IamPrincipal`。

现在创建：

```text
src/main/java/com/example/muerquickstart/account/DemoIdentityAuthenticator.java
```

```java
import cloud.muer.authentication.IdentityAuthenticator;
import cloud.muer.authentication.LoginRequest;
import cloud.muer.core.model.IamPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoIdentityAuthenticator {

    @Bean
    IdentityAuthenticator demoIdentityAuthenticator(DemoAccountService accounts) {
        return (LoginRequest request) -> accounts
                .findByUsername(request.username())
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

关键点：

- `IdentityAuthenticator` **只负责读取当前授权投影**，不会在登录时创建 Profile；
- `IamPrincipal` 里的 `activeProfileId` / `templateVersionId` 指明“这个用户现在落在哪个 Profile 上”——这两行必须能对上数据库里的授权数据（第 13 步创建）；
- 登录失败的账号返回 `Optional.empty()`，Muer 返回 `401`。

**完成检查**

- [ ] `DemoIdentityAuthenticator` 已注册 `IdentityAuthenticator` Bean；
- [ ] 能理解“登录返回 alice 的 Reader 投影”。

---

## 8. 声明业务 Permission

**开发者定义系统有哪些能力；管理员决定谁获得这些能力。** 权限在这里用代码声明，不和“谁”绑定。

现在创建：

```text
src/main/java/com/example/muerquickstart/security/MuerPermissionConfiguration.java
```

```java
import cloud.muer.authorization.PermissionDefinition;
import cloud.muer.authorization.PermissionDefinitionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class MuerPermissionConfiguration {

    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
                new PermissionDefinition("document:read", "Read a document",
                        "Read a document inside a project"),
                new PermissionDefinition("document:update", "Update a document",
                        "Update a document inside a project"));
    }
}
```

应用就绪后，Muer 会把这两个 Permission **幂等注册**进数据库，并在管理界面看到。命名规则见[定义权限](/getting-started/define-permissions/)。

> Permission 一旦声明，不是靠 SQL 手工初始化；`PermissionDefinitionProvider` 只管“注册能力”，它**不会**自动创建 Template / Template Version / Profile / Scope。

**完成检查**

- [ ] `MuerPermissionConfiguration` 已注册；
- [ ] 明白“Permission 由代码声明、谁拥有由管理员配置”的分工。

---

## 9. 创建 Document 业务资源

教程用最小业务模型：文档属于某个 Project。

现在创建：

```text
src/main/java/com/example/muerquickstart/document/Document.java
```

```java
public record Document(String id, String projectId, String status) {
}
```

```text
src/main/java/com/example/muerquickstart/document/DocumentService.java
```

```java
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {

    private final ConcurrentHashMap<String, Document> documents = new ConcurrentHashMap<>();

    public DocumentService() {
        documents.put("1001", new Document("1001", "101", "DRAFT"));
        documents.put("2001", new Document("2001", "202", "DRAFT"));
    }

    public Optional<Document> find(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    public Document update(String id, String status) {
        var current = documents.get(id);
        if (current == null) throw new IllegalArgumentException("Document not found: " + id);
        var updated = new Document(current.id(), current.projectId(), status);
        documents.put(id, updated);
        return updated;
    }
}
```

数据关系（记住它，后面 Scope 就靠它）：

```text
Document 1001 → Project 101
Document 2001 → Project 202
```

**完成检查**

- [ ] 两个 Document 的 projectId 与上面一致。

---

## 10. 描述 Resource Hierarchy

Muer 需要知道“一个业务资源落在哪个 Scope 内”，由 `ResourceHierarchyProvider` 回答。教程的层级很简单：资源是它自己，或它的 `parentPath` 含 `PROJECT:<id>`。

现在创建：

```text
src/main/java/com/example/muerquickstart/security/DocumentResourceHierarchyProvider.java
```

```java
import cloud.muer.core.model.ResourceDescriptor;
import cloud.muer.core.model.ResourceScope;
import cloud.muer.core.port.ResourceHierarchyProvider;
import org.springframework.stereotype.Component;

@Component
public class DocumentResourceHierarchyProvider implements ResourceHierarchyProvider {

    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        return resource.parentPath().contains(scope.scopeType() + ":" + scope.scopeRefId());
    }
}
```

逻辑落点：

- Document `1001` 的 `parentPath` = `PROJECT:101` → Scope `PROJECT/101/READ` **命中** → 允许；
- Document `2001` 的 `parentPath` = `PROJECT:202` → Scope `PROJECT/101/READ` **不命中** → 拒绝。

**完成检查**

- [ ] 能说明“为什么 GET 2001 会被拒”：Scope 只覆盖 Project 101。

---

## 11. 保护业务接口

先给接口加权限注解，再写一个 `ResourceResolver` 告诉 Muer“这个请求对应哪个资源”。

现在创建：

```text
src/main/java/com/example/muerquickstart/document/DocumentUpdate.java
```

```java
public record DocumentUpdate(String status) {
}
```

```text
src/main/java/com/example/muerquickstart/document/DocumentResourceResolver.java
```

```java
import cloud.muer.autoconfigure.web.MvcResourceDescriptorResolver;
import cloud.muer.core.model.ResourceDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DocumentResourceResolver implements MvcResourceDescriptorResolver {

    private final DocumentService documents;

    public DocumentResourceResolver(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)) return Optional.empty();
        Object id = pathVariables.get("id");
        if (!(id instanceof String documentId)) return Optional.empty();
        return documents.find(documentId).map(document -> new ResourceDescriptor(
                "DOCUMENT", document.id(),
                List.of("PROJECT:" + document.projectId()),
                Map.of()));
    }
}
```

> Muer 不查你的 Document / Project 表。宿主告诉它“这个资源是什么、属于哪里”，它再判断“当前 Principal 能不能访问”。

现在创建受保护接口：

```text
src/main/java/com/example/muerquickstart/document/DocumentController.java
```

```java
import cloud.muer.autoconfigure.web.RequirePermission;
import cloud.muer.core.model.ScopeAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}")
    @RequirePermission("document:read")
    public ResponseEntity<Document> read(@PathVariable("id") String id) {
        return documents.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}")
    @RequirePermission(value = "document:update", access = ScopeAccess.WRITE)
    public ResponseEntity<Document> update(@PathVariable("id") String id, @RequestBody DocumentUpdate update) {
        return documents.find(id)
                .map(document -> ResponseEntity.ok(documents.update(document.id(), update.status())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

`/api/**` 需要一条 Spring Security 过滤链把 Bearer Token 解析成认证。Starter 已自动为你保护 `/iam/**`，这里只管你自己的业务路径。创建：

```text
src/main/java/com/example/muerquickstart/security/QuickStartSecurityConfiguration.java
```

```java
import cloud.muer.autoconfigure.IamBearerTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class QuickStartSecurityConfiguration {

    @Bean
    @Order(100)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, IamBearerTokenFilter bearerFilter)
            throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, failure) -> response.sendError(401)))
                .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
```

**完成检查**

- [ ] `GET` 带 `@RequirePermission("document:read")`；
- [ ] `POST` 带 `document:update` 且 `access = WRITE`（这样 Editor 才够格）；
- [ ] 明白“只有同时满足 Permission 与 Scope 才会放行”。

---

## 12. 启动应用

配置好本地开发，运行主类：

```text
src/main/java/com/example/muerquickstart/QuickStartApplication.java
```

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuickStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuickStartApplication.class, args);
    }
}
```

首次启动且开着 `muer.schema.enabled=true`，确认日志里：

- MySQL 连接成功、Flyway migration 成功；
- Redis 连接成功；
- Permission（`document:read`、`document:update`）注册完成；
- 应用启动成功。

**完成检查**

- [ ] 应用能正常启动；
- [ ] `GET http://localhost:8080/iam/auth/me` 未带 Token 时返回 `401`（说明 `/iam/**` 已被 Starter 保护）。

---

## 13. 为 Alice 准备 Profile / Template / Scope

现在到了最容易卡住的地方。**光声明 Permission 还不够**——Muer 还需要“Template（含版本）+ Profile + Scope”这三类**授权行**，才能判断 alice 有没有权限。

- `PermissionDefinitionProvider` 只负责注册 Permission；
- **Template / Template Version / Profile / Scope 由管理员配置**，不会自动生成。

教程提供两条路：

### 路线 A：用 Admin Console（推荐理解管理流程）

参见[第一次使用 Muer Admin Console](/management/first-admin-tutorial/)。请留意：当前 0.1.0 Console 能**编辑**已有 DRAFT 版本的权限、编辑已有 Profile 的属性和 Resource Scope，但**不能**从零新建 Template / 发布 Version / 新建 Profile。若要从空库搭出 Reader/Editor，先用 QuickStart Seeder 或 Management API 建好骨架，再用 Console 做后续调整与查看。

### 路线 B：用教程专用 Seeder（仅本地 dev）

为让“复制就能跑”，`examples/quickstart` 带了一个**只用于教程环境**的初始化器。它只在 `dev` profile 且 `muer.quickstart.seed-demo=true` 时启用，会重置并写入下面的演示授权行：

```text
Template 201  Document Reader      Version 301 = [document:read]
Template 202  Document Editor      Version 302 = [document:read, document:update]

Profile 401  Alice Reader  → Version 301, Scope PROJECT/101/READ
Profile 402  Alice Editor  → Version 302, Scope PROJECT/101/READ + WRITE
```

> 401 / 402 / 301 / 302 这些 ID **只属于 Quick Start 演示数据**。生产环境由 Muer 管理接口 / 数据库生成，不要硬编码。

以 `dev` profile + 开关启动：

```bash
SPRING_PROFILES_ACTIVE=dev MUER_QUICKSTART_SEED_DEMO=true \
  mvn -f examples/quickstart/pom.xml spring-boot:run
```

> **生产不要用 Seeder。** 在受管环境里用 Admin Console 或 Management API 完成同样的配置。

**完成检查**

- [ ] 你能说清 Reader（401）与 Editor（402）的差异；
- [ ] 明白“Permission 注册 ≠ 授权生效”，还需要 Template/Profile/Scope。

---

## 14. 登录

用 Postman、Apifox 或 IDE HTTP Client。**接口**：

```http
POST http://localhost:8080/iam/auth/login
Content-Type: application/json
```

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

**预期成功响应**（字段与 OpenAPI 一致）：

```json
{
  "accessToken": "<opaque-token>",
  "sessionId": "<session-id>",
  "expiresAt": "2026-01-01T08:00:00.000+00:00",
  "principal": {
    "userId": 101,
    "identityId": "identity-alice",
    "identityDomain": "EXAMPLE",
    "activeProfileId": 401,
    "templateVersionId": 301,
    "clientType": "WEB",
    "authorizationVersion": 1
  }
}
```

记下 `accessToken`，后续请求都带：

```http
Authorization: Bearer <accessToken>
```

**完成检查**

- [ ] 登录返回 `200`，能看到 `accessToken`。

---

## 15. 验证第一个 200

读取当前用户：

```http
GET http://localhost:8080/iam/auth/me
Authorization: Bearer <accessToken>
```

预期 `200` 且返回你在登录里看到的 `principal`。

然后读你的文档：

```http
GET http://localhost:8080/api/documents/1001
Authorization: Bearer <accessToken>
```

**预期**：`200`。为什么？alice 有 `document:read`，且 Document 1001 落在其 Scope `PROJECT/101/READ` 内。

**完成检查**

- [ ] `/iam/auth/me` 返回 `200`；
- [ ] `GET /api/documents/1001` 返回 `200`。

---

## 16. 验证第一个 403

试着更新文档：

```http
POST http://localhost:8080/api/documents/1001
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{ "status": "PUBLISHED" }
```

**预期**：`403`。因为 alice（Reader）没有 `document:update`。

再读另一份文档：

```http
GET http://localhost:8080/api/documents/2001
Authorization: Bearer <accessToken>
```

**预期**：`403`。因为 `2001` 属于 Project `202`，不在 alice 的 Scope 内——即使她有 `document:read`。

**完成检查**

- [ ] `POST 1001` → `403`；
- [ ] `GET 2001` → `403`，且你能解释这是 Scope 拒绝，不是 Permission 拒绝。

---

## 17. 使用 Diagnostics 分析 403

403 只告诉你“被拒”，不告诉你“为什么”。用 Diagnostics 问清楚。

```http
POST http://localhost:8080/iam/authorization/diagnostics
Authorization: Bearer <accessToken>
Content-Type: application/json
```

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

**预期**：`200`，返回真实 `AuthorizationDecision`。Reader 缺少更新权限时大致是：

```json
{
  "allowed": false,
  "decisionCode": "PERMISSION_DENIED",
  "steps": [
    { "code": "IDENTITY_DOMAIN", "passed": true, "reason": "..." },
    { "code": "CLIENT_TYPE", "passed": true, "reason": "..." },
    { "code": "ACTIVE_PROFILE", "passed": true, "reason": "..." },
    { "code": "ATOMIC_PERMISSION", "passed": false, "reason": "permission missing" }
  ]
}
```

> Diagnostics 只诊断**当前已认证的 Principal**，不能指定别人。`decisionCode` 取值见[错误码](/reference/error-codes/)。

**完成检查**

- [ ] Diagnostics 能解释上面的 `POST 1001 → 403` 是缺 `document:update`。

---

## 18. 切换 Profile

现在把 alice 切到 Editor，验证 `POST 1001 → 200`。

```http
POST http://localhost:8080/iam/authorization/profiles/402/switch
Authorization: Bearer <accessToken>
```

`402` 是 Editor Profile。**预期**：`200`，返回新的 Token + Session（principal 的 `activeProfileId` 变为 402、`templateVersionId` 变为 302）。用**新返回的 Token**：

```http
POST http://localhost:8080/api/documents/1001
Authorization: Bearer <新token>
Content-Type: application/json
```

```json
{ "status": "PUBLISHED" }
```

**预期**：`200`。而原来的 Reader Token 仍然不能更新 1001——Profile Switch 不会改旧 Token。

**完成检查**

- [ ] 切到 Editor 后 `POST 1001 → 200`。

---

## 19. 撤销 Session

用 `/iam/auth/me` 或登录返回拿到 `sessionId`：

```http
POST http://localhost:8080/iam/sessions/{sessionId}/revoke
Authorization: Bearer <accessToken>
```

**预期**：`204`。之后该 Session 对应的 Token 访问受保护接口返回 `401`，同一用户的其他独立 Session 不受影响。

**完成检查**

- [ ] 撤销后带旧 Token 访问 `/iam/auth/me` 返回 `401`。

---

## 20. 查看 Runtime Health

Muer 会条件化贡献运行状态；启用 Spring Boot Actuator 即可看到 `muer` 健康与指标（见[可观测性](/operations/observability/)）：

```http
GET http://localhost:8080/actuator/health
```

预期包含 `muer` 条目，表示 Muer 自动配置可用；MySQL / Redis 健康由 Spring Boot 自带的 DataSource / Redis Health 负责。

**完成检查**

- [ ]（可选）能看到 `muer` 健康项。

---

## 21. 下一步

接入成功后的建议阅读顺序：

- [手动部署](/getting-started/manual-deployment/)：不用 Docker 的完整部署流程；
- [定义权限](/getting-started/define-permissions/)：权限命名与演进；
- [权限管理](/getting-started/permission-management/)：如何管理权限、Template、Profile、Scope；
- [第一次使用管理控制台](/management/first-admin-tutorial/)：用 Console 完成授权配置；
- [排障](/operations/troubleshooting/)：403 / 401 排查；
- [生产检查清单](/operations/production-checklist/)。

---

## 你现在学会了什么

把整套心智收成一句话：

```text
宿主负责身份，Muer 负责 Token / Session。
开发者声明 Permission，管理员组合 Template / Profile / Scope。
业务资源由 Resolver 描述，Muer 只做授权判断。
拒绝访问用 Diagnostics 解释。
```

如果你已经能独立完成「建工程 → 加 Starter → 接 IdentityAuthenticator → 声明 Permission → 写 Resolver → 加 @RequirePermission → 登录 → GET 1001=200 → POST 1001=403 → Diagnostics → 切 Editor → POST 1001=200」，那 Muer 的第一次接入体验就已经跑通了。
