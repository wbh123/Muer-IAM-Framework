---
title: MVC 资源描述解析器
description: 说明 MvcResourceDescriptorResolver 如何从请求解析出 ResourceDescriptor。
sidebar:
  order: 3
---

## 解决什么问题

授权引擎需要知道「当前请求访问的是哪个业务资源」。解析器把 `HttpServletRequest` + `HandlerMethod` 翻译成 `ResourceDescriptor`，由宿主把业务资源映射到 IAM 的资源模型。

## 关键概念

```java
Optional<ResourceDescriptor> resolve(HttpServletRequest request,
                                     HandlerMethod handlerMethod);
```

- 返回 `Optional<ResourceDescriptor>`：解析不到业务资源时为空。
- **IAM 不查询文档表**。宿主负责把业务主键映射到 `ResourceDescriptor`，IAM 仅消费结果。

## 示例：文档 1001

`GET /api/documents/1001` 经解析器后变为：

```
ResourceDescriptor(
  resourceType = "DOCUMENT",
  resourceId   = "1001",
  parentPath   = ["PROJECT:101", "DEPARTMENT:1"],
  attributes   = {}
)
```

即文档 1001 属于 PROJECT 101、DEPARTMENT 1。这样引擎可结合 `ResourceScope("PROJECT","101",READ)` 判定是否在作用域内。

## 失败语义

- 无 resolver → 500 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`
- resolver 返回 empty → 404 `IAM_RESOURCE_NOT_FOUND`

## 源码

<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/web/MvcResourceDescriptorResolver.java>
