---
title: 配置
description: 通过 `iam.*` 前缀配置 Token、Session、Schema、审计与诊断等行为，并了解默认约束。
sidebar:
  order: 3
---

## 它解决什么问题

IAM 通过 `IamProperties`（前缀 `iam`）暴露可配置项。本页列出真实存在的属性、类型、默认值与约束；未列出的配置项请勿臆造。

## 配置项一览

| 属性 | 类型 | 默认 | 约束 |
| --- | --- | --- | --- |
| `iam.enabled` | boolean | true | — |
| `iam.token.ttl` | Duration | 8h | 必须为正 |
| `iam.token.redis-prefix` | String | iam | 非空 |
| `iam.session.enabled` | boolean | true | — |
| `iam.session.touch-interval` | Duration | 10m | — |
| `iam.schema.enabled` | boolean | true | — |
| `iam.schema.history-table` | String | iam_flyway_schema_history | 非空 |
| `iam.audit.enabled` | boolean | true | — |
| `iam.diagnostics.enabled` | boolean | true | — |
| `iam.client-types` | List<String> | [WEB] | ≥1 非空；登录 clientType 必须精确匹配 |

## 示例

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
  client-types:
    - WEB
```

## 关于 enabled 属性

`audit`、`diagnostics`、`session` 的 `enabled` 仅是属性暴露，当前自动配置并未按此做条件化 bean；文档按真实行为说明，可注明「属性已暴露」。`client-types` 决定允许的登录客户端类型，登录时的 `clientType` 必须精确匹配其中之一。

## 下一步

完整属性与 HTTP 状态见[配置参考](/reference/configuration/)；项目结构见[项目结构](/getting-started/project-structure/)。
