---
title: 常见问题
description: 关于 IAM 采用、概念与排错的真实一致性问答。
sidebar:
  order: 2
---

### IAM 要求自己的用户表吗？
不要求。IAM 通过 `IdentityAuthenticator.authenticate(LoginRequest)` 把宿主现有登录映射为 `IamPrincipal(userId, identityId, identityDomain, ...)`，用户数据仍由宿主持有。

### Role 和 Permission 区别是什么？
IAM 没有 Role 概念。权限单位是 `permissionCode`（如 `document:read`）。`AuthorizationProfile` 通过 `PermissionTemplateVersion` 聚合一组 permission，并用 `ResourceScope` 限定资源范围；"角色"由宿主在 `IdentityAuthenticator` 中自行映射。

### 为什么要 Redis？
Token 与 session 以 `TokenRecord(sessionId, principal, expiresAt)` 存于 Redis（`iam.token.redis-prefix` 默认 `iam`），支持分布式、快速失效与集中撤销（revoke）。

### 为什么要 MySQL？
持久化 principal/permission/template/profile/scope，以及审计与 Flyway schema 历史（`iam.schema.history-table` 默认 `iam_flyway_schema_history`）。

### 什么是 Profile？
`AuthorizationProfile`：`(profileId, userId, profileName, templateVersionId, clientTypes, enabled, revoked, scopes...)`，把一个用户的某套权限模板+资源范围绑定为可切换的身份视图。

### 为什么 Profile Switch 生成新 Token？
切换改变 `principal.activeProfileId` 与权限范围，`AuthenticationResult` 重新签发 `accessToken/sessionId`（见 `LoginResponse` 字段）。

### Scope 是什么？
`ResourceScope(scopeType, scopeRefId, accessMode)`，如 `("PROJECT","101",READ)`；`ScopeAccess=READ/WRITE`，限制 permission 在哪些资源上生效。

### @RequirePermission 和 AuthorizationEngine 如何选择？
注解用于 MVC 方法/类（拦截器取 method 优先于 class），声明式；`AuthorizationEngine.decide()/require()` 用于命令式/编程判断。简单 CRUD 用注解，复杂逻辑用 engine。

### 为什么资源不存在返回 404？
resolver 返回 empty → 404 `IAM_RESOURCE_NOT_FOUND`（`RESOURCE_NOT_FOUND`），表示资源在宿主中不存在，而非无权限。

### 为什么没有 Resolver 返回 500？
无 resource resolver → 500 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`（`RESOURCE_RESOLUTION_UNAVAILABLE`），表示授权所需资源解析能力未配置。

### 怎么接入已有系统？
实现 `IdentityAuthenticator` 映射现有登录为 `IamPrincipal`；可选投影 profiles/scopes（[数据投影](/migration/data-projection/)）。

### 怎么撤销 Session？
`POST /iam/sessions/{sessionId}/revoke`（204/401/404）；撤销其他 `POST /iam/sessions/revoke-others`；admin：`POST /iam/admin/sessions/{sessionId}/revoke`、`POST /iam/admin/users/{userId}/sessions/revoke`。

### 怎么诊断 403？
`POST /iam/authorization/diagnostics` 返回 `AuthorizationDecision`（含 `steps`：`code/passed/reason`），据此区分 `SCOPE_DENIED`/`PERMISSION_DENIED` 等。

### IAM 会不会接管宿主所有 Spring Security？
不会。`iam.enabled=false` 可整体关闭；IAM 仅通过自身拦截器与 Bearer 过滤器参与，不替换宿主其它 Security 配置。
