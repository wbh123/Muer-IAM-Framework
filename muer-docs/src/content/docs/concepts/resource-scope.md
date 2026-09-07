---
title: 资源范围（Resource Scope）
description: 理解 ResourceScope、ResourceDescriptor 与 ResourceHierarchyProvider 如何把授权约束到具体资源空间。
sidebar:
  order: 5
---

## 它解决什么问题

permission 回答「能做什么」，scope 回答「能在哪些资源上做」。仅有 permission 不够——reader 能读文档，但不应读其他项目的文档。scope 把授权限定到资源空间。

## 核心类型

```java
// 资源描述：被访问的资源及其父路径
record ResourceDescriptor(
    resourceType,
    resourceId,
    parentPath,    // List<String>，形如 ["PROJECT:101","DEPARTMENT:1"]
    attributes    // Map<String,String>
)

// 授权范围：允许访问的资源空间与访问模式
record ResourceScope(
    scopeType,     // 如 "PROJECT"
    scopeRefId,    // 如 "101"
    accessMode     // ScopeAccess
)

enum ScopeAccess { READ, WRITE }
```

判断资源是否落在 scope 内由宿主实现：

```java
interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
```

源码见 [ResourceHierarchyProvider.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/port/ResourceHierarchyProvider.java) 与 [模型目录](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/model/)。

## 示例：项目内只读

reader profile 的 scope 为 `(PROJECT,101,READ)`。文档 `1001` 解析为 `ResourceDescriptor("DOCUMENT", "1001", parentPath=["PROJECT:101","DEPARTMENT:1"])`，落在 scope 内 → 允许；文档 `2001`（`PROJECT:202`）不在 scope 内 → `SCOPE_DENIED`（403）。

## 与 MVC 集成

用 `@RequirePermission` 时，由 `MvcResourceDescriptorResolver` 把请求解析为 `ResourceDescriptor`，详见 [MvcResourceDescriptorResolver](/authorization/mvc-resource-descriptor-resolver/)。

## 下一步

回到 [权限](/concepts/permission/) 看决策码，或阅读 [Session](/concepts/session/)。
