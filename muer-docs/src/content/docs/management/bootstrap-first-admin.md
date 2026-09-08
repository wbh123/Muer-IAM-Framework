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
                existingHostUserId,
                Set.of("WEB")));
    }
}
```

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

Bootstrap 只建立 Muer 授权投影。用户名、密码、MFA、账号生命周期与身份审计仍由宿主系统负责。宿主的 `ResourceHierarchyProvider` 应保留现有管理根语义：`IAM_ADMIN / *` 覆盖 `/iam/admin/**` 使用的 `IAM_*` Management Resource，但不覆盖宿主业务资源。

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
