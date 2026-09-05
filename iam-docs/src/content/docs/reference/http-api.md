---
title: HTTP API
description: 基于 iam-management-web OpenAPI 的真实 HTTP 端点，按域组织。
sidebar:
  order: 3
---

所有端点前缀 `/iam`。受保护接口需 `Authorization: Bearer <accessToken>`。响应错误统一为 `application/problem+json`。

## Authentication

### POST /iam/auth/login
- **Auth**：无
- **Request**：`LoginRequest` `{username, password, clientType, clientInstance?}`
- **Response 200**：`LoginResponse {accessToken, sessionId, expiresAt, principal{userId,identityId,identityDomain,clientType,authorizationVersion,activeProfileId?,templateVersionId?}}`
- **Status**：200 / 401
- **curl**：
```bash
curl -X POST http://host/iam/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"demo-pass","clientType":"WEB"}'
```

### POST /iam/auth/logout
- **Auth**：Bearer
- **Status**：204 / 401

### GET /iam/auth/me
- **Auth**：Bearer
- **Response 200**：当前 `IamPrincipal`
- **Status**：200 / 401

## Session

### GET /iam/sessions
- **Auth**：Bearer
- **Response 200**：`{ items: [SessionResponse{sessionId,userId,clientType,loginAt,lastSeenAt,expiresAt}] }`
- **Status**：200 / 401

### POST /iam/sessions/{sessionId}/revoke
- **Auth**：Bearer
- **Status**：204 / 401 / 404

### POST /iam/sessions/revoke-others
- **Auth**：Bearer
- **Status**：204 / 401

## Profile / Authorization

### GET /iam/authorization/profiles
- **Auth**：Bearer
- **Response 200**：当前用户 `AuthorizationProfile` 列表
- **Status**：200

### POST /iam/authorization/profiles/{profileId}/switch
- **Auth**：Bearer
- **Response 200**：新 `AuthenticationResult`（新 Token）
- **Status**：200 / 401 / 404

### POST /iam/authorization/diagnostics
- **Auth**：Bearer
- **Response 200**：`AuthorizationDecision`（含 `steps`）
- **Status**：200

## 管理（需对应 `iam.admin.*` 权限）

| Method | Path | 说明 |
| --- | --- | --- |
| PUT | `/iam/admin/templates/{versionId}` | 更新模板版本 |
| GET | `/iam/admin/users` | 用户列表 |
| PUT | `/iam/admin/users/{userId}` | 更新用户 |
| GET | `/iam/admin/users/{userId}/identities` | 用户身份 |
| PUT | `/iam/admin/identities/{identityId}` | 更新身份 |
| PUT | `/iam/admin/profiles/{profileId}` | 更新 Profile |
| PUT | `/iam/admin/profiles/{profileId}/scopes` | 更新 Profile 范围 |
| POST | `/iam/admin/users/{userId}/authorization-version` | 升级授权版本 |
| POST | `/iam/admin/sessions/{sessionId}/revoke` | 撤销会话 |
| POST | `/iam/admin/users/{userId}/sessions/revoke` | 撤销用户全部会话 |

## 源码参考

- OpenAPI：<https://github.com/wbh123/iam/blob/main/iam-management-web/src/main/resources/openapi/iam.yaml>
