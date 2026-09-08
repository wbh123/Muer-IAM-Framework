---
title: Profile 切换
description: 切换 Profile 不修改旧 Session，而是创建新的 Token 与 Session。
sidebar:
  order: 2
---

## 解决的问题

同一用户可能需要在不同授权上下文之间切换，例如从 Reader 切换到 Editor。IAM 不会直接提升旧 Token，而是基于目标 Profile 创建一条新的 Session。

## 关键语义

- 旧 Token / Session 保持原权限；
- 新 Profile 对应新的 Token / Session；
- 两条 Session 独立生命周期；
- 返回结果仍是 `AuthenticationResult`：`accessToken`、`sessionId`、`expiresAt`、`principal`。

## 请求接口

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/profiles/{profileId}/switch` |
| Auth | Bearer Token |
| 成功 | HTTP `200` |
| 目标不存在 | `404` |

QuickStart 中 Alice 的 Editor Profile ID 为 `402`。

## 预期结果

切换成功后返回新的认证结果，例如：

```json
{
  "accessToken": "<new-editor-token>",
  "sessionId": "<new-editor-session>",
  "expiresAt": "<timestamp>",
  "principal": {
    "userId": 101,
    "activeProfileId": 402,
    "templateVersionId": 302
  }
}
```

此时：

- 新 Editor Token 使用 Profile 402；
- 原 Reader Token 仍保持 Reader 权限；
- 不应该把这个行为描述成“修改旧 Session”。

## 源码

- `AuthorizationProfileSwitchService`：<https://github.com/wbh123/Muer-IAM-Framework/tree/main/muer-authentication/src/main/java/cloud/muer/authentication>
- `AuthenticationResult`：同上目录
