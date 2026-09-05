---
title: 配置参考
description: IamProperties 的全部真实配置项及 minimal/dev/production 推荐配置。
sidebar:
  order: 1
---

## 配置属性（前缀 `iam`）

| Property | Type | Default | Required | Description |
| --- | --- | --- | --- | --- |
| `iam.enabled` | boolean | `true` | 否 | 是否启用 IAM 拦截器与 Bearer 过滤器；设为 `false` 可整体退出。 |
| `iam.token.ttl` | Duration | `8h` | 否 | Access Token 有效期，必须为正。 |
| `iam.token.redis-prefix` | String | `iam` | 否 | Redis 键前缀，非空；回滚时据此清理 Token。 |
| `iam.session.enabled` | boolean | `true` | 否 | 会话能力开关（属性已暴露；当前自动配置未按此条件化 bean）。 |
| `iam.session.touch-interval` | Duration | `10m` | 否 | 会话续期间隔。 |
| `iam.schema.enabled` | boolean | `true` | 否 | 是否启用 Flyway schema 管理。 |
| `iam.schema.history-table` | String | `iam_flyway_schema_history` | 否 | Flyway 历史表名，非空。 |
| `iam.audit.enabled` | boolean | `true` | 否 | 审计开关（属性已暴露；当前自动配置未按此条件化 bean）。 |
| `iam.diagnostics.enabled` | boolean | `true` | 否 | 诊断开关（属性已暴露；当前自动配置未按此条件化 bean）。 |
| `iam.client-types` | List<String> | `[WEB]` | 否 | 允许的客户端类型，≥1 且非空；登录 `clientType` 必须精确匹配。 |

> 注意：`audit/diagnostics/session` 的 `enabled` 仅是属性暴露，当前自动配置并未据此做条件化 bean。

## minimal 配置

```yaml
iam:
  enabled: true
  client-types: [WEB]
```

## dev 推荐配置

```yaml
iam:
  enabled: true
  token:
    ttl: 8h
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  audit:
    enabled: true
  diagnostics:
    enabled: true
  client-types: [WEB]
  example:
    seed-demo: true   # QuickStartDemoSeeder 注入演示数据
```

## production 推荐配置

```yaml
iam:
  enabled: true
  token:
    ttl: 2h
    redis-prefix: iam-prod
  session:
    touch-interval: 5m
  schema:
    history-table: iam_flyway_schema_history
  client-types: [WEB, MOBILE]
```

## 源码参考

- `IamProperties`：<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamProperties.java>
