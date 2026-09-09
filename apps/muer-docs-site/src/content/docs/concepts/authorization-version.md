---
title: 权限变更与旧 Token
description: 理解 IamPrincipal 上的 authorizationVersion 字段如何用于授权缓存一致性，以及管理员如何提升它。
sidebar:
  order: 7
---

## 它解决什么问题

当用户的授权（permission、Profile、scope）在服务端被管理员修改后，已签发的 principal 可能携带过期的授权视图。IAM 用 `authorizationVersion` 作为一致性标记，便于缓存或拦截层判断是否需要重新评估。

## 字段定义（真实）

`authorizationVersion` 是 `IamPrincipal` 的最后一个组件，类型为整数，校验约束为 **`>= 0`**（源码 [IamPrincipal.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/model/IamPrincipal.java)）：

```
userId, identityId, identityDomain, activeProfileId(Long),
templateVersionId(Long), clientType, authorizationVersion
```

演示账号 alice 登录后该值为 `0`（源码 [ExampleIdentityAdapter](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-example/src/main/java/cloud/muer/example/ExampleIdentityAdapter.java)）。

## 管理端点

管理员可主动为该用户提升授权版本，触发重新评估：

```
POST /iam/admin/users/{userId}/authorization-version
```

该管理接口受对应的 `iam.admin.*` permission 授权检查（见 [OpenAPI](https://github.com/wbh123/Muer-IAM-Framework/blob/main/contracts/openapi/iam.yaml)）。

## 与缓存的关系

`authorizationVersion` 本身不改变决策逻辑，而是作为「授权快照版本」随 principal 流转；宿主或拦截层可据此使本地授权缓存失效。当前 starter 不自动按此做条件化缓存，文档按真实行为说明。

## 下一步

结合 [Identity 与 Principal](/concepts/identity-principal/) 与 [Profile](/concepts/profile/) 理解运行时授权来源。
