---
title: 迁移总览
description: 宿主如何在不一次性替换既有权限系统的前提下，渐进式采用 IAM。
sidebar:
  order: 1
---

## 解决什么问题

很多团队已有自己的登录与权限逻辑，无法一夜之间替换。IAM 提供**渐进式采用**路径：先并行评估、再投影数据、最后可随时回滚，避免"全有或全无"的绑定。

## 关键概念

- **Shadow Mode（影子模式）**：IAM 参与授权评估但不拦截请求，只记录决策，用于与旧系统对比验证。
- **Data Projection（数据投影）**：把宿主现有的用户/身份投影进 IAM，而不是迁移数据库。
- **Rollback（回滚）**：通过撤销 IAM 下发的 Session、关闭 `iam.enabled` 回到旧路径。

## 推荐路线

1. 接入 `IdentityAuthenticator`，让现有登录产出 `IamPrincipal`；见 [数据投影](/migration/data-projection/)。
2. 开启影子模式对照决策；见 [影子模式](/migration/shadow-mode/)。
3. 验证无差异后切流，保留回滚能力；见 [回滚](/migration/rollback/)。

## 源码参考

- `IdentityAuthenticator`：<https://github.com/wbh123/iam/blob/main/muer-authentication/src/main/java/io/github/muer/authentication/IdentityAuthenticator.java>
- `MuerProperties`：<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerProperties.java>
