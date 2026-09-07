---
title: 审计日志
description: 了解 IAM 的审计能力开关与事件记录范围，满足合规追溯需求。
sidebar:
  order: 2
---

## 解决的问题

安全合规要求对「谁、在何时、以何种身份、做了什么授权决策」留痕。审计日志把登录、会话、Profile 切换与授权决策等关键事件持久化，便于事后追溯与责任认定。

## 关键概念

- 配置开关：`muer.audit.enabled`（默认 `true`，属性已暴露）。
- 审计覆盖的关键动作：登录成功/失败、会话创建与吊销、Profile 切换、授权允许/拒绝决策。
- 与诊断不同：审计面向「事件留痕与合规」，诊断面向「实时排查决策原因」。
- 审计事件与 `AuthSession`、`AuthorizationDecision` 关联，可按 `sessionId`/`userId` 检索。

## 真实示例

配置开启审计（application.yml）：

```yaml
muer:
  audit:
    enabled: true
```

典型审计事件（概念结构）：

| 事件 | 关联字段 |
| --- | --- |
| LOGIN | userId, clientType, ipAddress, sessionId |
| PROFILE_SWITCH | userId, fromProfileId, toProfileId |
| AUTHZ_DENY | userId, decisionCode(SCOPE_DENIED), resourceId |

## 源码

- 配置 `MuerProperties`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/
- Session 模型：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-session/src/main/java/cloud/muer/session/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
