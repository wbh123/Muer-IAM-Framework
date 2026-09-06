---
title: 会话吊销
description: 按 sessionId 精确吊销单个 Session，并保持其他 Session 独立有效。
sidebar:
  order: 4
---

## 解决的问题

某台设备丢失、某个登录态可疑，或某次高权限操作结束后，需要精确撤销对应 Session，而不是把同一用户的所有登录态一起失效。

## 关键概念

- 单 Session：`POST /iam/sessions/{sessionId}/revoke`；
- 批量撤销其他 Session：`POST /iam/sessions/revoke-others`；
- 管理端还提供指定 Session / 指定用户会话的撤销接口；
- 吊销一个 Session 不会自动影响同一用户的其他独立 Session。

## 请求接口

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/sessions/{sessionId}/revoke` |
| Auth | Bearer Token |
| 成功 | HTTP `204` |
| 未认证 | `401` |
| Session 不存在 | `404` |

## 预期结果

假设 Alice 同时有 Reader Session 和 Editor Session：

1. 撤销 Editor Session 返回 `204`；
2. Editor Token 随后访问受保护接口返回 `401`；
3. Reader Token 仍然可以访问 Reader 原本允许的资源。

这个行为体现了 IAM 的 Session 隔离：高权限 Session 的生命周期不会污染低权限 Session。

## 什么时候使用 revoke-others

**接口**：`POST /iam/sessions/revoke-others`

适合“保留当前设备，退出其他设备”的场景。成功返回 `204`。

## 源码

- `AuthSession.revoke`：<https://github.com/wbh123/iam/tree/main/muer-session/src/main/java/io/github/iamstarter/session>
- OpenAPI：<https://github.com/wbh123/iam/blob/main/muer-management-web/src/main/resources/openapi/iam.yaml>
