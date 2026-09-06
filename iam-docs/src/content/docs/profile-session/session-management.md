---
title: 会话管理
description: 查询和理解用户登录 Session、生命周期与 MySQL/Redis 存储边界。
sidebar:
  order: 3
---

## 解决的问题

用户可能从多个设备或客户端登录，需要查看当前有哪些 Session、它们何时登录、最近何时活跃，以及何时过期。

## 关键概念

`AuthSession` 记录：

```text
sessionId
userId
clientType
clientInstance
ipAddress
userAgent
loginAt
lastSeenAt
expiresAt
revokedAt
logoutAt
revokeReason
```

常用生命周期方法包括 `revoked()`、`revoke(at, reason)` 和 `touch(at)`。

`iam.session.touch-interval` 默认 `10m`，用于控制活跃时间更新频率。

## 查询当前 Session

| 项目 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/iam/sessions` |
| Auth | Bearer Token |
| 成功 | HTTP `200` |

响应结构：

```json
{
  "items": [
    {
      "sessionId": "sess-reader",
      "userId": 101,
      "clientType": "WEB",
      "loginAt": "<timestamp>",
      "lastSeenAt": "<timestamp>",
      "expiresAt": "<timestamp>"
    }
  ]
}
```

## 存储边界

- MySQL 保存持久 Session 状态；
- Redis 保存不透明 Token 到 `TokenRecord` 的快速索引；
- Session 是否撤销、Profile 等持久事实不能只依赖 Redis。

详细基础设施说明见 [MySQL](/operations/mysql/) 和 [Redis](/operations/redis/)。

## 源码

- `AuthSession` / `TokenRecord`：<https://github.com/wbh123/iam/tree/main/muer-session/src/main/java/io/github/muer/session>
- `MuerProperties`：<https://github.com/wbh123/iam/tree/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure>
