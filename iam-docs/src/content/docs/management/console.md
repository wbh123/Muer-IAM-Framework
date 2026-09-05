---
title: Management Console 介绍
description: IAM Admin Console 的定位、能力与权限模型概述。
sidebar:
  order: 1
---

`iam-admin-web` 是 IAM Starter 的**可选**管理控制台。它通过 `iam-management-web`
暴露的 Management API 完成 IAM 运维与权限治理，而不是直接读取数据库或 Redis。

```
IAM Spring Boot Starter
        │
        └── iam-management-web
                  │
                  ▼
             OpenAPI iam.yaml
                  │
          ┌───────┴────────┐
          ▼                ▼
   Java Server API   TypeScript Client (openapi-generator)
                                  │
                                  ▼
                         IAM Admin Console (Vue 3)
```

## 能力

| 分组 | 页面 | 说明 |
| --- | --- | --- |
| 概览 | `/dashboard` | 轻量运营计数 + 最近审计事件 |
| 用户 | `/users`、`/users/:id` | 搜索/过滤、启停、详情 Tabs（Identity/Profile/Session/Audit） |
| Permission | `/permissions` | Permission Explorer：registry + 使用计数 |
| Permission Template | `/templates`、`/templates/:id` | 模板/版本/权限查看；仅 DRAFT 可编辑 |
| Profile | `/profiles`、`/profiles/:id` | 筛选、编辑、Scope 管理、Effective Permissions 只读展示 |
| Session | `/sessions` | 跨用户 Session 查询与强制撤销（仅状态，无 token） |
| Audit | `/audit` | 只读审计搜索与详情 Drawer |
| Diagnostics | `/diagnostics` | Authorization Playground：逐步决策诊断 |
| 个人 | `/account` | 个人 Profile/切换/我的 Session（无需 admin 权限） |

## 权限模型（IAM 管理 IAM）

控制台不使用「管理员角色旁路」。每条 `/iam/admin/**` 请求都携带当前 principal，由
`AuthorizationEngine` 校验：

- **Permission**：active profile 的 Template Version 包含对应 `iam.admin.*` code；
- **Resource Scope**：profile 的 scope 覆盖被管理资源（scopeType/scopeRefId/accessMode）；
- **扩展策略**：宿主注入的 `AuthorizationPolicy` 照常参与。

典型 `iam.admin.*` 集合：

```text
iam.admin.user.read / write
iam.admin.identity.read / write
iam.admin.permission.read
iam.admin.template.read / write
iam.admin.profile.read / write
iam.admin.scope.read / write
iam.admin.session.read / revoke / revoke-user
iam.admin.audit.read
iam.admin.diagnostics
iam.admin.overview.read
iam.admin.authorization-version.increment
```

前端菜单按 `GET /iam/auth/capabilities` 返回的本人权限渲染；菜单隐藏仅是 UI 优化，
安全边界始终在后端。
