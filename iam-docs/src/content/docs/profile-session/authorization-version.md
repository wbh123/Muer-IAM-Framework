---
title: 授权版本（AuthorizationVersion）
description: 理解 principal 上的 authorizationVersion 如何在权限模板变更时保证决策一致性。
sidebar:
  order: 5
---

## 解决的问题

权限模板版本（`PermissionTemplateVersion`）会随业务演进发布新版本。若用户登录后模板升级，其旧令牌可能仍指向旧版本，导致「同一个人不同设备权限不一致」。`authorizationVersion` 是 principal 上记录的授权快照版本，用于表达「本次身份所依据的权限基线」。

## 关键概念

- `IamPrincipal` record 含字段 `authorizationVersion`（类型 `int`，约束 `>=0`）。
- `PermissionTemplateVersion` record：`(versionId, templateId, versionNumber, status, permissions)`；`status` 为 `DRAFT/PUBLISHED/RETIRED`。
- QuickStart 种子：reader 模板 version `301`（PUBLISHED，含 `701 document:read`），editor 模板 version `302`（PUBLISHED，含 `701+702`）。
- 管理员可通过 `POST /iam/admin/users/{userId}/authorization-version` 推进某用户的授权版本。
- 概念页另见 [concepts/authorization-version](/concepts/authorization-version/)。

## 真实示例

Alice 默认 principal：

```json
{ "userId": 101, "activeProfileId": 401, "templateVersionId": 301, "authorizationVersion": 1 }
```

切到 profile 402 后 `templateVersionId=302`，`authorizationVersion` 反映其编辑器权限基线。

## 源码

- `IamPrincipal`：https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/model/IamPrincipal.java
- `PermissionTemplateVersion`：https://github.com/wbh123/iam/blob/main/iam-authorization/src/main/java/io/github/iamstarter/authorization/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
