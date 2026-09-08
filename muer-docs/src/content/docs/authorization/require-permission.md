---
title: 声明权限需求（@RequirePermission）
description: 说明 @RequirePermission 声明的不只是 Permission——它连同 Resolver 解析出的资源一起，交给引擎做 Permission + Resource + Scope 的联合判断。
sidebar:
  order: 2
---

## 本页完成后你会得到什么

- 能准确说出：`@RequirePermission("document:read")` 只表达“这个接口需要 `document:read` 这个能力”；
- 能纠正一个常见误解：**`@RequirePermission` ≠ 只查 Permission**；
- 知道真实决策是什么、由谁执行、失败时该看哪一环。

## 它只声明“需要什么”

在 MVC 控制器方法上放一个注解，把“访问此接口需要什么权限”写进代码，让意图清晰可读：

```java
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface RequirePermission {
    String value();                          // 权限码，如 "document:read"
    ScopeAccess access() default ScopeAccess.READ;  // 访问方式，默认只读
}
```

- `value` = 需要的 Permission 码（如 `document:read`）；
- `access` = 本次访问的读写方式，默认 `READ`，写操作要标 `WRITE`。

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) { ... }

@PostMapping("/api/documents/{id}")
@RequirePermission(value = "document:update", access = WRITE)
public Document update(@PathVariable String id, @RequestBody DocumentUpdate u) { ... }
```

## 关键认知：它不止是 Permission 检查

`@RequirePermission("document:read")` 在代码里**只写了一个权限码**，但它驱动的真实决策**不是只查 Permission**。

为什么？因为系统里可能有上千份文档。光有“能读文档”还不行，还得知道“读哪份文档”。所以当请求能解析出具体资源时（见[Resolver](/authorization/mvc-resource-descriptor-resolver/)），Muer 会继续做 Scope 判断。真实的判断是：

```text
Permission（value 声明的能力，如 document:read）
+ Resource（Resolver 解析出的当前资源，如 DOCUMENT/1001）
+ Scope   （该用户 Profile 是否覆盖此资源，且 access 匹配 READ/WRITE）
共同决定最终放行与否
```

也就是说：

```text
@RequirePermission ≠ 只有 Permission 检查
实际是 Permission + Resource + Scope 的联合判断
```

一个直观例子：两个接口都写 `@RequirePermission("document:read")`，但 Alice 只有 `PROJECT/101/READ` 的 Scope：

```text
GET /api/documents/1001   （属于 PROJECT 101） → 200
GET /api/documents/2001   （属于 PROJECT 202） → 403 SCOPE_DENIED
```

两个接口声明**完全一样**，结果却不同——因为决定权不在注解，而在引擎的 **Permission + Scope** 联合判断。注解只是“开口”。

## 真正的决策在哪里执行

`@RequirePermission` 本身**不执行任何判断**。它是一个“需求声明”，真正决策始终经过 `AuthorizationEngine.decide(...)`。在 Spring MVC 里，拦截器 `IamAuthorizationInterceptor` 读到注解后：

```text
读到 @RequirePermission(value, access)
    ↓
无登录主体            → 401
没有 Resolver 可用     → 500
Resolver 解析不到资源   → 404
engine.decide(...)     → 403（决策失败）或放行
```

方法注解优先于类注解（拦截器先取 method 上的，没有才取 class 上的）；推荐优先在方法上标注，粒度更精确。完整的引擎决策顺序见[授权引擎](/authorization/authorization-engine/)。

## 完成检查

```text
□ 我能说出 @RequirePermission 的两个参数：value 与 access
□ 我能说出注解 ≠ 只查 Permission，而是 Permission + Resource + Scope 联合判断
□ 我能解释：为什么两个声明一样的接口，对不同 Scope 的用户结果不同
□ 写操作接口已标 access = WRITE（否则即使有 document:update 也可能因 READ Scope 被拒）
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 改了注解里的权限码，接口行为却没变 | 确认 Bean 已重编译/重启；注解是方法优先于类，若方法没标会落到类级。 |
| 写接口只写了 `@RequirePermission("document:update")`，仍 403 | 默认 `access=READ`，而用户 Scope 是 `WRITE` 才放行。应显式 `access = WRITE`。 |
| 有权限码却仍 403 `SCOPE_DENIED` | 不是注解问题：该用户 Profile 的 Scope 没覆盖当前资源，或 access 不匹配。查[资源作用域](/authorization/resource-scope/)。 |
| 以为注解是安全边界、前端据此隐藏按钮 | 注解只声明需求；前端隐藏按钮不是安全边界，后端拦截器始终重新鉴权。 |

## 下一步

- [授权引擎](/authorization/authorization-engine/)——注解驱动的决策到底怎么一步步算出来。
- [MVC 资源描述解析器](/authorization/mvc-resource-descriptor-resolver/)——Resource 从哪来。
- [资源作用域](/authorization/resource-scope/)——Scope 怎么决定“能读哪些文档”。
