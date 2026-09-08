---
title: Management Console 介绍
description: Muer Admin Console 的定位、能力、启动方式与权限模型概述。
sidebar:
  order: 1
---

`muer-admin-web` 是 **IAM 0.1.0 首发范围内的可选管理控制台**。它通过
`muer-management-web` 暴露的 Management API 完成 IAM 运维与权限治理，而不是直接读取数据库或 Redis。

它属于 0.1.0 产品能力，但不是 Starter 的运行依赖：只使用 `muer-spring-boot-starter` 的宿主应用不需要部署前端。

```
IAM Spring Boot Starter
        │
        └── muer-management-web
                  │
                  ▼
             OpenAPI iam.yaml
                  │
          ┌───────┴────────┐
          ▼                ▼
   Java Server API   TypeScript Client (openapi-generator)
                                  │
                                  ▼
                         Muer Admin Console (Vue 3)
```

## 能力

| 分组 | 页面 | 说明 |
| --- | --- | --- |
| 概览 | `/dashboard` | 轻量运营计数 + 最近审计事件 |
| 用户 | `/users`、`/users/:id` | 搜索/过滤、启停、详情 Tabs（Identity/Profile/Session/Audit） |
| Permission | `/permissions` | Permission Explorer：registry + 使用计数 |
| Permission Template | `/templates`、`/templates/:id` | 创建模板与 DRAFT Version、编辑 DRAFT Permission、发布 Version；PUBLISHED 只读 |
| Profile | `/profiles`、`/profiles/:id` | 创建并绑定 PUBLISHED Version、筛选、属性与 Scope 管理、Effective Permissions 只读展示 |
| Session | `/sessions` | 跨用户 Session 查询与强制撤销（仅状态，无 token） |
| Audit | `/audit` | 只读审计搜索与详情 Drawer |
| Diagnostics | `/diagnostics` | Authorization Playground：逐步决策诊断 |
| 个人 | `/account` | 个人 Profile/切换/我的 Session（无需 admin 权限） |

## 本地启动

先按[快速开始](/getting-started/quick-start/)或[手动部署](/getting-started/manual-deployment/)准备 MySQL、Redis 并启动 `muer-example`。

开发演示管理员只有在下面两个条件同时满足时才会创建：

```text
SPRING_PROFILES_ACTIVE=dev
MUER_EXAMPLE_SEED_ADMIN=true
```

登录信息：

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

前端：

```bash
cd muer-admin-web
npm ci
npm run api:generate
npm run dev
```

打开 Vite 输出的地址，通常是 `http://localhost:5173`。

:::caution[仅用于开发演示]
`admin-demo / demo-pass` 不会在生产环境自动创建。生产第一个管理员由宿主显式调用 `MuerAdministrationBootstrapService` 创建授权投影，详见[初始化第一个管理员](/management/bootstrap-first-admin/)。
:::

## 建议手工验收

普通使用者不需要运行 IAM 仓库的完整 CI。浏览器中建议至少确认：

1. 登录后可以进入 Dashboard；
2. Users、Templates、Profiles、Sessions、Audit 页面可以读取数据；
3. 编辑一个开发 Profile 或 Scope 后刷新仍能读取新值；
4. 撤销一个测试 Session 后目标会话失效；
5. Diagnostics 能显示 ALLOW/DENY 与决策步骤；
6. 缺少某项 `iam.admin.*` Capability 时，对应路由进入 403，同时后端接口也拒绝访问；
7. Logout 调用后端登出并清理浏览器会话。

详细操作见[第一次使用管理控制台](/management/first-admin-tutorial/)，部署见[安装与部署](/management/deploy/)。

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

前端菜单与路由按 `GET /iam/auth/capabilities` 返回的本人权限渲染；这些只是 UI Guard，安全边界始终在后端。

`POST /iam/authorization/diagnostics` 是当前已认证 Principal 的自诊断接口，不要求 `iam.admin.diagnostics`；`iam.admin.diagnostics` 只用于控制 Admin Console 中 Diagnostics 管理页面的可见性和访问体验。
