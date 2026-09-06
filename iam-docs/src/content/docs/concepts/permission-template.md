---
title: 权限模板（Permission Template）
description: 了解 PermissionTemplateVersion 如何把一组 permission 版本化，并为 Profile 提供可发布的授权基线。
sidebar:
  order: 3
---

## 它解决什么问题

直接把零散 permission 绑给用户难以审计与回滚。IAM 用「模板 + 版本」组织 permission：模板定义一组权限，版本记录该组权限在某时刻的快照，Profile 引用某个已发布版本。

## PermissionTemplateVersion 是什么

```java
record PermissionTemplateVersion(
    versionId,
    templateId,
    versionNumber,
    status,           // TemplateVersionStatus
    permissions       // Set<String>
)
```

`TemplateVersionStatus` 为枚举：`DRAFT` / `PUBLISHED` / `RETIRED`。只有 `PUBLISHED` 的版本才会真正授予 Profile 权限；`DRAFT` 仍在编辑，`RETIRED` 已下线。

## 演示数据（真实）

来自 [`QuickStartDemoSeeder`](https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/iamstarter/example/QuickStartDemoSeeder.java)：

| 版本 | 模板 | 状态 | 权限 |
| --- | --- | --- | --- |
| 301 | quickstart-document-reader (201) | PUBLISHED | 701 `document:read` |
| 302 | quickstart-document-editor (202) | PUBLISHED | 701 `document:read` + 702 `document:update` |

## 与 Profile 的关系

Profile 通过 `templateVersionId` 引用某个版本（源码 [AuthorizationProfile](https://github.com/wbh123/iam/blob/main/muer-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationProfile.java)）。切换 Profile 即切换所引用的模板版本，从而改变可用 permission 与 scope。

## 下一步

继续阅读 [Profile](/concepts/profile/) 与 [资源范围](/concepts/resource-scope/)。
