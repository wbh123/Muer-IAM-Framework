---
title: 不透明令牌
description: 说明 IAM 访问令牌为不透明令牌的存储与解析机制。
sidebar:
  order: 5
---

## 解决什么问题

IAM 发行的 `accessToken` 是**不透明令牌（opaque token）**：它本身不可读、不含用户信息。服务端通过 `IamBearerTokenFilter` 持令牌去 Redis 换取 `IamPrincipal`，避免把敏感身份数据编码进令牌。

## 关键概念

- **TokenRecord**（record）：`(sessionId, principal, expiresAt)`，是 Redis 中令牌对应的主体记录。
- **AuthSession**（record）：`(sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt, lastSeenAt, expiresAt, revokedAt, logoutAt, revokeReason)`，提供 `revoked()/revoke(at,reason)/touch(at)`。
- 令牌 TTL 由 `iam.token.ttl`（默认 8h，必须为正）控制；Redis 键前缀由 `iam.token.redis-prefix`（默认 `iam`，非空）配置。

## 解析流程

```
请求携带 Authorization: Bearer <accessToken>
  → IamBearerTokenFilter
  → Redis 查 TokenRecord
  → 取出 IamPrincipal 注入安全上下文
```

## 源码

- TokenRecord / AuthSession / LoginResult：<https://github.com/wbh123/iam/blob/main/iam-session/src/main/java/io/github/iamstarter/session/>
- IamBearerTokenFilter：<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamBearerTokenFilter.java>
