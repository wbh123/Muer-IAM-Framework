# IAM 0.1.0 Quick Start

本文面向第一次接入 `IAM Spring Boot Starter` 的 Spring Boot 应用开发者。
版本当前是 `0.1.0-SNAPSHOT`；它不是已发布的 Maven 发行版。示例和接口均以
当前源码及 `iam-example` 的消费者测试为准。

## 1. 添加 Starter

```xml
<dependency>
    <groupId>io.github.iamstarter</groupId>
    <artifactId>iam-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

默认自动配置需要 Java 21、Spring Boot 4、MySQL `DataSource` 与
`StringRedisTemplate`。生产应用通常只依赖该 starter；不要以 MyBatis mapper
或持久化实现作为应用扩展点。

## 2. 配置本地基础设施

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iam_host
    username: iam
    password: ${IAM_DB_PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379

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
  client-types: [WEB]
```

IAM 自己从 `classpath:db/iam/migration` 执行迁移，并使用独立的
`iam_flyway_schema_history`。仅当宿主应用有意接管相同 IAM schema 的部署过程时，
才设置 `iam.schema.enabled=false`。

## 3. 提供宿主身份适配器

`IdentityAuthenticator` 是宿主负责校验凭据的 SPI。空结果表示登录失败；返回的
`IamPrincipal` 必须来自宿主自己的身份和授权投影，不能包含密码、原始 token 或敏感
设备信息。

```java
@Bean
IdentityAuthenticator identityAuthenticator(AccountGateway accounts) {
    return request -> accounts.verify(request.username(), request.password())
            .map(account -> new IamPrincipal(
                    account.id(), account.identityKey(), account.identityDomain(),
                    account.activeProfileId(), account.templateVersionId(),
                    request.clientType(), account.authorizationVersion()));
}
```

`iam.client-types` 是登录客户端类型的精确允许列表；默认值是 `[WEB]`，不在列表中的
请求会在调用宿主 `IdentityAuthenticator` 前被拒绝。

## 4. 提供资源层级适配器

默认 `ResourceHierarchyProvider` 对所有范围关系返回 `false`。需要资源范围授权的
应用必须提供自己的适配器：

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

可选的 `AuthorizationPolicy` bean 可拒绝或注解决策，但不能绕过身份、profile、
permission 或 scope 的核心检查。

## 5. 登录并携带 Bearer token

`POST /iam/auth/login` 是匿名接口；其余 `/iam/**` 接口需要有效的 Bearer token。

```http
POST /iam/auth/login
Content-Type: application/json

{"username":"operator-a","password":"demo-pass","clientType":"WEB"}
```

登录成功返回 `200` 及 `accessToken`、`sessionId`、`expiresAt` 和 `principal`。后续请求：

```http
Authorization: Bearer <accessToken>
```

`iam-example` 的集成测试验证了登录、个人 session 列表、profile 切换，以及撤销后该
token 返回 `401` 的完整 HTTP 流程。可运行其 container-free 自动配置检查：

```bash
mvn -pl iam-example -am -Dtest=IamStarterAutoConfigurationSmokeTest test
```

完整 HTTP/MySQL/Redis 验证使用 `iam-example` 的 `integration` Maven profile；它需要
可用 Docker/Testcontainers 环境。

## 6. 为 MVC 处理器声明权限（可选）

`@RequirePermission` 仅作用于 Servlet MVC handler。宿主还必须注册
`MvcResourceDescriptorResolver`，将已经验证的请求变量转换成宿主拥有的
`ResourceDescriptor`：

```java
@RequirePermission(value = "invoice:approve", access = ScopeAccess.WRITE)
@PostMapping("/api/invoices/{id}/approve")
ResponseEntity<Invoice> approve(@PathVariable String id) { /* host logic */ }
```

未标注的路由不受影响。默认失败响应为 `application/problem+json`；其状态与稳定 `code`
见 [PUBLIC_API.md](PUBLIC_API.md#默认-mvc-失败响应)。宿主可替换
`IamAuthorizationFailureHandler` 来接入统一错误信封，但不得泄露 token、principal、
permission、resource、scope 或决策细节。

下一步请阅读 [PUBLIC_API.md](PUBLIC_API.md) 与
[IAM_MIGRATION_GUIDE.md](IAM_MIGRATION_GUIDE.md)。
