---
title: 授权策略与拦截
description: 说明 IamAuthorizationInterceptor 的失败阶梯与对应 HTTP 错误码。
sidebar:
  order: 6
---

## 解决什么问题

把「声明权限 → 引擎决策 → 返回错误」串成统一策略。拦截器按固定顺序短路，保证每种失败都有确定、可机读的错误码（`application/problem+json`）。

## 拦截决策顺序（真实）

`IamAuthorizationInterceptor` 先解析注解（**方法优先于类**），随后：

1. 无 principal → **401** `IAM_UNAUTHENTICATED`
2. 无 resource resolver → **500** `IAM_RESOURCE_RESOLUTION_UNAVAILABLE`
3. resolver 返回 empty → **404** `IAM_RESOURCE_NOT_FOUND`
4. `engine.allowed()==false` → **403** `IAM_ACCESS_DENIED`

## 错误码映射

| HTTP | ProblemDetail code |
| --- | --- |
| 401 | `IAM_UNAUTHENTICATED` |
| 403 | `IAM_ACCESS_DENIED` |
| 404 | `IAM_RESOURCE_NOT_FOUND` |
| 500 | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` |

`ACCESS_DENIED` 的具体原因由 `AuthorizationDecision.decisionCode`（如 `SCOPE_DENIED`）进一步说明。

## 源码

<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptor.java>

相关：见 [authorization-engine](/authorization/authorization-engine/) 与 [require-permission](/authorization/require-permission/)。
