---
title: 当前用户
description: 通过 GET /iam/auth/me 获取当前 Token 对应的 IamPrincipal。
sidebar:
  order: 4
---

## 解决什么问题

登录后，前端或下游服务经常需要知道“我是谁、当前激活了哪个 Profile、授权版本是多少”。IAM 提供 `GET /iam/auth/me` 直接返回当前 Token 对应的 Principal。

## 关键概念

`IamPrincipal` 包含：

```text
userId
identityId
identityDomain
activeProfileId
templateVersionId
clientType
authorizationVersion
```

`IamBearerTokenFilter` 从不透明 Token 解析 `TokenRecord(sessionId, principal, expiresAt)`，再恢复当前 Principal。

## 请求接口

| 项目 | 内容 |
| --- | --- |
| Method | `GET` |
| Path | `/iam/auth/me` |
| Auth | `Authorization: Bearer <accessToken>` |
| 成功 | HTTP `200` |
| Token 无效 | HTTP `401` |

## 预期结果

成功时返回当前 `IamPrincipal`。其中：

- `userId`、`identityId`、`identityDomain`、`clientType`、`authorizationVersion` 为核心字段；
- `activeProfileId`、`templateVersionId` 在对应授权上下文存在时返回。

如果刚完成 QuickStart 登录，能通过这个接口读取到正确 Principal，就说明基础 Token 解析链路已经接通。

## 源码

- `IamPrincipal`：<https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java>
- `IamBearerTokenFilter`：<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamBearerTokenFilter.java>
