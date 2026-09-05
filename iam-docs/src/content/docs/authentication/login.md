---
title: 登录
description: 说明如何通过 POST /iam/auth/login 完成身份认证并获取 IAM 令牌与会话。
sidebar:
  order: 1
---

## 解决什么问题

应用需要把「用户名 + 密码 + 客户端类型」转换为可信的 `IamPrincipal`，并拿到可用于后续请求的访问令牌。`login` 是 IAM 认证链的起点。

## 关键概念

- **LoginRequest**：record，必填 `username`、`password`、`clientType`；便捷构造器 `(username,password,clientType)` 与 `(username,password,clientType,clientInstance)`。完整组件另含 `clientInstance`、`ipAddress`、`userAgent`、`deviceType`、`osName`、`browserName`、`appVersion`、`requestId`。
- **AuthenticationResult**：record `(accessToken, sessionId, expiresAt, principal)`，是登录与切换身份的返回。
- **登录流程**：`POST /iam/auth/login` → IAM → `IdentityAuthenticator` → 宿主用户服务 → `IamPrincipal` → IAM 令牌 / 会话。

## 示例

```bash
curl -X POST https://iam.example.com/iam/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"demo-pass","clientType":"WEB"}'
```

返回 `LoginResponse` 必填：`accessToken`、`sessionId`、`expiresAt`、`principal`；`principal` 必填 `userId,identityId,identityDomain,clientType,authorizationVersion`，可空 `activeProfileId,templateVersionId`。

## 源码

- LoginRequest / AuthenticationResult：<https://github.com/wbh123/iam/blob/main/iam-authentication/src/main/java/io/github/iamstarter/authentication/LoginRequest.java>
- 登录端点：<https://github.com/wbh123/iam/blob/main/iam-management-web/src/main/resources/openapi/iam.yaml>
