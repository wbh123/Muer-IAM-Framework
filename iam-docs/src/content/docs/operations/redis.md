---
title: Redis 存储
description: Redis 仅作为不透明令牌的会话索引，绝不是授权的真相来源。
sidebar:
  order: 2
---

## 解决的问题

每次请求都要快速把不透明令牌（`accessToken`）映射到其 `TokenRecord`（含 `AuthSession` 与 `IamPrincipal`）。Redis 提供低延迟的索引，避免每次请求回查 MySQL。

## 关键概念

- `TokenRecord` record：`(sessionId, principal, expiresAt)`——Redis 中按 `accessToken` 索引的条目。
- 配置：`iam.token.ttl`（默认 `8h`）、`iam.token.redis-prefix`（默认 `iam`，非空）。
- 类比：**Redis 是高速索引卡**，指向档案室（MySQL）中的真实会话与身份。
- 关键约束：Redis 只存「令牌→记录」的索引，**不能**成为授权判定的真相来源。若 Redis 清空，应回源 MySQL 重建索引，而非以 Redis 状态决定允许/拒绝。

## 真实示例

配置令牌 TTL 与 Redis 前缀（application.yml）：

```yaml
iam:
  token:
    ttl: 8h
    redis-prefix: iam
  session:
    enabled: true
    touch-interval: 10m
```

令牌校验流程：`Bearer <token>` → Redis 查 `iam:<token>` → 得到 `TokenRecord` → 取出 `IamPrincipal` 交给授权引擎。

## 源码

- `TokenRecord` / `AuthSession`：https://github.com/wbh123/iam/blob/main/iam-session/src/main/java/io/github/iamstarter/session/
- `IamBearerTokenFilter`：https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
