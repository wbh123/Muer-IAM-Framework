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
2. Muer Schema 已就绪；
3. 应用已经注册 `iam.admin.*` Permission；
4. `clientTypes` 与宿主允许的客户端类型一致。

## 最小调用

从受控的部署初始化器、迁移后任务或其他仅由宿主触发的 Java 流程调用：

```java
import cloud.muer.authorization.AdministrationBootstrapRequest;
import cloud.muer.authorization.MuerAdministrationBootstrapService;

import java.util.Set;

public final class MuerFirstAdministratorProvisioner {
    private final MuerAdministrationBootstrapService bootstrapService;

    public MuerFirstAdministratorProvisioner(
            MuerAdministrationBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    public void provision(long existingHostUserId) {
        bootstrapService.bootstrapFirstAdministrator(
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
6. 返回 Template、Version 与 Profile ID。

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

Bootstrap 只建立 Muer 授权投影。用户名、密码、MFA、账号生命周期与身份审计仍由宿主系统负责。

## 完成后

让该宿主用户通过正常 `IdentityAuthenticator` 登录，取得带管理员 Profile 的 Principal，再访问 Admin Console。之后可使用受保护的 Management API / Console 创建其他 Template、Version 和 Profile。

Bootstrap 保持幂等，因此不要求执行后删除代码；但触发条件必须受宿主控制，不能由匿名网络请求驱动。
