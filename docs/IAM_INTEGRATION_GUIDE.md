# IAM Integration Guide

## Dependency

A consuming Spring Boot application declares one IAM production dependency:

```xml
<dependency>
    <groupId>io.github.iamstarter</groupId>
    <artifactId>iam-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Java 21, Spring Boot 4, a MySQL `DataSource`, and a `StringRedisTemplate` are
required by the default adapters. Applications can replace any default port
bean when another implementation is needed.

## Minimal configuration

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

IAM migrations are loaded only from `classpath:db/iam/migration` and use their
own history table. Set `iam.schema.enabled=false` only when the host deliberately
manages the same IAM schema by another deployment process.

`iam.token.ttl` must be a positive duration. Invalid values are rejected while
the Spring application context is being created, before any token can be issued.

`iam.client-types` is the starter login allow-list. A client type must match an
entry exactly; other login attempts are rejected before the host
`IdentityAuthenticator` is invoked. The default allow-list is `[WEB]`.

## Required identity adapter

The host verifies its own credentials and projects the result into one generic
principal. Returning an empty result rejects the login.

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

The adapter must not place passwords, raw tokens, or sensitive device values in
the principal. Profile, template, client and authorization-version values must
match the IAM persistence projection.

The management login controller records `HttpServletRequest.getRemoteAddr()` as
the remote address and does not trust forwarding headers by default. Deployments
behind a trusted proxy should normalize the servlet remote address at the host
container boundary. User agent, request ID and coarse client labels are audit
metadata only and never become authorization inputs. The built-in controller
removes control characters and bounds each value to the IAM schema width.

## Resource and policy adapters

The default `ResourceHierarchyProvider` denies hierarchy membership. A host
that uses scoped resources supplies its own provider:

```java
@Bean
ResourceHierarchyProvider resourceHierarchyProvider(ResourceGateway resources) {
    return resources::isWithinScope;
}
```

Additional hard-deny or compliance behavior is registered through ordered
`AuthorizationPolicy` beans. Policies can deny or annotate a decision; they do
not bypass core identity, profile, permission or scope checks.

## HTTP and security behavior

The starter contributes a stateless security chain only for `/iam/**`:

- `POST /iam/auth/login` is anonymous;
- all other IAM endpoints require a valid Bearer token;
- the IAM filter is disabled as a global servlet filter;
- the host remains responsible for security rules outside `/iam/**`.

The complete contract is in `iam-management-web/src/main/resources/openapi/iam.yaml`.

## Operational boundary

Redis stores opaque tokens and reverse indexes; MySQL stores durable sessions,
profiles, permission-template versions and audit data. MySQL remains the
durable authority for authorization state. Changing authorization-relevant
state must increment the user's authorization version so previously issued
tokens become invalid.
