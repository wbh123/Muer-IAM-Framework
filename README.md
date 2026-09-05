# IAM Spring Boot Starter

A reusable Identity and Access Management framework for Spring Boot applications, providing authentication, fine-grained authorization, permission templates, authorization profiles, resource scopes, distributed session management, security auditing and explainable authorization decisions.

## Capabilities

- **Authentication** delegates credential verification to a host adapter and issues opaque tokens.
- **Authorization** evaluates domain, atomic permission, active profile, resource scope and extension policies through one engine.
- **Role** is optional metadata and never the final enforcement input.
- **Permission** is an application-defined atomic capability code.
- **Template** versions make published permission sets immutable and traceable.
- **Profile** activates exactly one template version per token and session.
- **Scope** separates READ and WRITE access to application-defined resources.
- **Session** governance provides durable MySQL state and Redis token indexes.
- **Audit** stores generic events and multiple related subjects.
- **Diagnostics** projects the exact runtime authorization decision.

## First use

Start with the source-verified [Quick Start](docs/QUICK_START.md), then use the [public API reference](docs/PUBLIC_API.md) to implement the host adapters.

The 0.1.0 candidate scope and known limitations are in the [release notes](docs/RELEASE_NOTES_0.1.0.md); migration ownership and staged adoption are in the [migration guide](docs/IAM_MIGRATION_GUIDE.md).

A browsable Chinese documentation site lives in [`iam-docs/`](iam-docs/) (Astro + Starlight). To preview it locally:

```bash
cd iam-docs
npm ci
npm run dev
```

The documentation includes a manual deployment guide for MySQL, Redis and Spring Boot configuration. Docker is optional and is not required to use IAM.

## Five-minute integration

1. Add the single dependency:

   ```xml
   <dependency>
       <groupId>io.github.iamstarter</groupId>
       <artifactId>iam-spring-boot-starter</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

2. Point the host application at an existing or manually prepared MySQL and Redis service:

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

   iam:
     enabled: true
     token:
       ttl: 8h
       redis-prefix: my-app:iam
     client-types:
       - WEB
   ```

3. Register an `IdentityAuthenticator` that verifies host credentials and returns an `IamPrincipal`.
4. Register a `ResourceHierarchyProvider` when scoped resources are used.
5. If MVC routes use `@RequirePermission`, register an `MvcResourceDescriptorResolver`.
6. Start the application and confirm `POST /iam/auth/login` plus `GET /iam/auth/me` behave as expected.

Using Docker, Testcontainers, or the repository's complete verification suite is **not** required for application deployment. Those tools are used by IAM maintainers and CI. If a developer has no local MySQL / Redis, `examples/quickstart/docker-compose.yml` remains available as an optional convenience.

See [IAM_INTEGRATION_GUIDE.md](docs/IAM_INTEGRATION_GUIDE.md) for infrastructure and SPI configuration and [iam-example](iam-example/README.md) for a maintainer-facing consumer showcase.

## Project maintenance

- [Contributing](CONTRIBUTING.md) defines branch and compatibility rules.
- [Release policy](docs/RELEASE_POLICY.md) defines version and publication boundaries.
- [Security policy](SECURITY.md) defines responsible reporting expectations.

## Modules

Applications normally depend only on `iam-spring-boot-starter`. Internal modules separate the dependency-free domain, authentication, authorization, session, audit, diagnostics, MyBatis persistence, OpenAPI web contract and Spring Boot automatic configuration.

## Security defaults

The starter uses an isolated `/iam/**` stateless security chain. Login is anonymous; every other IAM route requires a resolved Bearer principal. Host business routes remain host-owned and must explicitly reuse the IAM filter or principal resolver in their own security chain. The default hierarchy provider denies access until a host adapter is supplied.
