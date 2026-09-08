---
title: 初始化第一个管理员
description: 在生产空库中由宿主显式、安全、幂等地建立第一个 Muer 管理员。
---

Muer 的 Management API 自身受 `iam.admin.*` 权限保护。全新数据库还没有管理员 Profile，因此不能通过尚未授权的 Admin API 创建第一个管理员：

```text
调用 Admin API
  → 需要 iam.admin.*
  → 需要已发布的管理员 Template Version 和 Profile
  → 新库中尚不存在
```

`MuerAdministrationBootstrapService` 用于打破这一次性的引导闭环。它是 Java Service，不是公开 HTTP 接口，也不会创建宿主账号或凭据。

### Bootstrap 前后的状态

直观地看，Bootstrap 只“补齐”授权投影相关的部分，不碰你的宿主账号：

```text
执行前                                  执行后

Host User 1001              ✅          Admin Template               ✅
  （你已有的宿主用户）                    └── 稳定 Key：muer-administrator
                                          │
Muer User Projection 1001   ✅          已发布 Template Version      ✅
  （已通过 saveUser 投影）                 └── iam.admin.* 权限
                                          │
Admin Template              ❌          Admin Profile               ✅
Admin Profile               ❌           ├── userId = 1001（宿主用户）
                                         ├── IAM_ADMIN/*  READ
                                         └── IAM_ADMIN/*  WRITE
```

也就是说：**Bootstrap 之前缺的是“管理员授权投影”，不是“用户”**。执行后，`result` 里带回 `profileId` 与 `templateVersionId`，宿主保存后，下次该用户登录就会以管理员 Profile 进入系统。

## 前置条件

调用前必须满足：

1. 宿主应用已经创建真实用户，并能取得正数 `userId`；
2. 该用户已经通过 `AccountGovernanceService.saveUser(...)` 同步到 Muer User Projection；
3. Muer Schema 已就绪；
4. 应用已经注册 `iam.admin.*` Permission；
5. `clientTypes` 与宿主允许的客户端类型一致。

:::caution[Host User 不等于 Muer User Projection]
宿主业务库里存在 `user.id=1001`，不代表 `IamUserRepository` 已能找到 `1001`。Bootstrap 不创建宿主用户，也不自动投影用户；目标 `userId` 不存在于 Muer User Projection 时会立即失败。
:::

使用当前正式治理入口建立投影：

```java
import cloud.muer.authentication.AccountGovernanceService;

accountGovernanceService.saveUser(
    existingHostUserId,
    hostUsername,
    hostUserType,
    true);
```

`AccountGovernanceService` 是 Muer 的真实公共 Service。宿主仍负责决定何时同步，以及用户名、类型和启用状态来自哪里。

> **saveUser 不是新建一个登录账号。** 它把宿主**已有**的用户投影到 Muer 的 User Projection，使之后的 Profile、Session、Audit 能稳定引用同一个 `userId`。Muer 并不因此维护第二套“能登录”的账号系统——登录仍走宿主身份源（见[身份认证器](/authentication/identity-authenticator/)）。

## 最小调用

从受控的部署初始化器、迁移后任务或其他仅由宿主触发的 Java 流程调用：

```java
import cloud.muer.authorization.AdministrationBootstrapRequest;
import cloud.muer.authorization.AdministrationBootstrapResult;
import cloud.muer.authorization.MuerAdministrationBootstrapService;

import java.util.Set;

public final class MuerFirstAdministratorProvisioner {
    private final MuerAdministrationBootstrapService bootstrapService;

    public MuerFirstAdministratorProvisioner(
            MuerAdministrationBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    public AdministrationBootstrapResult provision(long existingHostUserId) {
        return bootstrapService.bootstrapFirstAdministrator(
            new AdministrationBootstrapRequest(
                existingHostUserId,   // 已经进入 Muer User Projection 的宿主用户 ID
                Set.of("WEB")));      // 该管理员允许使用的客户端类型
    }
}
```

`existingHostUserId` 必须是你**已经在第 2 步用 `saveUser` 投影过**的那个正数 `userId`；`Set.of("WEB")` 声明这个管理员 Profile 允许哪些 `clientType` 登录（要与 `muer.client-types` 及其登录方式一致）。

Service 会：

1. 验证宿主 `userId` 已存在于 Muer 用户投影；
2. 收集当前注册的全部 `iam.admin.*` Permission；
3. 确保稳定业务 Key `muer-administrator` 对应的 Template 存在；
4. 创建并发布包含这些权限的 Version；
5. 为目标用户创建 `muer-administrator` Profile；
6. 为 Profile 授予已有管理根 Scope：`IAM_ADMIN / * / READ` 与 `IAM_ADMIN / * / WRITE`；
7. 返回 Template、Version 与 Profile ID。

相同请求可重复执行，不会重复创建 Template、Version 或 Profile。若稳定 Key 已存在但名称、描述、启用状态、权限集合或目标用户 Profile 语义不一致，调用会 fail-fast，不覆盖既有数据。

不同用户可以分别调用；每个用户获得自己的管理员 Profile，已有用户不会被破坏。

## 安全边界

生产环境禁止：

- 开启 Quick Start Demo Seeder；
- 创建默认 `admin/admin`；
- 暴露匿名 `/bootstrap` HTTP Endpoint；
- 自动生成或记录默认管理员密码；
- 绕过宿主用户与认证体系；
- 用固定数据库 ID 覆盖既有数据。

Bootstrap 只建立 Muer 授权投影。用户名、密码、MFA、账号生命周期与身份审计仍由宿主系统负责。Starter 内置了 Muer Management Resource 的层级解释：`IAM_ADMIN / *` 覆盖 Muer 自己的 Management API 所使用的明确资源类型。宿主无需为这些 Muer 管理资源重复编写层级规则。

宿主的 `ResourceHierarchyProvider` 只应声明自己的业务资源层级；Starter 会将它与内置 Muer 规则按“任一规则命中即可”的方式组合。`IAM_ADMIN / *` 绝不会自动覆盖例如 `DOCUMENT / 1001` 等宿主资源，也不会以 `IAM_` 前缀匹配未知资源类型。权限码和 Scope 仍同时必需。

## 完成后

Bootstrap 返回的 `AdministrationBootstrapResult` 包含 `profileId()` 与 `templateVersionId()`。宿主必须把这两个值保存到自己的用户授权映射，后续认证时投影到 Principal：

```java
AdministrationBootstrapResult result = provision(existingHostUserId);

// 将 result.profileId() 与 result.templateVersionId()
// 保存到宿主已有的用户授权映射中。

IdentityAuthenticator authenticator = request -> hostAccounts
    .verify(request.username(), request.password())
    .map(account -> new IamPrincipal(
        account.id(),
        account.identityId(),
        account.identityDomain(),
        account.muerProfileId(),        // result.profileId()
        account.muerTemplateVersionId(),// result.templateVersionId()
        request.clientType(),
        account.authorizationVersion()));
```

上例中的 `hostAccounts` 与 `account.muerProfileId()` 是宿主自己的适配层，不是新增的 Muer API。Bootstrap 不会偷偷修改宿主认证数据。只有 `IdentityAuthenticator` 返回正确的 `activeProfileId` 与 `templateVersionId`，新 Token 才会使用管理员授权。

完整链路：

```text
Host User
  → AccountGovernanceService.saveUser(...) → Muer User Projection
  → MuerAdministrationBootstrapService
  → Admin Profile + PUBLISHED Version + IAM_ADMIN/* Scopes
  → 宿主保存 profileId/templateVersionId 映射
  → IdentityAuthenticator → IamPrincipal
  → IAM_* Management Resource → AuthorizationEngine → ALLOW
```

之后可通过受保护的 Management API / Console 创建其他 Template、Version 和 Profile。

Bootstrap 保持幂等，因此不要求执行后删除代码；但触发条件必须受宿主控制，不能由匿名网络请求驱动。

## 完成检查

```text
□ 调用前已把宿主 userId 通过 AccountGovernanceService.saveUser 投影（不是只存在于宿主库）
□ bootstrapFirstAdministrator(...) 返回，未抛“用户不存在”类异常
□ 结果里 profileId() 与 templateVersionId() 均为正数，且已保存到宿主的用户授权映射
□ 同一请求再调一次也不重复建 Template / Version / Profile（幂等）
□ 用该账号 + 新 Token 能访问一个受 iam.admin.* 保护的 Management API（200）
□ 生产环境未开启 Quick Start Demo Seeder，也未暴露匿名 /bootstrap HTTP 端点
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 调用即失败：目标用户不在 User Projection | 宿主库里没有这个 `userId` 并不够，先 `saveUser` 投影它。见上“Host User ≠ Muer User Projection”。 |
| 把 Bootstrap 当“建账号”用 | 它不是。登录账号与密码由宿主身份源负责；Bootstrap 只建 Muer 授权投影。 |
| 管理请求仍 403 | 新 Token 必须由返回正确 `activeProfileId/templateVersionId` 的 `IdentityAuthenticator` 签发；检查是否保存并投影了 Bootstrap 返回的两个 ID。 |
| 想把 Bootstrap 暴露成匿名 HTTP 端点 | 禁止。触发条件必须由宿主受控流程驱动，不能匿名网络调用。 |
| 想给更多管理员建 Profile | 用同一个 Bootstrap Service 对不同 `userId` 各调一次，或之后走 Management API / Console。 |

## 下一步

- [第一次使用 Admin Console](/management/first-admin-tutorial/)——有了第一个管理员之后，如何在界面里管理模板/Profile/Scope。
- [身份认证器](/authentication/identity-authenticator/)——如何把 Bootstrap 结果投影进 `IamPrincipal`。
- [权限管理](/getting-started/permission-management/)——管理员 Profile 与普通用户 Profile 的关系。

