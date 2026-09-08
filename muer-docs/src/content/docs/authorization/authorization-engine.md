---
title: 授权引擎
description: 说明 AuthorizationEngine 如何根据请求产出 AuthorizationDecision 决策结果。
sidebar:
  order: 5
---

## 解决什么问题

所有「是否放行」的判断都集中在 `AuthorizationEngine`，保证注解、拦截器、自定义调用走同一套决策逻辑与可审计结果。

## 关键概念

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
default void require(IamPrincipal principal, AuthorizationRequest request) // 拒绝时抛 AuthorizationDeniedException
```

- **AuthorizationRequest**：`(permissionCode, domain, clientType, resource, scopeAccess)`。
- **AuthorizationDecision**（record）：`(allowed, decisionCode, steps)`。
- **AuthorizationDecisionStep**（record）：`(code, passed, reason)`，记录每一步判定。

## 决策代码（真实）

| 代码 | 含义 |
| --- | --- |
| `ALLOWED` | 允许 |
| `PERMISSION_DENIED` | 权限不足 |
| `SCOPE_DENIED` | 超出资源作用域 |
| `IDENTITY_DOMAIN_MISMATCH` | 身份域不匹配 |
| `CLIENT_TYPE_MISMATCH` | 客户端类型不匹配 |

## 源码

<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/>

相关：见 [authorization-policy](/authorization/authorization-policy/) 与 [resource-scope](/authorization/resource-scope/)。
