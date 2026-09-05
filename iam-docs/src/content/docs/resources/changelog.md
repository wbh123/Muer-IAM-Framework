---
title: 更新日志
description: 0.1.0-SNAPSHOT 各里程碑的能力变更记录。
sidebar:
  order: 4
---

## 0.1.0-SNAPSHOT（Release Candidate）

### 认证
- 新增 `IdentityAuthenticator` 函数式接口，支持宿主投影现有用户为 `IamPrincipal`。
- 新增 `LoginRequest`/`AuthenticationResult`，登录/切换统一返回 `accessToken/sessionId/principal`。
- 新增 Opaque Token 与 `IamBearerTokenFilter`。

### 授权
- 新增 `AuthorizationEngine.decide/require`，返回 `AuthorizationDecision(allowed, decisionCode, steps)`。
- 新增 `PermissionTemplateVersion`（DRAFT/PUBLISHED/RETIRED）与 `AuthorizationProfile`。
- 新增 `ResourceScope`/`ResourceDescriptor`/`ResourceHierarchyProvider` 资源范围判定。
- 新增 `@RequirePermission` 与 `MvcResourceDescriptorResolver`、拦截器（method 优先于 class）。

### 会话
- 新增 `AuthSession`/`TokenRecord`，支持 `revoke/touch` 与续期。

### 诊断与管理
- 新增 `POST /iam/authorization/diagnostics` 决策回放。
- 新增 `/iam/admin/**` 管理端点（受 `iam.admin.*` 保护）。

### 文档
- 新增 Astro+Starlight 文档站与迁移/参考/资源分组。

> 版本仍为 `0.1.0-SNAPSHOT`，尚未正式发布。
