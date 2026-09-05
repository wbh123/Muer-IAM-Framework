---
title: 会话吊销
description: 按 sessionId 吊销单个会话，理解吊销彼此独立、互不影响的设计语义。
sidebar:
  order: 4
---

## 解决的问题

当某台设备丢失或某会话可疑时，需精确吊销该会话，而不影响用户的其他登录态（例如吊销编辑器会话，读者会话应继续存活）。

## 关键概念

- 端点：`POST /iam/sessions/{sessionId}/revoke` → `204`；未认证 `401`，会话不存在 `404`。
- 吊销仅作用于目标 `sessionId` 对应的 `AuthSession`：调用其 `revoke(at, reason)` 写入 `revokedAt`/`revokeReason`。
- **关键语义**：吊销一个会话**不会**影响其他会话。读者会话在编辑器会话被吊销后依然有效。
- 批量：`POST /iam/sessions/revoke-others` → `204`，吊销除当前外的其他会话。
- 管理端：`POST /iam/admin/sessions/{sessionId}/revoke`、`POST /iam/admin/users/{userId}/sessions/revoke`（需 `iam.admin.*` 权限）。

## 真实示例

吊销 Editor 会话，Reader 会话存活：

```bash
curl -X POST https://iam.example.com/iam/sessions/sess-editor/revoke \
  -H "Authorization: Bearer <reader-token>"
```

返回 `204 No Content`。随后读者令牌仍可访问 `GET /api/documents/1001`，仅编辑写操作需重新登录。

## 源码

- `AuthSession.revoke`：https://github.com/wbh123/iam/blob/main/iam-session/src/main/java/io/github/iamstarter/session/
- OpenAPI 端点定义：https://github.com/wbh123/iam/blob/main/iam-management-web/src/main/resources/openapi/iam.yaml

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
