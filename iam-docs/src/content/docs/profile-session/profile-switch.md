---
title: Profile 切换
description: 切换 Profile 不会修改旧会话，而是基于新 Profile 创建全新的 Token/Session。
sidebar:
  order: 2
---

## 解决的问题

用户在同一会话里需要临时以更高权限身份操作（如从读者切到编辑者）。若直接「改写」旧会话，会破坏既有登录态、造成状态竞态。`Profile 切换`的语义是：**保留旧 Reader Token/Session 不变，并基于目标 Profile 创建一条全新的 Editor Token/Session**。

## 关键概念

- 切换端点：`POST /iam/authorization/profiles/{profileId}/switch` → `200`。
- 返回 `AuthenticationResult` record `(accessToken, sessionId, expiresAt, principal)`（与登录返回同构）。
- 新 Session 拥有目标 Profile 的 `activeProfileId` 与 `templateVersionId`，旧 Session 不受影响。
- **永远不要描述为「修改旧会话」**——这是两条独立生命周期的会话。

## 真实示例

Alice 默认持有 profile 401 的 Reader Token。切到编辑 Profile：

```bash
curl -X POST https://iam.example.com/iam/authorization/profiles/402/switch \
  -H "Authorization: Bearer <reader-token>" \
  -H "Content-Type: application/json"
```

响应（节选）：

```json
{
  "accessToken": "<new-editor-token>",
  "sessionId": "<new-editor-session>",
  "expiresAt": "2026-09-04T12:00:00Z",
  "principal": { "userId": 101, "activeProfileId": 402, "templateVersionId": 302 }
}
```

此时旧 Reader Token 仍有效，可继续用于只读操作。

## 源码

- `AuthorizationProfileSwitchService`：https://github.com/wbh123/iam/blob/main/iam-authentication/src/main/java/io/github/iamstarter/authentication/
- `AuthenticationResult`：同上目录

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
