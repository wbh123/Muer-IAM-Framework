---
title: 用户与权限数据如何映射
description: 把宿主现有的用户与身份投影进 IAM，而非迁移数据库。
sidebar:
  order: 3
---

## 解决什么问题

宿主通常已有用户表与登录体系。IAM 不要求你迁移用户数据，而是通过**投影**：在运行时把现有用户映射成 IAM 能消费的 `IamPrincipal`。

## 关键概念

`IdentityAuthenticator` 是函数式接口：

```java
Optional<IamPrincipal> authenticate(LoginRequest request);
```

你只需实现它：用 `request.username/password/clientType` 校验现有体系，成功后构造 `IamPrincipal(userId, identityId, identityDomain, activeProfileId, templateVersionId, clientType, authorizationVersion)`。校验规则为 `userId>0`、`identityId/identityDomain/clientType` 非空、`activeProfileId/templateVersionId>0`、`authorizationVersion>=0`。

可选投影：把宿主的角色/权限映射为 `AuthorizationProfile`（含 `scopes: List<ResourceScope>`）与 `PermissionTemplateVersion`，让 IAM 的 scope 与模板能力生效。

## 示例

演示实现见 `ExampleIdentityAdapter`：alice 登录默认得到 `IamPrincipal(userId=101, activeProfileId=401, templateVersionId=301)`。

## 源码参考

- `IdentityAuthenticator`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/IdentityAuthenticator.java>
- `IamPrincipal`：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/model/IamPrincipal.java>
- 演示适配器：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/examples/showcase/src/main/java/cloud/muer/showcase/>
