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

成功响应（HTTP `200`，`Content-Type: application/json`）的字段由 `LoginResponse` 定义：`accessToken`、`sessionId`、`expiresAt`、`principal` 均为必填；`principal.activeProfileId` 与 `principal.templateVersionId` 为可空字段——用户尚未绑定任何 Profile 时为空。下列仅为结构示例，Token 与时间戳是动态值：

```json
{
  "accessToken": "opaque-token-value-…",
  "sessionId": "sess_8f3a9c2e6b1d4a07",
  "expiresAt": "2026-09-08T15:50:00Z",
  "principal": {
    "userId": 101,
    "identityId": "alice",
    "identityDomain": "EXAMPLE",
    "activeProfileId": 401,
    "templateVersionId": 301,
    "clientType": "WEB",
    "authorizationVersion": 3
  }
}
```

调用受保护接口时把 `accessToken` 放入 `Authorization: Bearer <accessToken>`。该 Token 是不透明字符串，不解析内容即可使用。

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

返回体是 `PrincipalResponse`。它与 `LoginResponse.principal` 是同一结构：

```json
{
  "userId": 101,
  "identityId": "alice",
  "identityDomain": "EXAMPLE",
  "activeProfileId": 401,
  "templateVersionId": 301,
  "clientType": "WEB",
  "authorizationVersion": 3
}
```

`userId`、`identityId`、`identityDomain`、`clientType`、`authorizationVersion` 始终返回；`activeProfileId`、`templateVersionId` 在用户已绑定并激活授权 Profile 时返回整数值，否则为空。

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

返回体是 `AuthorizationProfileResponse` 的 JSON 数组：

```json
[
  {
    "profileId": 401,
    "userId": 101,
    "profileName": "Project-101 Reader",
    "templateVersionId": 301,
    "clientTypes": ["WEB"],
    "enabled": true,
    "revoked": false,
    "validFrom": null,
    "validUntil": null,
    "scopes": [
      { "scopeType": "PROJECT", "scopeRefId": "101", "accessMode": "READ" }
    ]
  }
]
```

### 切换 Profile

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/profiles/{profileId}/switch` |
| Auth | Bearer |
| Success | `200`，返回新的 `AuthenticationResult` |
| Failure | `401` / `404` |

成功响应（HTTP `200`）与 `LoginResponse` 同构：返回一份携带新激活 Profile 的 Token、Session 与 Principal。原 Token 不会被修改成新 Profile，因此旧 Token 继续以切换前的 Profile 生效：

```json
{
  "accessToken": "opaque-token-after-switch-…",
  "sessionId": "sess_c0b1a6f3e8d2a09c",
  "expiresAt": "2026-09-08T16:00:00Z",
  "principal": {
    "userId": 101,
    "identityId": "alice",
    "identityDomain": "EXAMPLE",
    "activeProfileId": 402,
    "templateVersionId": 302,
    "clientType": "WEB",
    "authorizationVersion": 3
  }
}
```

若 `profileId` 不属于当前 Principal，返回 `404`。

### 授权诊断

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/diagnostics` |
| Auth | Bearer |
| Success | `200`，返回真实 `AuthorizationDecision` |
| Failure | `401` |

该接口是当前已认证 Principal 的**只读自诊断**：只能评估自己，不能指定其他用户或 Profile，也不需要 `iam.admin.*` 权限。

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

成功响应（HTTP `200`）返回 `AuthorizationDecisionResponse`：`allowed`、`decisionCode` 与已检查步骤 `steps`。例如 Reader 缺少 `document:update` 权限时，`DefaultAuthorizationEngine` 依次记录已通过的步骤，在权限校验处终止并返回 `PERMISSION_DENIED`：

```json
{
  "allowed": false,
  "decisionCode": "PERMISSION_DENIED",
  "steps": [
    { "code": "IDENTITY_DOMAIN", "passed": true, "reason": "domain matches" },
    { "code": "CLIENT_TYPE", "passed": true, "reason": "client matches" },
    { "code": "ACTIVE_PROFILE", "passed": true, "reason": "profile valid" },
    { "code": "ATOMIC_PERMISSION", "passed": false, "reason": "permission missing" }
  ]
}
```

`steps[].code` 是引擎内部固定步骤标识（`IDENTITY_DOMAIN` / `CLIENT_TYPE` / `ACTIVE_PROFILE` / `ATOMIC_PERMISSION` / `RESOURCE_SCOPE`，及扩展策略的 `POLICY:<code>`）；`decisionCode` 才是对外稳定的拒绝码。具体取值与含义见[错误码](/reference/error-codes/)。

## MVC 业务接口的默认授权失败

对于使用 `@RequirePermission` 的宿主 MVC 接口，默认语义为：

| HTTP | Code | 含义 |
| --- | --- | --- |
| `401` | `IAM_UNAUTHENTICATED` | 没有有效 Principal |
| `403` | `IAM_ACCESS_DENIED` | Permission / Scope 等授权条件不满足 |
| `404` | `IAM_RESOURCE_NOT_FOUND` | 宿主 Resolver 确认业务资源不存在 |
| `500` | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | 接口需要资源解析，但宿主未提供可用 Resolver |

失败响应由 `ProblemDetailIamAuthorizationFailureHandler` 写出，`Content-Type: application/problem+json`，体为 RFC 9457 `ProblemDetail` 并附 `code` 与 `path` 两个自定义字段。例如 Reader 访问需要 `document:update` 的接口：

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "The request could not be authorized.",
  "code": "IAM_ACCESS_DENIED",
  "path": "/api/documents/1001"
}
```

其中 `detail` 是安全的通用文案（默认处理不泄露内部原因）；真正可用于程序判断的是 `code`。`title` 由 HTTP 状态决定。若需把 `code` 映射为业务错误体或补充本地化文案，见[自定义错误处理](/diagnostics/custom-error-handling/)。

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

- OpenAPI：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-management-web/src/main/resources/openapi/iam.yaml>
