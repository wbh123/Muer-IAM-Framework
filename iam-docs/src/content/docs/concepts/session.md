---
title: 会话（Session）
description: 理解登录产生的 AuthSession 与 TokenRecord，以及查询、续期与撤销的生命周期管理。
sidebar:
  order: 6
---

## 它解决什么问题

认证后需要统一治理「这次登录活了多久、最近是否活跃、能否被撤销」。IAM 把每次登录建模为一个 `AuthSession`，并用不透明 Token 索引它。

## 核心类型

```java
// 会话记录
record AuthSession(
    sessionId, userId, clientType, clientInstance,
    ipAddress, userAgent, loginAt, lastSeenAt,
    expiresAt, revokedAt, logoutAt, revokeReason
) {
    boolean revoked();
    void revoke(Instant at, String reason);
    void touch(Instant at);   // 更新 lastSeenAt
}

// 令牌记录：sessionId -> principal 的 Redis 索引
record TokenRecord(sessionId, principal, expiresAt)
```

源码见 [muer-session](https://github.com/wbh123/iam/blob/main/muer-session/src/main/java/io/github/iamstarter/session/)。`touch` 受 `iam.session.touch-interval`（默认 10m）节流。

## 相关 HTTP 端点（真实）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/iam/sessions` | 列出当前用户会话（200/401） |
| POST | `/iam/sessions/{sessionId}/revoke` | 撤销指定会话（204/401/404） |
| POST | `/iam/sessions/revoke-others` | 撤销其他会话（204/401） |
| POST | `/iam/auth/logout` | 登出（204/401） |

## 示例：撤销自己的会话

```bash
curl -X POST "$BASE/iam/sessions/$SESSION_ID/revoke" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{"reason":"QUICKSTART_COMPLETE"}'
# 204
```

撤销后该 token 失效（读取返回 401），但其他独立 session 不受影响。

## 下一步

Profile 切换会创建独立 session，见 [Profile 切换](/profile-session/profile-switch/) 与 [Session 撤销](/profile-session/session-revoke/)。
