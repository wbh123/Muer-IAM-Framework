---
title: 声明权限需求
description: 说明 @RequirePermission 仅声明权限与访问方式，真实决策始终经由 AuthorizationEngine。
sidebar:
  order: 2
---

## 解决什么问题

在 MVC 控制器方法上声明「访问此接口需要什么权限」，让授权意图清晰可读，而把真正的允许/拒绝逻辑交给统一引擎处理。

## 关键概念

```java
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface RequirePermission {
    String value();                 // 权限码，如 "document:read"
    ScopeAccess access() default ScopeAccess.READ;
}
```

**要点**：

1. `@RequirePermission` **只声明**「需要的权限 + 访问方式」，**不执行**任何判断。真实决策**始终**经过 `AuthorizationEngine.decide(...)`。
2. **方法注解优先于类注解**：拦截器先取 method 上的注解，若不存在才取 class 上的。因此方法级注解可覆盖类级默认值。
3. 推荐优先在**方法**上标注，粒度更精确。

## 示例

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) { ... }

@PostMapping("/api/documents/{id}")
@RequirePermission(value = "document:update", access = WRITE)
public Document update(@PathVariable String id, @RequestBody DocumentUpdate u) { ... }
```

## 源码

<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/RequirePermission.java>

相关：见 [authorization-engine](/authorization/authorization-engine/) 与 [mvc-resource-descriptor-resolver](/authorization/mvc-resource-descriptor-resolver/)。
