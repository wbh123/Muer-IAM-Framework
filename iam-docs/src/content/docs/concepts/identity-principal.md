---
title: Identity 与 Principal
description: 理解 IAM 如何把宿主用户投影为运行时的 IamPrincipal，以及它的字段含义与校验规则。
sidebar:
  order: 1
---

## 它解决什么问题

授权决策需要「当前是谁」的统一答案。IAM 不持有宿主用户表，而是通过宿主的 `IdentityAuthenticator` 把凭据投影成一个不可变的 `IamPrincipal`，作为后续所有决策的输入。

## IamPrincipal 是什么

`IamPrincipal` 是一个 Java `record`，组件顺序固定如下（源码 [IamPrincipal.java](https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java)）：

```
userId, identityId, identityDomain, activeProfileId(Long),
templateVersionId(Long), clientType, authorizationVersion
```

| 字段 | 含义 |
| --- | --- |
| `userId` | 宿主侧用户标识 |
| `identityId` / `identityDomain` | 身份标识与身份域（用于跨域校验） |
| `activeProfileId` | 当前激活的 Profile（可空） |
| `templateVersionId` | 激活 Profile 对应的模板版本（可空） |
| `clientType` | 登录客户端类型，须匹配 `iam.client-types` |
| `authorizationVersion` | 授权版本号，用于缓存一致性 |

## 校验规则（真实）

- `userId > 0`；
- `identityId` / `identityDomain` / `clientType` 非空；
- `activeProfileId` / `templateVersionId` 若非空则必须 `> 0`；
- `authorizationVersion >= 0`。

## 示例：登录返回的 principal

```json
{
  "userId": 101,
  "identityId": "app-user-101",
  "identityDomain": "EXAMPLE",
  "activeProfileId": 401,
  "templateVersionId": 301,
  "clientType": "WEB",
  "authorizationVersion": 0
}
```

这是 `AuthenticationResult` 的一部分，由登录与 Profile 切换返回。相关概念见 [Profile](/concepts/profile/) 与 [授权版本](/concepts/authorization-version/)。
