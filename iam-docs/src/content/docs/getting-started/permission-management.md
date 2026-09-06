---
title: 权限管理
description: 使用管理控制台或 Management API 管理模板、Profile 和 Scope，无需另建权限后端。
sidebar:
  order: 5
---

不需要为 Muer 单独建设权限管理后端。Starter 随附的 Management API 与可选管理控制台可管理权限模板、Profile、Scope、会话和审计；宿主也可以基于同一 OpenAPI 构建自己的管理界面。

权限代码本身由应用的 `PermissionDefinitionProvider` 声明，管理员不应在控制台或数据库中任意创造业务权限代码。管理员的职责是把已声明权限组合进模板，再为用户分配 Profile 与 Scope。

## 推荐管理流程

1. 应用发布时注册新的权限声明；
2. 在管理控制台的 Permission 页面确认权限目录；
3. 创建或修订 Permission Template；
4. 创建 Profile，并关联模板与 Scope；
5. 将 Profile 分配给目标用户，必要时提升其 authorization version 或撤销会话，使旧 token 不再继续使用旧授权状态。

常用只读管理接口包括 `GET /iam/admin/permissions`、`GET /iam/admin/templates` 与 `GET /iam/admin/profiles`。可写操作及其权限要求以项目 OpenAPI 为准。

:::caution[不要直接写表]
不要直接写入 `iam_*` 表、MyBatis mapper 或 Redis Key。直接写入会绕过授权版本、审计和兼容性边界；始终通过 Management API、管理控制台或已公开的应用 SPI 操作。
:::

参见[管理控制台](/management/console/)和[定义权限](/getting-started/define-permissions/)。
