---
title: 基础配置
description: 配置 MySQL、Redis 与 `iam.*`，让 IAM Starter 在手动部署或现有基础设施中正常启动。
sidebar:
  order: 3
---

IAM 的运行配置分成两部分：

1. Spring Boot 管理的基础设施连接：`spring.datasource`、`spring.data.redis`；
2. IAM 自己的行为配置：`iam.*`。

Docker、物理机、云数据库或 Kubernetes 最终都落到这两组配置上。

## 1. MySQL 连接

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
```

如果数据库位于远端，把 `127.0.0.1` 换成实际内网地址或域名。

IAM 默认使用宿主应用的 `DataSource`。`iam.schema.enabled=true` 时，Starter 会自动执行 IAM Flyway migration。

## 2. Redis 连接

无密码：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

启用认证：

```yaml
spring:
  data:
    redis:
      host: 10.0.0.11
      port: 6379
      password: ${IAM_REDIS_PASSWORD}
```

Redis 不需要手工初始化 Key。IAM 使用 `iam.token.redis-prefix` 隔离自己的 Token 索引。

## 3. IAM 配置项

`IamProperties` 使用前缀 `iam`：

| 属性 | 类型 | 默认 | 约束 |
| --- | --- | --- | --- |
| `iam.enabled` | boolean | `true` | — |
| `iam.token.ttl` | Duration | `8h` | 必须为正 |
| `iam.token.redis-prefix` | String | `iam` | 非空 |
| `iam.session.enabled` | boolean | `true` | 属性已暴露 |
| `iam.session.touch-interval` | Duration | `10m` | — |
| `iam.schema.enabled` | boolean | `true` | — |
| `iam.schema.history-table` | String | `iam_flyway_schema_history` | 非空 |
| `iam.audit.enabled` | boolean | `true` | 属性已暴露 |
| `iam.diagnostics.enabled` | boolean | `true` | 属性已暴露 |
| `iam.client-types` | List<String> | `[WEB]` | 至少一个非空值，登录时精确匹配 |

## 4. 推荐起步配置

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
      password: ${IAM_REDIS_PASSWORD:}

iam:
  enabled: true
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  client-types:
    - WEB
```

## 5. 多应用共享 Redis

如果多个系统连接同一个 Redis，不要全部使用默认前缀 `iam`。

例如：

```yaml
iam:
  token:
    redis-prefix: order-service:iam
```

另一个应用可以使用：

```yaml
iam:
  token:
    redis-prefix: hr-service:iam
```

这样 Token 与反向索引不会互相混淆。

## 6. Schema 管理

默认推荐：

```yaml
iam:
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
```

只有当你明确由外部发布流程管理同一套 IAM migration 时才关闭：

```yaml
iam:
  schema:
    enabled: false
```

关闭前必须确保数据库 schema 已经和当前 IAM 版本一致。

## 7. 关于 enabled 属性

`audit`、`diagnostics`、`session` 的 `enabled` 当前主要是配置属性暴露，自动配置尚未全部按这些值条件化 Bean。文档按当前真实实现描述，不应把它们理解成所有组件的硬开关。

`client-types` 则会真实参与登录前校验，不在列表中的 `clientType` 会在宿主 `IdentityAuthenticator` 之前被拒绝。

## 8. 配置完成后的最小确认

普通使用者只需要确认：

- Spring Boot 能正常启动；
- 没有 MySQL / Redis 连接错误；
- Schema migration 正常完成或已经由外部流程管理；
- 合法账号登录成功；
- `GET /iam/auth/me` 能返回当前 Principal。

无需运行 IAM 仓库自身的全量集成测试。

更多基础设施步骤见[手动部署](/getting-started/manual-deployment/)，全部 IAM 属性见[配置参考](/reference/configuration/)。
