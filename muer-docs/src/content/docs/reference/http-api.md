---
title: HTTP API
description: 基于 muer-management-web OpenAPI 的真实 HTTP 端点，按请求方式、认证要求和预期结果组织。
sidebar:
  order: 3
---

所有 IAM 管理端点使用 `/iam` 前缀。受保护接口需要：

```text
Authorization: Bearer <accessToken>
```

错误响应默认使用 `application/problem+json`。

文档以“接口 + 请求方式 + 请求体 + 预期结果”为主，你可以使用 Postman、Apifox、IDE HTTP Client 或自己的前端调用这些接口。

## Authentication

### 登录

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/auth/login` |
| Auth | 无 |
| Success | `200` |
| Failure | `401` |

请求体：

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

成功响应包含：

```text
accessToken
sessionId
expiresAt
principal
```

### 登出

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/auth/logout` |
| Auth | Bearer |
| Success | `204` |
| Failure | `401` |

### 当前 Principal

| 项目 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/iam/auth/me` |
| Auth | Bearer |
| Success | `200`，返回当前 `IamPrincipal` |
| Failure | `401` |

## Session

### 当前用户 Session 列表

| 项目 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/iam/sessions` |
| Auth | Bearer |
| Success | `200`，返回当前用户 Session 列表 |
| Failure | `401` |

### 撤销指定 Session

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/sessions/{sessionId}/revoke` |
| Auth | Bearer |
| Success | `204` |
| Failure | `401` / `404` |

成功撤销后，该 Session 对应 Token 再访问受保护资源应得到 `401`。

### 撤销其他 Session

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/sessions/revoke-others` |
| Auth | Bearer |
| Success | `204` |
| Failure | `401` |

## Profile / Authorization

### 查询可用 Profile

| 项目 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/iam/authorization/profiles` |
| Auth | Bearer |
| Success | `200`，返回当前用户可用 `AuthorizationProfile` 列表 |

### 切换 Profile

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/profiles/{profileId}/switch` |
| Auth | Bearer |
| Success | `200`，返回新的 `AuthenticationResult` |
| Failure | `401` / `404` |

成功结果包含新的 Token 和 Session；原 Token 不会被修改成新 Profile。

### 授权诊断

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/diagnostics` |
| Auth | Bearer |
| Success | `200`，返回真实 `AuthorizationDecision` |

示例请求体：

```json
{
  "permissionCode": "document:update",
  "domain": "EXAMPLE",
  "clientType": "WEB",
  "resourceType": "PROJECT",
  "resourceId": "101",
  "scopeAccess": "WRITE"
}
```

## MVC 业务接口的默认授权失败

对于使用 `@RequirePermission` 的宿主 MVC 接口，默认语义为：

| HTTP | Code | 含义 |
| --- | --- | --- |
| `401` | `IAM_UNAUTHENTICATED` | 没有有效 Principal |
| `403` | `IAM_ACCESS_DENIED` | Permission / Scope 等授权条件不满足 |
| `404` | `IAM_RESOURCE_NOT_FOUND` | 宿主 Resolver 确认业务资源不存在 |
| `500` | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | 接口需要资源解析，但宿主未提供可用 Resolver |

## 管理端点

管理接口需要对应的 `iam.admin.*` 权限。

| Method | Path | 说明 |
| --- | --- | --- |
| `PUT` | `/iam/admin/templates/{versionId}` | 更新模板版本 |
| `GET` | `/iam/admin/users` | 用户列表 |
| `PUT` | `/iam/admin/users/{userId}` | 更新用户 |
| `GET` | `/iam/admin/users/{userId}/identities` | 用户身份 |
| `PUT` | `/iam/admin/identities/{identityId}` | 更新身份 |
| `PUT` | `/iam/admin/profiles/{profileId}` | 更新 Profile |
| `PUT` | `/iam/admin/profiles/{profileId}/scopes` | 更新 Profile 范围 |
| `POST` | `/iam/admin/users/{userId}/authorization-version` | 升级授权版本 |
| `POST` | `/iam/admin/sessions/{sessionId}/revoke` | 撤销 Session |
| `POST` | `/iam/admin/users/{userId}/sessions/revoke` | 撤销用户全部 Session |

## 使用建议

QuickStart 阶段只需要重点关注：

1. `POST /iam/auth/login`；
2. `GET /iam/auth/me`；
3. 一个受 `@RequirePermission` 保护的宿主业务接口。

Profile Switch、Session Revoke 与 Diagnostics 可以在业务确实需要时再接入，不要求部署者为了安装 IAM 完整调用一遍所有接口。

## 源码参考

- OpenAPI：<https://github.com/wbh123/iam/blob/main/muer-management-web/src/main/resources/openapi/iam.yaml>
