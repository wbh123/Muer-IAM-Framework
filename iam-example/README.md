# IAM Starter Consumer Example

This module is a consuming Spring Boot application, not another IAM deployment
layer. Its only production dependency is `iam-spring-boot-starter`.

The example provides a deliberately small `IdentityAuthenticator` and uses the
starter defaults for schema migration, MyBatis repositories, Redis tokens,
session persistence, controllers and `/iam/**` security.

`GET /example/orders/{orderId}` requires `order.read` plus READ access to the
order's department. `POST /example/orders/{orderId}/approve` requires
`order.approve` plus WRITE access. The example business security chain reuses
the starter Bearer filter explicitly, demonstrating how host routes remain
host-owned while consuming the same IAM principal.

Runtime environment variables:

```text
IAM_EXAMPLE_JDBC_URL
IAM_EXAMPLE_DB_USERNAME
IAM_EXAMPLE_DB_PASSWORD
IAM_EXAMPLE_REDIS_HOST
IAM_EXAMPLE_REDIS_PORT
```

The bundled `demo` / `demo-pass` credential exists only to demonstrate the
adapter boundary and must never be copied into a real application. The
integration test starts isolated MySQL and Redis containers, seeds the matching
generic IAM projection, logs in over HTTP and reads the persisted session with
the returned Bearer token.
