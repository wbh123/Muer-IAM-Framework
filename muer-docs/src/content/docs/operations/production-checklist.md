---
title: 上线前检查清单
description: 上线前核对数据源、令牌、会话、审计与安全配置的关键检查项。
sidebar:
  order: 5
---

## 解决的问题

把 IAM 接入生产前，遗漏任一项（如 Redis 成真相来源、审计关闭、clientType 不匹配）都可能引发安全或可用性问题。本清单汇总必查项。

## 关键检查项

1. **MySQL 为真相来源**：确认 `muer.schema.enabled=true`，Flyway 历史表已建；切勿让 Redis 决定授权。
2. **Redis 仅索引**：`muer.token.redis-prefix` 非空，`muer.token.ttl` 为正。MySQL 是持久授权状态（principal/permission/template/profile/scope/session 记录）的权威；Redis 只保存不透明 Token 到 Session 的索引。若 Redis 中 Token 数据丢失，已签发 Token 可能无法继续解析、用户需要重新登录，但不会改变 MySQL 中的授权事实；Muer 不会把 MySQL 当作已签发 Token 的自动回源来重建 TokenRecord。
3. **会话配置**：`muer.session.enabled=true`，`touch-interval` 合理（默认 `10m`）。
4. **clientType**：`muer.client-types` ≥1 且非空，登录 `clientType` 精确匹配。
5. **审计开启**：`muer.audit.enabled=true`（属性已暴露），事件可回溯。
6. **诊断可用**：`muer.diagnostics.enabled=true`，便于排障（见 [授权诊断](/diagnostics/authorization-diagnostics/)）。
7. **反向代理**：透传 `Authorization` 与 `X-Forwarded-*` 头（见 [反向代理](/operations/reverse-proxy/)）。
8. **吊销能力**：确认 `POST /iam/sessions/{sessionId}/revoke` 可达，异常可强制下线。

## 真实示例

最小生产配置（application.yml）：

```yaml
muer:
  enabled: true
  token: { ttl: 8h, redis-prefix: iam }
  session: { enabled: true, touch-interval: 10m }
  schema: { enabled: true, history-table: iam_flyway_schema_history }
  audit: { enabled: true }
  diagnostics: { enabled: true }
  client-types: [WEB]
```

## 源码

- 配置全集 `MuerProperties`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
