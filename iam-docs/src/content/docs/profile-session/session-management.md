---
title: 会话管理
description: 查询与维护用户的登录会话，理解 AuthSession 的生命周期与心跳机制。
sidebar:
  order: 3
---

## 解决的问题

用户可能从多个设备/客户端登录，需要统一查看、续期与失效这些会话。`AuthSession` 记录每次登录的完整元数据，是审计与强制下线的依据。

## 关键概念

- `AuthSession` record 组件：`sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt, lastSeenAt, expiresAt, revokedAt, logoutAt, revokeReason`。
- 方法：`revoked()`、`revoke(at, reason)`、`touch(at)`（更新 `lastSeenAt`）。
- 心跳：配置 `iam.session.touch-interval`（默认 `10m`），每次访问刷新 `lastSeenAt`。
- 查询：`GET /iam/sessions` → `{ items: [ SessionResponse ] }`，含 `sessionId, userId, clientType, loginAt, lastSeenAt, expiresAt`。

## 真实示例

```bash
curl https://iam.example.com/iam/sessions \
  -H "Authorization: Bearer <token>"
```

返回示例：

```json
{
  "items": [
    { "sessionId": "sess-reader", "userId": 101, "clientType": "WEB",
      "loginAt": "2026-09-04T08:00:00Z", "lastSeenAt": "2026-09-04T09:30:00Z" }
  ]
}
```

会话持久化在 MySQL；Redis 仅存不透明令牌到会话的索引（见 [Redis 架构](/operations/redis/)）。

## 源码

- `AuthSession` / `TokenRecord`：https://github.com/wbh123/iam/blob/main/iam-session/src/main/java/io/github/iamstarter/session/
- 配置 `IamProperties`：https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
