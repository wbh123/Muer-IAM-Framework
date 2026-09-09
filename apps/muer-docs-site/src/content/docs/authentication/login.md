---
title: 登录
description: 通过 POST /iam/auth/login 完成身份认证并获取 IAM Token 与 Session。
sidebar:
  order: 1
---

## 解决什么问题

应用需要把“宿主凭据 + Client Type”转换为可信的 `IamPrincipal`，并得到后续请求使用的不透明 Token。登录是 IAM 认证链的起点。

## 关键概念

- **LoginRequest**：必填 `username`、`password`、`clientType`；还可携带 `clientInstance` 等审计元数据；
- **AuthenticationResult**：`(accessToken, sessionId, expiresAt, principal)`；
- **流程**：`POST /iam/auth/login` → IAM Client Type 校验 → `IdentityAuthenticator` → 宿主身份服务 → `IamPrincipal` → Token / Session。

## 请求接口

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/auth/login` |
| Bearer Token | 不需要 |
| 成功 | HTTP `200` |
| 失败 | HTTP `401` |

请求体示例：

```json
{
  "username": "alice",
  "password": "demo-pass",
  "clientType": "WEB"
}
```

## 预期结果

成功时返回：

- `accessToken`：后续请求使用的不透明 Token；
- `sessionId`：当前 Session ID；
- `expiresAt`：Token 过期时间；
- `principal`：当前 `IamPrincipal`。

后续受保护请求携带：

```text
Authorization: Bearer <accessToken>
```

`alice / demo-pass` 只是 `muer-example` 的演示身份。生产系统继续使用自己的用户表、密码校验或企业身份源。

## 源码

- `LoginRequest`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/LoginRequest.java>
- `IdentityAuthenticator`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/IdentityAuthenticator.java>
- OpenAPI：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/contracts/openapi/iam.yaml>
