# IAM Spring Boot Starter

A reusable Identity and Access Management framework for Spring Boot applications, providing authentication, fine-grained authorization, permission templates, authorization profiles, resource scopes, distributed session management, security auditing and explainable authorization decisions.

## Capabilities

- **Authentication** delegates credential verification to a host adapter and
  issues opaque tokens.
- **Authorization** evaluates domain, atomic permission, active profile,
  resource scope and extension policies through one engine.
- **Role** is optional metadata and never the final enforcement input.
- **Permission** is an application-defined atomic capability code.
- **Template** versions make published permission sets immutable and traceable.
- **Profile** activates exactly one template version per token and session.
- **Scope** separates READ and WRITE access to application-defined resources.
- **Session** governance provides durable MySQL state and Redis token indexes.
- **Audit** stores generic events and multiple related subjects.
- **Diagnostics** projects the exact runtime authorization decision.

## Five-minute integration

1. Add the single dependency:

   ```xml
   <dependency>
       <groupId>io.github.iamstarter</groupId>
       <artifactId>iam-spring-boot-starter</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

2. Configure a MySQL `DataSource`, Redis connection, and IAM settings:

   ```yaml
   iam:
     enabled: true
     token:
       ttl: 8h
       redis-prefix: iam
     client-types: [WEB]
   ```

3. Register an `IdentityAuthenticator` that verifies host credentials and
   returns an `IamPrincipal`.
4. Register a `ResourceHierarchyProvider` for scoped business resources.
5. Start the application and use `POST /iam/auth/login`; send the returned
   opaque token as `Authorization: Bearer <token>`.

See [IAM_INTEGRATION_GUIDE.md](docs/IAM_INTEGRATION_GUIDE.md) for complete
configuration and the [iam-example consumer showcase](iam-example/README.md)
for a copyable login, generic document permission/scope flow, scoped order
read, denied request, profile switch, runtime diagnostics, and session-
revocation walkthrough. Its fast smoke check is container-free; its separate
Docker/Testcontainers command proves the complete HTTP flow against MySQL and
Redis.

## Project maintenance

- [Contributing](CONTRIBUTING.md) defines branch and compatibility rules.
- [Release policy](docs/RELEASE_POLICY.md) defines version and publication boundaries.
- [Security policy](SECURITY.md) defines responsible reporting expectations.

## Modules

Applications normally depend only on `iam-spring-boot-starter`. Internal
modules separate the dependency-free domain, authentication, authorization,
session, audit, diagnostics, MyBatis persistence, OpenAPI web contract and
Spring Boot automatic configuration.

## Security defaults

The starter uses an isolated `/iam/**` stateless security chain. Login is
anonymous; every other IAM route requires a resolved Bearer principal. Host
business routes remain host-owned and must explicitly reuse the IAM filter or
principal resolver in their own security chain. The default hierarchy provider
denies access until a host adapter is supplied.
