---
title: 权限（Permission）
description: 理解 permission 代码如何驱动授权决策，以及拒绝时返回的权威 decisionCode。
sidebar:
  order: 2
---

## 它解决什么问题

「能不能做这件事」由 permission 表达。IAM 把 permission 从角色中解耦出来，让授权决策可解释、可诊断，而不是一堆 `if (role == ...)`。

## Permission 是什么

permission 是一个字符串代码，例如 `document:read`、`document:update`。它在 `PermissionTemplateVersion` 中被收集为一组权限集合，再经由 Profile 授予用户。判定入口是 `AuthorizationEngine`：

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
// 拒绝时抛 AuthorizationDeniedException
default void require(IamPrincipal principal, AuthorizationRequest request)
```

`AuthorizationRequest` 是一个 record：`(permissionCode, domain, clientType, resource, scopeAccess)`。决策结果为 `AuthorizationDecision(allowed, decisionCode, steps)`，其中 `steps` 为 `List<AuthorizationDecisionStep(code, passed, reason)>`，解释每一步为何通过或失败。

## 决策码（真实）

成功为 `ALLOWED`；拒绝可能为：

- `PERMISSION_DENIED`：未授予该 permission；
- `SCOPE_DENIED`：permission 已授予，但资源不在授权 scope 内；
- `IDENTITY_DOMAIN_MISMATCH` / `CLIENT_TYPE_MISMATCH`：身份域或客户端类型不匹配。

## 示例：诊断一次写入

```bash
curl -X POST "$BASE/iam/authorization/diagnostics" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{
    "permissionCode":"document:update",
    "domain":"EXAMPLE",
    "clientType":"WEB",
    "resourceType":"PROJECT",
    "resourceId":"101",
    "scopeAccess":"WRITE"
  }'
# allowed=false, decisionCode=PERMISSION_DENIED
```

## 下一步

permission 由 [权限模板](/concepts/permission-template/) 组织，并在 [Profile](/concepts/profile/) 中授予；scope 约束见 [资源范围](/concepts/resource-scope/)。
