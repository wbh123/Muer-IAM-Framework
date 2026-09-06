---
title: 档案（Profile）
description: 理解 AuthorizationProfile 如何把模板版本、客户端类型、启用状态与资源范围组合成一个可切换的授权上下文。
sidebar:
  order: 4
---

## 它解决什么问题

同一用户往往在不同上下文拥有不同权限。IAM 用 Profile 表达「在某个上下文下，我能做什么、能访问哪些资源」，并支持运行时切换而不改动原会话。

## AuthorizationProfile 是什么

```java
record AuthorizationProfile(
    profileId,
    userId,
    profileName,
    templateVersionId,
    clientTypes,      // Set<String>
    enabled,
    revoked,
    validFrom,
    validUntil,
    scopes            // List<ResourceScope>
)
```

关键点：

- `templateVersionId` 指向某 `PUBLISHED` 的 `PermissionTemplateVersion`，决定可用 permission；
- `clientTypes` 限制该 Profile 适用的客户端类型；
- `scopes` 是一组 `ResourceScope`，限定可访问的资源空间；
- `enabled` / `revoked` / `validFrom` / `validUntil` 控制有效性。

## 演示数据（真实）

来自 [`QuickStartDemoSeeder`](https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/iamstarter/example/QuickStartDemoSeeder.java)：

| Profile | 用户/模板 | clientTypes | 默认 | scopes |
| --- | --- | --- | --- | --- |
| 401 alice-reader-project-101 | user101 / 301 | [WEB] | 是 | (PROJECT,101,READ) |
| 402 alice-editor-project-101 | user101 / 302 | [WEB] | 否 | (PROJECT,101,READ)+(PROJECT,101,WRITE) |

## 切换语义

切换到 402 会创建**独立 session 与原 token 之外的新 token**，原 reader token 不会被提升（见 [快速开始](/getting-started/quick-start/) 第 6 步）。切换端点：`POST /iam/authorization/profiles/{profileId}/switch`。

## 下一步

scope 细节见 [资源范围](/concepts/resource-scope/)；切换与管理见 [Profile 与 Session](/profile-session/profile-switch/)。
