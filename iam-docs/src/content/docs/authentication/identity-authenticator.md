---
title: 身份认证器
description: 说明 IdentityAuthenticator 函数式接口如何对接宿主用户体系完成身份解析。
sidebar:
  order: 2
---

## 解决什么问题

IAM **不持有**宿主的 User / Employee / Account / LDAP 等凭据源。它只定义「给我一个 `LoginRequest`，还你一个 `IamPrincipal`」的契约，由宿主实现来真正校验密码并组装身份。

## 关键概念

`IdentityAuthenticator` 是单方法函数式接口：

```java
Optional<IamPrincipal> authenticate(LoginRequest request);
```

- 校验成功返回 `Optional.of(IamPrincipal)`；失败返回 `Optional.empty()`（对应 401）。
- 宿主负责对接自己的 User 服务、LDAP、密码库等。**IAM 不拥有宿主凭据**。

## 登录流程

```
POST /iam/auth/login
  → IAM
  → IdentityAuthenticator.authenticate(request)
  → 宿主 User Service（校验凭据）
  → IamPrincipal
  → IAM Token / Session
```

## 关于 clientType

`iam.client-types` 的合法性校验发生在 **IAM 层**（`clientType` 必须精确匹配配置列表）。宿主适配器**不应**再重复校验 clientType，避免两处规则不一致。

## 源码

<https://github.com/wbh123/iam/blob/main/muer-authentication/src/main/java/io/github/iamstarter/authentication/IdentityAuthenticator.java>

相关：见 [client-type](/authentication/client-type/) 与 [login](/authentication/login/)。
