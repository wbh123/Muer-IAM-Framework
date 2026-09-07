---
title: 自定义错误处理
description: 理解 IAM 的 problem+json 失败响应结构，并在应用中定制异常映射。
sidebar:
  order: 3
---

## 解决的问题

默认授权拦截会抛出 `AuthorizationDeniedException` 等，由 Spring 转换为 `application/problem+json` 响应。应用常需把这些失败码映射成自己的业务错误体，或补充本地化文案。

## 关键概念

- 拦截决策顺序（`IamAuthorizationInterceptor`，真实）：先取 method 注解，否则取 class 注解（**Method 优先于 Class**），随后：
  1. 无 principal → `401 UNAUTHENTICATED`
  2. 无 resource resolver → `500 RESOURCE_RESOLUTION_UNAVAILABLE`
  3. resolver 返回 empty → `404 RESOURCE_NOT_FOUND`
  4. `engine.allowed()==false` → `403 ACCESS_DENIED`
- 默认 MVC 失败 code（ProblemDetail）：`401=IAM_UNAUTHENTICATED`、`403=IAM_ACCESS_DENIED`、`404=IAM_RESOURCE_NOT_FOUND`、`500=IAM_RESOURCE_RESOLUTION_UNAVAILABLE`。
- 拒绝由 `AuthorizationEngine.require(...)` 抛出 `AuthorizationDeniedException`（`decide` 返回 `AuthorizationDecision`）。

## 真实示例

拦截器默认响应头 `Content-Type: application/problem+json`，体如：

```json
{ "status": 403, "code": "IAM_ACCESS_DENIED", "detail": "scope denied" }
```

自定义 `@ControllerAdvice` 可捕获 `AuthorizationDeniedException` 改写响应。

## 源码

- `IamAuthorizationInterceptor`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/web/
- `AuthorizationEngine`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
