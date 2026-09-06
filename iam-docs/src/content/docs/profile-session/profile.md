---
title: Profile（身份画像）
description: 理解 Profile 不是角色，而是把权限模板版本与资源作用域绑定到具体用户的可切换身份视图。
sidebar:
  order: 1
---

## 解决的问题

传统 RBAC 把「角色」直接挂在用户上，一个用户只能有一种权限集合，难以表达「同一人在不同项目里既是读者又是编辑者」。`Profile` 解决了这个矛盾：它**不是 Role**，而是把某一套权限模板版本（`PermissionTemplateVersion`）与一组资源作用域（`ResourceScope`）绑定到某个用户的可切换「身份视图」。

## 关键概念

- **Identity = 用户本体**（如 Alice，`userId=101`）；Profile 是挂在这个本体上的视图。
- 一个用户可以拥有多个 Profile：Reader Profile 仅含 `document:read`，Editor Profile 含 `document:read` + `document:update`。
- `AuthorizationProfile` record 组件：`profileId, userId, profileName, templateVersionId, clientTypes, enabled, revoked, validFrom, validUntil, scopes`。
- `activeProfileId` 记录在 `IamPrincipal` 上，标记当前生效的 Profile。

## 真实示例（QuickStart 种子数据）

Alice 默认登录得到 `IamPrincipal(userId=101, activeProfileId=401, templateVersionId=301)`：

| Profile | 名称 | 权限 | 作用域 |
| --- | --- | --- | --- |
| 401 | `alice-reader-project-101` | `document:read` | `PROJECT:101` READ |
| 402 | `alice-editor-project-101` | `document:read`+`document:update` | `PROJECT:101` READ+WRITE |

即：profile 401 只能读 project 101 内文档；profile 402 额外具备写权限。

## 源码

- `AuthorizationProfile`：https://github.com/wbh123/iam/blob/main/muer-authorization/src/main/java/io/github/iamstarter/authorization/
- `IamPrincipal`：https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java
- 演示种子：`muer-example/src/main/java/io/github/iamstarter/example/`

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
