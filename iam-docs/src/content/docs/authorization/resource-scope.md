---
title: 资源作用域
description: 说明 ResourceScope 与 ResourceHierarchyProvider 如何界定授权覆盖范围。
sidebar:
  order: 4
---

## 解决什么问题

权限码只能表达「能做什么」，而作用域表达「能在哪些资源上做」。IAM 用 `ResourceScope` 把授权限定到某个项目、部门等层级节点。

## 关键概念

- **ResourceScope**（record）：`(scopeType, scopeRefId, accessMode)`，例如 `("PROJECT","101",READ)` 表示对 PROJECT 101 具有读权限。
- **ScopeAccess**：枚举 `READ` / `WRITE`。
- **ResourceHierarchyProvider**（接口）：`boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);`——判断某资源是否落在给定作用域内（沿 `parentPath` 计算层级包含关系）。

## 示例

alice 默认 Profile 401 持有作用域 `(PROJECT,101,READ)`；其文档 1001 的 `parentPath` 含 `PROJECT:101`，因此 `isWithinScope` 为 true，可读。当以 WRITE 访问时，因 401 仅有 READ，被拒并得 `SCOPE_DENIED`。切换到 Profile 402（含 `PROJECT,101,WRITE`）后即可写。

## 源码

- ResourceScope：<https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/model/>
- ResourceHierarchyProvider：<https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/port/ResourceHierarchyProvider.java>
