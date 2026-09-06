---
title: 授权模型
description: 概述 IAM 授权模型的核心要素：主体、权限、资源与作用域。
sidebar:
  order: 1
---

## 解决什么问题

IAM 需要回答「这个用户，对这笔业务资源，能否以某种方式访问」。授权模型把这些概念标准化，使决策可声明、可审计、可复用。

## 关键概念

- **IamPrincipal**：谁在访问（`userId`、身份域、激活 Profile、授权版本）。
- **Permission**：权限码，如 `document:read`、`document:update`。
- **ResourceDescriptor**：资源描述，`(resourceType, resourceId, parentPath, attributes)`；`parentPath` 形如 `["PROJECT:101","DEPARTMENT:1"]`，表达资源在层级中的位置。
- **ResourceScope**：授权作用域，`(scopeType, scopeRefId, accessMode)`，如 `("PROJECT","101",READ)`。
- **ScopeAccess**：枚举 `READ` / `WRITE`。
- **AuthorizationRequest**：决策请求，`(permissionCode, domain, clientType, resource, scopeAccess)`。

## 决策位置

声明（注解）与决策（引擎）分离：见 [require-permission](/authorization/require-permission/) 与 [authorization-engine](/authorization/authorization-engine/)。

## 源码

- ResourceDescriptor / ResourceScope：<https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/iamstarter/core/model/>
- IamPrincipal：<https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java>
