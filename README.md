# 木耳 Muer

Identity infrastructure that grows naturally with your applications.

让身份能力，在每个系统中自然生长。\
Grow quietly. Connect steadily.

Muer Identity 是面向现代业务系统的通用身份与访问管理基础框架，提供认证、细粒度授权、权限模板、Profile、资源范围、会话管理、审计与可解释诊断能力。

## 0.1.0 scope

`0.1.0` is the first complete release baseline and includes the embeddable Starter, Management API, documentation site and the optional IAM Admin Console. The console is part of the 0.1.0 product scope, but it is **not** a runtime dependency of applications that only need the Starter.

The repository remains `0.1.0-SNAPSHOT` until the final release gate, tag and publication are explicitly approved.

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
- **Management API** exposes user, identity, permission, template, profile, scope, session, audit and overview operations under `/iam/**`.
- **Admin Console** provides an optional Vue 3 management client backed by the same OpenAPI contract and backend authorization engine.

## First use

Start with the source-verified [Quick Start](docs/QUICK_START.md), then use the [public API reference](docs/PUBLIC_API.md) to implement host adapters.

The 0.1.0 candidate scope and known limitations are in the [release notes](docs/RELEASE_NOTES_0.1.0.md); migration ownership and staged adoption are in the [migration guide](docs/IAM_MIGRATION_GUIDE.md).

A browsable Chinese documentation site lives in [`iam-docs/`](iam-docs/) (Astro + Starlight). To preview it locally:

```bash
cd iam-docs
npm ci
npm run dev
```

The documentation includes manual deployment guidance for MySQL, Redis, Spring Boot and the Admin Console. Docker is optional and is not required to use IAM.

## Five-minute Starter integration

1. Add the single dependency:

   ```xml
   <dependency>
       <groupId>io.github.muer</groupId>
       <artifactId>muer-spring-boot-starter</artifactId>
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

   muer:
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

See [IAM_INTEGRATION_GUIDE.md](docs/IAM_INTEGRATION_GUIDE.md) for infrastructure and SPI configuration and [muer-example](muer-example/README.md) for a maintainer-facing consumer showcase.

## IAM Admin Console

`iam-admin-web/` is the **optional management console shipped with 0.1.0**. It is a Vue 3 + TypeScript + Element Plus SPA whose API client is generated from the same [`iam.yaml`](muer-management-web/src/main/resources/openapi/iam.yaml) contract implemented by the Java Management API.

The console covers Dashboard, Users and Identities, Permission Explorer, Permission Templates, Authorization Profiles and Scopes, Sessions, Audit, Diagnostics and current-user account/profile operations.

### Local development demo

The example application can explicitly seed a development-only administrator. Both settings are required:

```text
SPRING_PROFILES_ACTIVE=dev
IAM_EXAMPLE_SEED_ADMIN=true
```

Then use:

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

This account is **never** auto-created in production. Do not copy these credentials into a real deployment.

Start the backend with your manually configured MySQL and Redis, then run the frontend:

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

Use the Vite address shown in the terminal (normally `http://localhost:5173`). A manual acceptance pass should at least cover login, Dashboard load, User/Profile/Session/Audit pages, one Profile/Scope edit, one Session revoke, Diagnostics, 403 route handling and logout.

### Production boundary

- The Starter works **without** the admin frontend: depending on `muer-spring-boot-starter` never requires serving `iam-admin-web`.
- Every `/iam/admin/**` request is re-authorized by `AuthorizationEngine` with fine-grained `iam.admin.*` permissions and resource scopes. Menu or route hiding is only a frontend usability guard.
- Production has no default administrator and no public bootstrap endpoint. The first administrator must be provisioned through controlled SQL/migration/deployment seeding or the host application's own initial provisioning flow.
- `POST /iam/authorization/diagnostics` remains authenticated self-diagnostics for the current principal and does not require an admin permission.

See [IAM Admin Console Deployment](docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md) for Nginx/reverse-proxy guidance, token/CSP rules, local demo startup and production administrator bootstrap.

## Project maintenance

- [Contributing](CONTRIBUTING.md) defines branch and compatibility rules.
- [Release policy](docs/RELEASE_POLICY.md) defines version and publication boundaries.
- [Security policy](SECURITY.md) defines responsible reporting expectations.

## Modules

Applications normally depend only on `muer-spring-boot-starter`. Internal modules separate the dependency-free domain, authentication, authorization, session, audit, diagnostics, MyBatis persistence, OpenAPI web contract and Spring Boot automatic configuration. `iam-admin-web` and `iam-docs` are repository-level companion applications, not Maven runtime modules.

## Security defaults

The starter uses an isolated `/iam/**` stateless security chain. Login is anonymous; every other IAM route requires a resolved Bearer principal. Host business routes remain host-owned and must explicitly reuse the IAM filter or principal resolver in their own security chain. The default hierarchy provider denies access until a host adapter is supplied.
