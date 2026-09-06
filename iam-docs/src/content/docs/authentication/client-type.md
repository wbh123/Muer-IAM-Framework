---
title: 客户端类型
description: 说明 iam.client-types 配置及其在 IAM 层的登录校验规则。
sidebar:
  order: 3
---

## 解决什么问题

同一个用户可能从 Web、移动端、服务端等多种渠道登录。IAM 用 `clientType` 区分客户端，并在登录时强制其必须属于允许的集合，防止非法客户端接入。

## 关键概念

- 配置项 `iam.client-types`（`List<String>`，默认 `[WEB]`），约束：≥1 个非空项；登录时的 `clientType` 必须**精确匹配**其中一项，否则登录失败（401）。
- `clientType` 同时写入 `IamPrincipal.clientType`，参与后续授权决策（如 `CLIENT_TYPE_MISMATCH`）。

## 配置示例

```yaml
iam:
  client-types:
    - WEB
    - MOBILE
    - SERVER
```

## 校验职责

`iam.client-types` 的校验在 **IAM 层统一完成**；宿主的 `IdentityAuthenticator` 适配器**不应**再重复校验，以免规则漂移。

## 源码

- IamProperties：<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/MuerProperties.java>

相关：见 [identity-authenticator](/authentication/identity-authenticator/) 与 [current-user](/authentication/current-user/)。
