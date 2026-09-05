---
title: 当前用户
description: 说明如何通过 GET /iam/auth/me 获取已登录用户的 IamPrincipal 信息。
sidebar:
  order: 4
---

## 解决什么问题

登录后，前端或下游服务常需「我是谁、当前激活了哪个 Profile、授权版本是多少」。IAM 提供 `GET /iam/auth/me` 直接返回当前令牌对应的主体信息。

## 关键概念

- **IamPrincipal**（record）组件顺序：`userId, identityId, identityDomain, activeProfileId(Long), templateVersionId(Long), clientType, authorizationVersion`。
- 校验规则：`userId>0`；`identityId/identityDomain/clientType` 非空；`activeProfileId/templateVersionId>0`；`authorizationVersion>=0`。
- 通过 `IamBearerTokenFilter` 从 opaque token 解析出 `TokenRecord(sessionId, principal, expiresAt)`，从而拿到当前用户。

## 示例

```bash
curl https://iam.example.com/iam/auth/me \
  -H 'Authorization: Bearer <accessToken>'
```

返回 `principal` 必填：`userId,identityId,identityDomain,clientType,authorizationVersion`；可空：`activeProfileId,templateVersionId`。

## 源码

- IamPrincipal：<https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java>
- IamBearerTokenFilter：<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/IamBearerTokenFilter.java>
