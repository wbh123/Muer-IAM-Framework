---
title: 自定义认证与授权错误响应
description: 理解 IAM 的 problem+json 失败响应结构，并在应用中定制异常映射。
sidebar:
  order: 3
---

## 解决的问题

IAM 有两种进入授权检查的路径，失败表现不同，应用做错误映射前需分清是哪一条：

- **声明式 MVC 路径（`@RequirePermission`）**：由 `IamAuthorizationInterceptor` 在进入 handler 前检查，失败时**直接**通过 `IamAuthorizationFailureHandler` 写出 `application/problem+json`，不抛异常、不走 Spring 异常转换。
- **编程式路径（业务代码调用 `AuthorizationEngine.require(...)`）**：拒绝时抛出 `AuthorizationDeniedException`（其 `decision()` 携带 `AuthorizationDecision`），需要你自己的异常处理去转换。

应用常需把这些失败码映射成自己的业务错误体，或补充本地化文案。

## 关键概念

- 拦截决策顺序（`IamAuthorizationInterceptor`，真实）：先取 method 注解，否则取 class 注解（**Method 优先于 Class**），随后：
  1. 无 principal → `401 UNAUTHENTICATED`
  2. 无 resource resolver → `500 RESOURCE_RESOLUTION_UNAVAILABLE`
  3. resolver 返回 empty → `404 RESOURCE_NOT_FOUND`
  4. `decision.allowed()==false` → `403 ACCESS_DENIED`
- 默认 MVC 失败 code（ProblemDetail）：`401=IAM_UNAUTHENTICATED`、`403=IAM_ACCESS_DENIED`、`404=IAM_RESOURCE_NOT_FOUND`、`500=IAM_RESOURCE_RESOLUTION_UNAVAILABLE`，由 `IamAuthorizationFailure` 枚举定义。
- 声明式路径的失败写响应与决策判定在同一个 interceptor 内完成：`deny(...)` 调 `failures.write(request, response, status, failure)` 后返回 `false`，不会继续调用 controller 方法。
- 编程式路径由 `AuthorizationEngine.require(...)`（`default` 方法）在 `decide(...).allowed()==false` 时抛出 `AuthorizationDeniedException`；`decision()` 返回完整 `AuthorizationDecision`（`allowed`/`decisionCode`/`steps`）。

## 真实示例

默认失败处理器 `ProblemDetailIamAuthorizationFailureHandler` 写出 `Content-Type: application/problem+json`。它以 `ProblemDetail.forStatusAndDetail(status, GENERIC_DETAIL)` 构造，其中 `GENERIC_DETAIL = "The request could not be authorized."`，并把拒绝类别放进 `code`、请求路径放进 `path`。例如 Reader 访问需要写权限的接口，体为：

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "The request could not be authorized.",
  "code": "IAM_ACCESS_DENIED",
  "path": "/api/documents/1001"
}
```

注意：默认 `detail` 恒为通用安全文案，不携带具体拒绝原因（原因在 `code` 中）。若应用希望向客户端暴露更细的“为什么被拒”：

- **声明式 MVC 路径**：注册一个自定义的 `IamAuthorizationFailureHandler` Bean，由它接管默认的 `ProblemDetailIamAuthorizationFailureHandler`，改写 `detail` 或附加 `decisionCode` / `steps`。
- **编程式路径**：捕获 `AuthorizationDeniedException` 并读取 `decision()`，把 `AuthorizationDecision`（含 `decisionCode` 与 `steps`）映射成你的业务错误体。

## 源码

- `IamAuthorizationInterceptor`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/web/
- `AuthorizationEngine`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
