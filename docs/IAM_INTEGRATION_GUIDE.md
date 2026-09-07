# IAM Integration Guide

## Dependency

A consuming Spring Boot application declares one IAM production dependency:

```xml
<dependency>
    <groupId>cloud.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Java 21, Spring Boot 4, a MySQL `DataSource`, and a `StringRedisTemplate` are required by the default adapters.

IAM does **not** require Docker. MySQL and Redis may come from local installations, internal services, managed cloud services, containers, or Kubernetes. What matters is that the host Spring Boot application can connect to them with standard Spring Boot configuration.

## Infrastructure preparation

### MySQL

The release validation baseline uses MySQL 8.4. An existing compatible MySQL 8.x instance can be reused.

A simple database and account can be prepared manually:

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

Use a real application host or controlled network instead of `localhost` when MySQL is remote. Production credentials must not reuse documentation passwords.

By default IAM uses the host `DataSource` and runs its Flyway migrations automatically. No manual IAM table creation is normally required.

### Redis

The release validation baseline uses Redis 7. IAM does not require pre-created keys or data structures.

For a same-host deployment, Redis can remain bound to the loopback interface. For remote deployments, expose it only on a controlled private network and enable authentication according to the deployment environment.

## Minimal configuration

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379
      password: ${IAM_REDIS_PASSWORD:}

muer:
  enabled: true
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
  client-types:
    - WEB
```

IAM migrations are loaded only from `classpath:db/iam/migration` and use their own history table. Set `muer.schema.enabled=false` only when the host deliberately manages the same IAM schema through another deployment process and the schema has already been applied.

`muer.token.ttl` must be positive. Invalid values are rejected when the Spring application context is created.

`muer.client-types` is the starter login allow-list. A client type must match an entry exactly; other login attempts are rejected before the host `IdentityAuthenticator` is invoked. The default is `[WEB]`.

`muer.token.redis-prefix` must be non-blank. Use a host-specific value when multiple applications share Redis.

## Required identity adapter

The host verifies its own credentials and projects the result into one generic principal. Returning an empty result rejects the login.

```java
@Bean
IdentityAuthenticator identityAuthenticator(AccountGateway accounts) {
    return request -> accounts.verify(request.username(), request.password())
            .map(account -> new IamPrincipal(
                    account.id(),
                    account.identityKey(),
                    account.identityDomain(),
                    account.activeProfileId(),
                    account.templateVersionId(),
                    request.clientType(),
                    account.authorizationVersion()));
}
```

The adapter must not place passwords, raw tokens, or sensitive device values in the principal. Profile, template, client and authorization-version values must match the IAM persistence projection.

The management login controller records `HttpServletRequest.getRemoteAddr()` as the remote address and does not trust forwarding headers by default. Deployments behind a trusted proxy should normalize the servlet remote address at the host container boundary.

## Declare application permissions

Declare business permission codes in the host application instead of inserting them directly into an IAM table:

```java
@Bean
PermissionDefinitionProvider documentPermissions() {
    return () -> List.of(
            new PermissionDefinition("document:read", "Read document", "Read a document"),
            new PermissionDefinition("document:update", "Update document", "Update a document"));
}
```

At application-ready time, Muer registers new definitions and refreshes only their display name and description. It does not delete undeclared historical rows or change enabled state. Exact duplicate definitions are accepted once; conflicting declarations for the same code fail startup. Administrators then assemble declared permissions into template versions, Profiles and Scopes; they do not create arbitrary business codes. Do not write `iam_*` tables directly.

## Resource and policy adapters

The default `ResourceHierarchyProvider` denies hierarchy membership. A host that uses scoped resources supplies its own provider:

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

Additional hard-deny or compliance behavior is registered through ordered `AuthorizationPolicy` beans. Policies can deny or annotate a decision; they do not bypass core identity, profile, permission or scope checks.

## HTTP and security behavior

The starter contributes a stateless security chain only for `/iam/**`:

- `POST /iam/auth/login` is anonymous;
- all other IAM endpoints require a valid Bearer token;
- the IAM filter is disabled as a global servlet filter;
- the host remains responsible for security rules outside `/iam/**`.

The complete contract is in `muer-management-web/src/main/resources/openapi/iam.yaml`.

For deployment verification, users normally only need to confirm:

1. the Spring Boot application starts successfully;
2. MySQL and Redis connections succeed;
3. IAM schema migration succeeds or is already managed externally;
4. a valid user can call `POST /iam/auth/login` and receive `200`;
5. the returned token can call `GET /iam/auth/me` and receive `200`;
6. one representative business authorization path returns the expected allow or deny result.

Users do not need to run the repository's full Testcontainers or independent-consumer test suites. Those are maintained by IAM project CI.

## Operational boundary

Redis stores opaque tokens and reverse indexes; MySQL stores durable sessions, profiles, permission-template versions and audit data. MySQL remains the durable authority for authorization state.

Changing authorization-relevant state must increment the user's authorization version so previously issued tokens become invalid.

When a Micrometer `MeterRegistry` is present, Muer emits low-cardinality authentication, authorization, session and token-lookup metrics. Without one it uses a no-op implementation. If Spring Boot Health is present, its Muer health contributor reports framework availability only and does not probe MySQL or Redis.

For a Chinese step-by-step guide, see `docs/QUICK_START.md` and the documentation site's **手动部署**, **MySQL**, **Redis**, and **配置参考** pages.
