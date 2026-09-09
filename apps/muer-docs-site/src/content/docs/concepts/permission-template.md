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

来自 [`QuickStartDemoSeeder`](https://github.com/wbh123/Muer-IAM-Framework/blob/main/examples/showcase/src/main/java/cloud/muer/showcase/QuickStartDemoSeeder.java)：

| 版本 | 模板 | 状态 | 权限 |
| --- | --- | --- | --- |
| 301 | quickstart-document-reader (201) | PUBLISHED | 701 `document:read` |
| 302 | quickstart-document-editor (202) | PUBLISHED | 701 `document:read` + 702 `document:update` |

## 与 Profile 的关系

Profile 通过 `templateVersionId` 引用某个版本（源码 [AuthorizationProfile](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/AuthorizationProfile.java)）。切换 Profile 即切换所引用的模板版本，从而改变可用 permission 与 scope。

## Template vs Version：谁改了、影响谁

模板是一个持续演进的“容器”，版本才是真正被 Profile 引用的“快照”。一个典型生命周期：

```text
Document Editor（模板）
│
├── V1 PUBLISHED        ← 已被若干 Profile 引用
│     document:read
│     document:update
│
└── V2 DRAFT            ← 正在编辑，尚未生效
      document:read
      document:update
      document:delete
```

要点：

- **已经 PUBLISHED 的版本不要直接改**：被 Profile 引用的是那个版本号对应的权限集合。若想调整权限，应新建一个 DRAFT 版本、修改、再 PUBLISHED，而不是改写已发布的版本——否则线上正在使用的授权会“静默漂移”，难以审计与回滚。
- **Profile 绑定的是 Version**：正因为如此，发布 V2 后，已绑定 V1 的 Profile 不会自动获得 `document:delete`。要让他们升级，需要把对应 Profile 的 `templateVersionId` 指向 V2。
- **什么时候 RETIRED**：当某个版本不再被任何 Profile 引用、且你不再希望新 Profile 选中它时，把它 `RETIRED`。不要把 RETIRED 当成“删除”，历史审计仍需要它。
- 只有 `PUBLISHED` 的版本才会实际授予权限；`DRAFT` 仅供编辑，`RETIRED` 已下线。

## 下一步

继续阅读 [Profile](/concepts/profile/) 与 [资源范围](/concepts/resource-scope/)。
