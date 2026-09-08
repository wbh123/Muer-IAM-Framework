---
title: 从零接入 Muer
description: 逐步理解一次完整的 Muer 身份认证、权限注册与资源授权接入。
sidebar:
  order: 2
---

如果你第一次只想跑通接口，请先看[10~15 分钟快速开始](/getting-started/quick-start/)。本页用于在成功运行之后理解每个接入部件的职责。

## 接入路径

```text
DemoAccount
  ↓
IdentityAuthenticator
  ↓
PermissionDefinitionProvider
  ↓
DocumentResourceResolver
  ↓
ResourceHierarchyProvider
  ↓
Muer Security Filter Chain
```

### 1. 身份认证

宿主实现 `IdentityAuthenticator`，校验自己的账号体系并返回包含有效
`activeProfileId`、`templateVersionId` 和 `clientType` 的 `IamPrincipal`。Muer 不拥有密码，也不替宿主保存账号。

### 2. 注册权限

通过 `PermissionDefinitionProvider` 注册 `document:read`、`document:update` 等业务权限。注册只建立权限目录，不会自动创建 Template、Version 或 Profile。

### 3. 保护接口

在 Controller 上声明权限与资源描述，Muer 根据当前 Principal、Template Version 和 Scope 执行确定性的授权判断：允许返回业务响应，缺少权限或范围时返回 `403`。

### 4. 管理授权投影

管理员按“Permission → Template → Draft Version → Publish → Profile → Scope”的顺序维护授权结构。已发布 Version 不可原地修改；Profile 或 Scope 变更会提升用户的 `authorizationVersion`，旧 Token 应按宿主策略重新签发。

完成本页后，可回到 Quick Start 的 HTTP 示例验证 `GET /api/documents/1001` 返回 `200`，而没有相应权限的写请求返回 `403`。
