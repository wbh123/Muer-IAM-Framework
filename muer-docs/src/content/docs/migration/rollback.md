---
title: 回滚
description: 当 IAM 出现问题时，如何撤销其下发的会话并回到原有路径。
sidebar:
  order: 4
---

## 解决什么问题

渐进式采用的底气来自"能随时退出"。本页说明 IAM 的回滚手段：撤销已下发的会话，并关闭 IAM 接管。

## 关键概念

IAM 的访问凭据是 Token，背后是 `AuthSession(sessionId, userId, clientType, ..., revokedAt, logoutAt, revokeReason)` 与 `TokenRecord(sessionId, principal, expiresAt)`，存于 Redis。回滚即**让这些凭据失效**，并停用 IAM 的强制授权。

## 实践步骤

1. **撤销单个会话**：`POST /iam/sessions/{sessionId}/revoke`（204/401/404）。
2. **撤销当前用户其他会话**：`POST /iam/sessions/revoke-others`（204/401）。
3. **管理员批量撤销**：`POST /iam/admin/sessions/{sessionId}/revoke`、`POST /iam/admin/users/{userId}/sessions/revoke`。
4. **整体退出**：将配置 `muer.enabled=false` 并重启，IAM 拦截器与 Bearer 过滤器不再参与，回到宿主原有权限路径。注意 Token 仍留在 Redis，必要时清理 `muer.token.redis-prefix`（默认 `iam`）对应的键。

## 源码参考

- `AuthSession`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-session/src/main/java/cloud/muer/session/AuthSession.java>
- `MuerProperties`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/MuerProperties.java>
