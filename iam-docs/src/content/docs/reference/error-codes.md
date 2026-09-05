---
title: 错误码
description: 授权决策码与 HTTP ProblemDetail 错误码对照。
sidebar:
  order: 4
---

## 授权决策码（AuthorizationDecision.decisionCode）

来自 `DefaultAuthorizationEngine`：

- `ALLOWED`：允许。
- `PERMISSION_DENIED`：主体缺少该 `permissionCode`。
- `SCOPE_DENIED`：权限存在，但不在 `ResourceScope` 范围内或被 `accessMode` 限制。
- `IDENTITY_DOMAIN_MISMATCH`：身份域不匹配。
- `CLIENT_TYPE_MISMATCH`：客户端类型不匹配（需与 `iam.client-types` 精确匹配）。
- Profile 系列：Profile 被禁用/撤销或不在有效期（`enabled=false`、`revoked=true`、`validFrom/validUntil` 越界）。

## HTTP ProblemDetail 错误码

默认 MVC 失败响应（`application/problem+json`）：

| HTTP | code | 触发条件 |
| --- | --- | --- |
| 401 | `IAM_UNAUTHENTICATED` | 无 principal（拦截决策第 1 步 `UNAUTHENTICATED`） |
| 403 | `IAM_ACCESS_DENIED` | `engine.allowed()==false`（第 4 步 `ACCESS_DENIED`） |
| 404 | `IAM_RESOURCE_NOT_FOUND` | resolver 返回 empty（第 3 步 `RESOURCE_NOT_FOUND`） |
| 500 | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | 无 resource resolver（第 2 步 `RESOURCE_RESOLUTION_UNAVAILABLE`） |

## 拦截决策顺序

`IamAuthorizationInterceptor`：先取方法注解，无则取类注解（**Method 优先于 Class**），随后：无 principal→401；无 resolver→500；resolver empty→404；engine 拒绝→403。

## 源码参考

- 拦截器：<https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/web/IamAuthorizationInterceptor.java>
- `AuthorizationDecision`：<https://github.com/wbh123/iam/blob/main/iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationDecision.java>
