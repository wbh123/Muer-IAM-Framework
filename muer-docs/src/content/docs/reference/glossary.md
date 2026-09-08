---
title: 术语表
description: Identity、Principal、Permission、Template、Version、Profile、Scope、Session、Authorization Version 等概念的一句话定义与互相关系。
sidebar:
  order: 6
---

第一次接触 Muer 时，几个核心词容易被混用。这里按“从人到权限再到资源”的顺序给出一句话定义，并标注每项最终由谁决定。

## 身份与主体

| 术语 | 一句话解释 | 谁负责 |
| --- | --- | --- |
| **Identity（身份）** | 宿主用户体系中一个可登录的主体。Muer 不持有你的账号、密码，只通过 `IdentityAuthenticator` 在登录时把它投影为 Principal。 | 宿主持有 |
| **Principal（主体 / IamPrincipal）** | 一次已认证请求所代表的主体投影，携带 `userId`、`identityId`、`identityDomain`、`activeProfileId`、`templateVersionId`、`clientType`、`authorizationVersion` 等字段。 | `IdentityAuthenticator` 生成，Muer 校验 |
| **identityDomain（身份域）** | 主体所属域（如 `LOCAL`、`SSO`），用于把一个框架/租户的授权语义与其他域隔离。请求 `domain` 与它不匹配会得到 `IDENTITY_DOMAIN_MISMATCH`。 | 宿主声明 |
| **clientType（客户端类型）** | 登录与请求携带的客户端类型（如 `WEB`）。必须与 `muer.client-types` 允许列表**精确匹配**，并进一步受 Profile 的 `clientTypes` 约束。 | 登录请求 + 配置 |

## 能力：Permission

| 术语 | 一句话解释 | 谁负责 |
| --- | --- | --- |
| **Permission（权限 / permissionCode）** | 系统里一个稳定的“业务能力”标识，如 `document:read`。开发者通过 `PermissionDefinitionProvider` 声明，Muer 幂等注册。 | 开发者声明 |
| **Role（角色）** | `cloud.muer.core.model.Role`，可选的权限分组元数据。它只把一组原子 Permission 聚合成有名字的集合，**不是**最终授权依据。 | 可选元数据 |

## 组织：Template / Version / Profile / Scope

| 术语 | 一句话解释 | 谁负责 |
| --- | --- | --- |
| **Permission Template（权限模板）** | 一组可复用权限的容器（如“文档读者”）。 | 管理员 |
| **Template Version（模板版本）** | 模板在某时刻的权限快照，状态为 `DRAFT` / `PUBLISHED` / `RETIRED`。**只有 `PUBLISHED` 版本才会实际授予 Profile**。 | 管理员 |
| **AuthorizationProfile（档案 / Profile）** | 一个用户身份的一个授权上下文：`templateVersionId` + `clientTypes` + `scopes` + 有效/撤销状态。Profile 引用的是“模板版本”而非“模板”。 | 管理员 |
| **ResourceScope（资源范围 / Scope）** | `(scopeType, scopeRefId, accessMode)`，如 `("PROJECT","101",READ)`，限定某 permission 在哪些资源上生效。“能做什么”是 Permission，“在哪里做”是 Scope。 | 管理员 / 宿主 |
| **ResourceDescriptor（资源描述符）** | 宿主把一个具体业务对象（如 Document 1001）映射成的授权输入，包含资源类型、ID 与父路径（parentPath）。 | 宿主 Resolver |

## 运行与安全

| 术语 | 一句话解释 | 谁负责 |
| --- | --- | --- |
| **Authorization Version（授权版本）** | `IamPrincipal.authorizationVersion`，参与令牌失效判断的版本号；递增它可让旧令牌对应的授权随之失效。 | Muer 运行时 |
| **Session（会话）** | 持久化授权上下文。MySQL 保存权威 Session 记录；Redis 只保存 opaque Token 到 Session 的索引。 | Muer |
| **AuthorizationDecision** | `AuthorizationEngine` 的决策结果：`allowed` + `decisionCode` + `steps`。拒绝时 `decisionCode` 给出根因（如 `PERMISSION_DENIED`、`SCOPE_DENIED`）。 | Muer |

## 一句话关系链

```text
宿主用户表
   └─ IdentityAuthenticator 投影 → IamPrincipal (userId/identityDomain/clientType/...)
PermissionDefinitionProvider 声明原子 Permission
管理员把 Permission 组进 Template → Version(PUBLISHED)
Profile = 用户身份 × 模板版本 × Scope × clientTypes
每次请求 → AuthorizationEngine 按 Permission + Scope 决策 → AuthorizationDecision
```

## 对照

| 词 | 它**不是**什么 |
| --- | --- |
| Identity | 不是 Muer 建的账号，而是宿主的用户 |
| Role | 不是最终授权依据（运行时仍按原子 Permission） |
| Profile | 不是 Role 的别名，而是“模板版本 + 资源范围”的授权上下文 |
| Scope | 不是权限本身，而是权限“在哪里”生效 |
| Redis | 不是授权权威，只是 opaque Token 索引（MySQL 才是持久权威） |

## 下一步

- 想知道每个类型在认证链路里的顺序：看[快速开始](/getting-started/quick-start/)。
- 想知道拒绝的每种原因：看[错误码](/reference/error-codes/)。
- 想排查一个 403：看[排障](/operations/troubleshooting/)。
