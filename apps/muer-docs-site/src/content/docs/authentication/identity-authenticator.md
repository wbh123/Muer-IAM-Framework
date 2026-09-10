---
title: 接入现有登录系统
description: 谁调用它、你负责什么、Muer 负责什么，以及如何用你自己的 UserService 投影一个真实的 IamPrincipal（含逐字段解释与首次无 Profile 场景）。
sidebar:
  order: 2
---

`IdentityAuthenticator` 是宿主接入自己用户体系的唯一入口。这一页回答四件事：谁调用它、你负责什么、Muer 负责什么、`IamPrincipal` 每个字段怎么填。

## 一、谁调用它？

它是单方法函数式接口，签名是：

```java
Optional<IamPrincipal> authenticate(LoginRequest request);
```

调用方是 Muer 的登录流程：

```text
POST /iam/auth/login
  → Muer
  → IdentityAuthenticator.authenticate(request)   ← 这里轮到你
  → 宿主 UserService / LDAP / 密码库校验凭据
  → 返回 IamPrincipal
  → Muer 校验 clientType、签发 opaque Token、持久化 Session
```

你只需要提供**一个 Spring Bean**，实现这个方法。校验成功返回 `Optional.of(principal)`，失败返回 `Optional.empty()`（对应 HTTP 401，且不创建会话）。

## 二、你负责什么 vs Muer 负责什么

| 责任 | 归属 |
| --- | --- |
| 持有账号、密码、UserService / LDAP 等真实身份源 | 宿主（你） |
| 校验用户凭据是否合法 | 宿主（你） |
| 把通过校验的用户投影成一个 `IamPrincipal` | 宿主（你） |
| 校验 `clientType` 是否在 `muer.client-types` 允许列表 | Muer |
| 签发 opaque Token、持久化 Session、管理 TTL | Muer |
| 之后每次请求如何授权 | Muer（AuthorizationEngine） |

要点：**不要在 `IdentityAuthenticator` 里重复校验 clientType**——`muer.client-types` 的匹配由 Muer 在登录层统一做，两处维护同一规则会漂移。你的职责只是“验对账号密码，并说清楚这个人是谁”。

## 三、IamPrincipal 逐字段解释

`IamPrincipal` 是 Muer 对一次已认证主体的唯一投影，字段来自
`cloud.muer.core.model.IamPrincipal`。每个字段必须在构造时合法，否则抛异常：

| 字段 | 谁提供 | 示例 | 用途 / 约束 |
| --- | --- | --- | --- |
| `userId` | 宿主（你） | `1001` | 用户稳定 ID；**必须 > 0**。Muer 用它关联 Session、Profile、Scope。 |
| `identityId` | 宿主（你） | `alice` | 该用户在宿主体系里的标识（账号名 / key）；**非空**。 |
| `identityDomain` | 宿主（你） | `LOCAL` | 身份域，用于把不同来源/租户的授权语义隔开；请求 `domain` 不匹配会得到 `IDENTITY_DOMAIN_MISMATCH`。**非空**。 |
| `activeProfileId` | 宿主（你，来自 Profile） | `401` | 当前生效的 `AuthorizationProfile` ID；**必须为正**。决定该用户此刻用哪套模板版本 + Scope。 |
| `templateVersionId` | 宿主（你，来自 Profile） | `301` | 该 Profile 引用的 `PermissionTemplateVersion` ID；**必须为正**。 |
| `clientType` | `LoginRequest`（登录时传入） | `WEB` | 本次登录的客户端类型；**非空**。Muer 与 Profile 的 `clientTypes` 都据此约束。 |
| `authorizationVersion` | 宿主（你） | `3` | 授权版本，用于令牌失效控制；`>= 0`。递增它可使按旧版本签发的授权失效。 |

> `activeProfileId` / `templateVersionId` 属于“授权身份”而非“登录账号”。它们通常来自你在宿主里维护的 Profile 关系——见下文“首次用户没有 Profile 怎么办”。

## 四、最小实现

一个只验证密码、返回固定投影的最小示例（演示用，不要照抄到生产）。

:::note[下面的 `AccountGateway` 是宿主自己的接口]
`AccountGateway` / `Account` **不是 Muer API**，只是示例里假设你已有的账号查询层。真正要替换成的是你项目里现成的用户服务 / 仓储 / DAO。完整且可直接运行的示例见[从零接入 → 实现 IdentityAuthenticator](/getting-started/from-zero-tutorial/) 里的 `DemoAccountService`，它属于 `examples/quickstart`。
:::

```java
@Bean
IdentityAuthenticator identityAuthenticator(AccountGateway accounts) { // AccountGateway = 宿主示例接口
    return request -> {
        if (!"demo-pass".equals(request.password())) {
            return Optional.empty();
        }
        Account account = accounts.findByUsername(request.username()).orElse(null); // 宿主账号查询
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(new IamPrincipal(
                account.id(),                 // userId
                account.username(),           // identityId
                "LOCAL",                      // identityDomain
                account.activeProfileId(),    // activeProfileId
                account.templateVersionId(),  // templateVersionId
                request.clientType(),         // clientType（来自 LoginRequest）
                account.authorizationVersion())); // authorizationVersion
    };
}
```

## 五、真实 UserService 接入

生产里不要自己做明文密码校验。让 `IdentityAuthenticator` 委托给你的既有登录服务 / Spring Security 的 `UserDetailsService` / LDAP。

:::note[`UserDirectory` 同样是宿主示例接口]
下面的 `UserDirectory` 不是 Muer API，它代表“你已有的登录校验入口”（例如 `UserDetailsService`、LDAP 客户端、或你自己的 `userService.verify(...)`）。它需要能回答两个问题：账号密码对不对、该用户当前激活哪个 Profile / 模板版本 / 授权版本。
:::

```java
@Bean
IdentityAuthenticator identityAuthenticator(UserDirectory users) { // UserDirectory = 宿主示例接口
    return request -> users.verify(request.username(), request.password())
            .map(user -> new IamPrincipal(
                    user.id(),                 // userId
                    user.login(),              // identityId
                    user.domain(),             // identityDomain
                    user.activeProfileId(),    // activeProfileId（已预置的 Profile）
                    user.templateVersionId(),  // templateVersionId
                    request.clientType(),      // clientType（来自 LoginRequest）
                    user.authorizationVersion())); // authorizationVersion
}
```

你的用户表除了“能不能登录”，还需要能回答“这个用户当前激活哪个 Profile / 模板版本 / 授权版本”——这正是把旧系统 Role 或“默认权限”投影进 Muer 的关键。

## 六、首次用户如何完成授权 Provisioning

`IamPrincipal` 要求 `activeProfileId` 与 `templateVersionId` 始终为正数。新用户不能带着空 Profile 进入登录流程，宿主应在账号首次允许登录前完成授权 Provisioning。

回答分三件事：

- **Profile 什么时候创建**：Profile（档案）通常由管理员或自助流程创建，用来决定“这个身份默认拥有哪些权限、能访问哪些资源”。它不是登录时随手造的。
- **IdentityAuthenticator 要不要自己建 Profile**：不要。它的职责是**投影当前存在的授权关系**，而不是在登录路径里并发建 Profile。登录里做写操作会引入竞态与副作用。
- **生产第一批用户怎么 Provision（预置）**：通过 [Permission Management](/getting-started/permission-management/) 或受控的 Bootstrap / Management API，为该用户创建一个指向某 `PUBLISHED` 模板版本 + 默认 Scope 的 Profile，并把 `profileId/templateVersionId` 落到你的用户投影数据上。之后登录时 `IdentityAuthenticator` 只需读取这层关系。

推荐的关系链：

```text
账号（Identity）
   └─ 一个或多个 Profile（AuthorizationProfile）
        ├─ 引用某个 PUBLISHED 的模板版本（权限）
        └─ 一组 ResourceScope（资源范围）
用户默认 Profile → 登录时投影 activeProfileId/templateVersionId
```

如果某用户确实“还没有任何 Profile”，通常意味着他登录后也没有任何业务权限——请让投影逻辑返回 `Optional.empty()`（拒绝登录）或让其得到一个“只有基础能力、无业务权限”的 Profile，取决于你的业务约定。别让登录代码替用户“现场发明”权限。

## 完成检查

接入后逐项核对（用 Quick Start 的 `alice / demo-pass / WEB` 或你自己的测试账号）：

```text
□ 应用能启动，且无 IdentityAuthenticator / 配置相关报错
□ POST /iam/auth/login 会真正进入宿主 UserService/DemoAccountService（打断点或看日志确认）
□ 正确凭据返回 200 并拿到 token
□ 错误密码 / 不存在用户返回 401（不创建 Session）
□ GET /iam/auth/me（或 /me）能看到正确的 userId / identityId / identityDomain
□ 返回的 activeProfileId / templateVersionId 与你预置的 Profile / 模板版本一致
□ clientType 不在 muer.client-types 时被 Muer 拒绝（而不是你在认证器里拒绝）
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 登录永远 401，认证器看起来没被调用 | 确认 Bean 类型是 `IdentityAuthenticator` 且只存在一个；检查日志是否真的进入你的方法。 |
| 正确凭据也 401 | `clientType` 与 `muer.client-types` 不精确匹配（大小写），或凭据校验逻辑有误。 |
| 启动抛异常：某个 `IamPrincipal` 字段非法 | 例如 `userId<=0`、空字符串、`activeProfileId` 为 null。日志会指出字段；回头检查 `DemoAccount` 数据。 |
| 能登录但随后业务 403 | 认证器返回的 `activeProfileId/templateVersionId` 指向的 Profile 未预置或无对应权限，见[权限管理](/getting-started/permission-management/)。 |
| 在认证器里也写了一份 clientType 允许名单 | 不要重复判断：`muer.client-types` 由 Muer 在登录层统一校验，两处维护会漂移。 |

更多启动 / 登录 / 403 排查见[排障](/operations/troubleshooting/)。

## 源码

- `IdentityAuthenticator`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/IdentityAuthenticator.java>
- `IamPrincipal`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/model/IamPrincipal.java>

相关：[client-type](/authentication/client-type/) 与 [login](/authentication/login/)。
