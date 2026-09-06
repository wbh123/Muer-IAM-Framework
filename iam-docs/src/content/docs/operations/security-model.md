---
title: 安全模型
description: 梳理 IAM 的信任边界、令牌机密性与最小权限设计，理解整体防护假设。
sidebar:
  order: 4
---

## 解决的问题

接入 IAM 前需清楚：哪些假设成立、信任边界在哪、令牌泄露的后果是什么。本页汇总 IAM 的安全模型，帮助正确部署与威胁建模。

## 关键概念

- **不透明令牌（opaque token）**：`accessToken` 本身无含义，须经 Redis/MySQL 解析为 `TokenRecord`，降低令牌被本地伪造的风险。
- **持久化权威**：MySQL 为真相来源，Redis 仅索引（见 [MySQL](/operations/mysql/) / [Redis](/operations/redis/)）。
- **Profile 隔离**：切换 Profile 创建新会话，旧会话独立，缩小单点泄露影响面（见 [Profile 切换](/profile-session/profile-switch/)）。
- **会话吊销**：可按 `sessionId` 精确失效，互不影响（见 [会话吊销](/profile-session/session-revoke/)）。
- **clientType 绑定**：`IamPrincipal.clientType` 用于 `CLIENT_TYPE_MISMATCH` 校验，登录 `clientType` 须精确匹配 `iam.client-types`（默认 `[WEB]`）。

## 真实示例

威胁与缓解对照：

| 威胁 | 缓解 |
| --- | --- |
| 令牌泄露 | 不透明令牌 + 可吊销会话 |
| Redis 故障 | 回源 MySQL，不以 Redis 为真相 |
| 越权作用域 | `SCOPE_DENIED` + 决策步骤可诊断 |

## 源码

- `IamPrincipal`：https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/muer/core/model/IamPrincipal.java
- `IamBearerTokenFilter`：https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
