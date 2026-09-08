---
title: 错误码
description: 授权决策码与 HTTP ProblemDetail 错误码对照。
sidebar:
  order: 4
---

## 授权决策码（AuthorizationDecision.decisionCode）

`AuthorizationDecision.allowed=false` 时，`decisionCode` 标明拒绝原因。以下枚举来自
`DefaultAuthorizationEngine`，按其校验顺序排列（遇到首个不满足即返回，`steps` 记录已检查的步骤）：

- `IDENTITY_DOMAIN_MISMATCH`：`principal.identityDomain` 与请求 `domain` 不匹配。
- `CLIENT_TYPE_MISMATCH`：`principal.clientType` 与请求 `clientType` 不匹配（需与 `muer.client-types` 精确匹配）。
- `PROFILE_MISSING`：令牌未携带 `activeProfileId`，或活动 Profile 无法解析。
- `PROFILE_UNAVAILABLE`：解析活动 Profile 时底层抛异常。
- `PROFILE_OWNER_MISMATCH`：Profile 的 `profileId`/`userId` 与令牌主体不一致。
- `PROFILE_DISABLED`：Profile `enabled=false`。
- `PROFILE_REVOKED`：Profile `revoked=true`。
- `PROFILE_CLIENT_DENIED`：Profile 的 `clientTypes` 不含令牌的 `clientType`。
- `PROFILE_NOT_YET_VALID`：`validFrom` 晚于当前时间。
- `PROFILE_EXPIRED`：`validUntil` 已早于当前时间。
- `PROFILE_TEMPLATE_MISMATCH`：Profile 的模板版本与令牌 `templateVersionId` 不一致。
- `PERMISSION_DENIED`：主体缺少该 `permissionCode`。
- `SCOPE_DENIED`：权限存在，但不在 `ResourceScope` 范围内或被 `accessMode` 限制。
- `ALLOWED`：`allowed=true`，全部检查通过。
- 扩展策略：若宿主注册 `AuthorizationPolicy` 且策略返回拒绝，`decisionCode` 为该策略的 `code()`。

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

- 拦截器：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/web/IamAuthorizationInterceptor.java>
- `AuthorizationDecision`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/AuthorizationDecision.java>
