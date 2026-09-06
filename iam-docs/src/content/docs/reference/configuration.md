---
title: 配置参考
description: Spring Boot 基础设施连接与 IamProperties 的真实配置项，以及开发和生产建议。
sidebar:
  order: 1
---

IAM 配置分为两层：

- `spring.datasource` / `spring.data.redis`：由 Spring Boot 管理 MySQL 与 Redis 连接；
- `iam.*`：由 `MuerProperties` 管理 IAM 行为。

## 基础设施连接

### MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://10.0.0.10:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
```

### Redis

```yaml
spring:
  data:
    redis:
      host: 10.0.0.11
      port: 6379
      password: ${IAM_REDIS_PASSWORD:}
```

Docker、本机安装、云数据库最终都使用同样的 Spring Boot 连接属性。IAM 不定义第二套 MySQL / Redis 地址配置。

## `iam.*` 配置属性

| Property | Type | Default | Required | Description |
| --- | --- | --- | --- | --- |
| `iam.enabled` | boolean | `true` | 否 | 是否启用 IAM 自动配置的主要集成行为。 |
| `iam.token.ttl` | Duration | `8h` | 否 | Access Token 有效期，必须为正。 |
| `iam.token.redis-prefix` | String | `iam` | 否 | Redis Key 前缀，必须非空；多应用共享 Redis 时应使用应用独立值。 |
| `iam.session.enabled` | boolean | `true` | 否 | Session 属性开关；当前自动配置并未完全据此条件化 Bean。 |
| `iam.session.touch-interval` | Duration | `10m` | 否 | Session 活跃时间更新间隔。 |
| `iam.schema.enabled` | boolean | `true` | 否 | 是否由 Starter 执行 IAM Flyway migration。 |
| `iam.schema.history-table` | String | `iam_flyway_schema_history` | 否 | IAM Flyway 历史表名，必须非空。 |
| `iam.audit.enabled` | boolean | `true` | 否 | 审计属性已暴露；当前自动配置并未完全据此条件化 Bean。 |
| `iam.diagnostics.enabled` | boolean | `true` | 否 | 诊断属性已暴露；当前自动配置并未完全据此条件化 Bean。 |
| `iam.client-types` | List<String> | `[WEB]` | 否 | 登录 Client Type 允许列表，至少一个非空值并精确匹配。 |

## 最小配置

基础设施连接已经由宿主 Spring Boot 配好时：

```yaml
iam:
  enabled: true
  client-types:
    - WEB
```

## 推荐开发配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
  data:
    redis:
      host: 127.0.0.1
      port: 6379

iam:
  enabled: true
  token:
    ttl: 8h
    redis-prefix: local-app:iam
  session:
    enabled: true
    touch-interval: 10m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  client-types:
    - WEB
```

`iam.example.seed-demo` 属于 `muer-example` 的演示配置，不是通用 Starter 的 `MuerProperties`。只有运行仓库演示时才需要它。

## 推荐生产配置示例

```yaml
spring:
  datasource:
    url: ${APP_JDBC_URL}
    username: ${APP_DB_USERNAME}
    password: ${APP_DB_PASSWORD}
  data:
    redis:
      host: ${APP_REDIS_HOST}
      port: ${APP_REDIS_PORT:6379}
      password: ${APP_REDIS_PASSWORD:}

iam:
  enabled: true
  token:
    ttl: 2h
    redis-prefix: my-service:iam
  session:
    touch-interval: 5m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  client-types:
    - WEB
```

生产配置中的 TTL、Client Type 和 Redis Prefix 应根据业务实际调整，而不是机械复制示例值。

## Schema 管理模式

### Starter 自动管理

```yaml
iam:
  schema:
    enabled: true
```

适合大多数简单接入场景。

### 外部部署流程管理

```yaml
iam:
  schema:
    enabled: false
```

只有在 DBA / 发布平台已经应用同一版本 IAM migration 后才能关闭自动迁移。

## 配置后的验证

部署者只需确认应用能启动、MySQL / Redis 能连接，以及登录和当前 Principal 接口工作正常。完整框架集成测试由仓库 CI 负责。

## 源码参考

- `MuerProperties`：<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerProperties.java>
- 手动部署教程：[手动部署](/getting-started/manual-deployment/)
