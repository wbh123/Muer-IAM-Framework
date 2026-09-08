---
title: 第一次使用 Muer Admin Console
description: 登录管理控制台，用真实界面查看 Permission、Template、Profile 与 Scope，并理解 0.1.0 下哪些配置在界面上完成、哪些要用 Seeder 或 Management API。
---

这篇教程带你走一遍 Muer Admin Console 的真实界面，目标不是“点完能创建所有东西”，而是让你第一次就搞懂**哪些是开发者用代码声明的、哪些是管理员用界面/API 配置的**，以及当前 0.1.0 版 Console 到底能做什么、不能做什么。

在开始前，建议先完成[15 分钟快速开始](/getting-started/quick-start/)，那里的 `document:read` / `document:update` 与 Profile 401 / 402 就是本教程操作的对象。

## 0. 先建立正确心智

Muer 把“权限”这件事分成两层，**这两层分别由不同的人负责**：

```text
开发者（代码）                    管理员（配置）
PermissionDefinitionProvider      Permission Template
  → document:read                   → Template Version（含权限集合）
  → document:update                 → Profile（绑定 Version）
                                    → Scope（资源范围）
```

- **Permission** 是开发者用 `PermissionDefinitionProvider` 在代码里声明的“能力清单”。管理员**不会也不该**在界面上新建 `document:read` 这种权限。
- **Template / Template Version / Profile / Scope** 是管理员配置的“谁、通过哪个版本、在哪个范围内拥有哪些能力”。

记住这一点，就不会在 Console 里找不到“新建权限”按钮而困惑——那个按钮本来就不存在。

## 1. 前置：启动带 Admin 演示账号的宿主应用

Admin Console 是一个独立的前端，它操作的数据在宿主应用里。开发演示用的是 `muer-example` 应用 + `muer-admin-web` 前端。

**第 1 步：启动 `muer-example`，并开启 Admin 演示账号。**

```bash
export SPRING_PROFILES_ACTIVE=dev
export MUER_EXAMPLE_SEED_ADMIN=true        # 关键：才会创建 admin-demo 账号
export MUER_EXAMPLE_SEED_DEMO=true         # 附带 alice / Reader / Editor 演示数据
mvn -pl muer-example spring-boot:run
```

> `MUER_EXAMPLE_SEED_ADMIN=true` 会创建**仅限 dev 演示**的账号 `admin-demo / demo-pass`。生产与普通 dev 都不会自动存在该账号。

**第 2 步：启动 Admin Console 前端。**

```bash
cd muer-admin-web
npm ci
npm run dev        # 默认 http://localhost:5173
```

在浏览器打开 `http://localhost:5173`。

**完成检查**

- [ ] `muer-example` 启动成功；
- [ ] `http://localhost:5173` 能看到登录页。

## 2. 登录

Console 登录页与业务登录同源：它用 `POST /iam/auth/login` 换取 Bearer Token，存进浏览器 Session。

**现在做：** 在登录页输入

```text
用户名  admin-demo
密码    demo-pass
```

登录后默认落在**运营概览（Dashboard）**。

> 左侧菜单出现哪些页，取决于 `admin-demo` 的 Profile（405）里 `iam.admin.*` 权限。看不到某页通常是权限不足，而不是故障。

**完成检查**

- [ ] 登录成功，进入 Dashboard。

## 3. 查看 Permission（只读，印证“代码声明”）

左侧点 **Permissions**。你会看到一行行 Permission Code，例如 `document:read`、`document:update`，还有 `iam.admin.*` 那一批。

这里**没有“新建/删除权限”的按钮**，这是设计如此：

- 权限来自宿主应用的 `PermissionDefinitionProvider` 代码；
- Console 只负责**展示**开发者声明的能力及其使用情况。

**如果你在界面里找不到某个权限**，回到宿主代码，确认它有没有通过 Provider 声明、应用是否重新启动过。

**完成检查**

- [ ] 你能在 Permissions 里看到 `document:read` / `document:update`。

## 4. 查看 Template 与其 Version

左侧点 **Templates**，能看到模板列表（名称、Key、最新版本状态）。

点进任一模板进 **Template 详情**，会看到：

- 模板字段（Template ID / Key / Name / Enabled / Description）；
- **版本与权限**表：每行是一个 Template Version，含 Version Number、Status（`DRAFT` / `PUBLISHED` / `RETIRED`）、以及该版本包含的权限 Code。

关键规则（界面上已注明）：**版本不可变，PUBLISHED 版本只读**。

- 如果某行版本是 `DRAFT`，且有 `iam.admin.template.write` 权限，会看到 **「编辑 Draft」** 按钮；
- 点击后可在一个对话框里**增删该 Draft 版本的权限 Code**，然后点「保存」把 Draft 更新回服务端。

> 当前 0.1.0 Console **不支持**：从零新建一个 Template、新建一个 Version、或把一个 DRAFT 版本「发布」为 PUBLISHED。这些能力在 Console 里没有对应按钮。

**完成检查**

- [ ] 你能在 `muer-example` 的 QuickStart 数据里看到 Template 201 / 202 及 Version 301 / 302；
- [ ] 你能分清 `DRAFT` 可编辑、`PUBLISHED` 只读。

## 5. 查看 Profile 与其绑定的 Version

左侧点 **Profiles**，列表支持按用户 ID、Enabled、Revoked、Client Type 过滤。你会看到 QuickStart 的 Profile 401（Alice Reader）与 402（Alice Editor）。

点进一个 Profile 进 **Profile 详情**，会看到：

- 基本信息：Profile ID、User ID、Profile Name、绑定的 **Template Version**、Client Types、Enabled、Valid From / Until；
- 左栏 **Resource Scopes**：该 Profile 的资源范围行（Scope Type / Scope Ref ID / Access Mode）；
- 右栏 **Effective Permissions**：由绑定的 Template Version 计算出的最终权限 Code。

**完成检查**

- [ ] 打开 Profile 401，能看到它绑定 Version 301，Scope 为 `PROJECT / 101 / READ`，有效权限只有 `document:read`。

## 6. 编辑 Profile 与 Resource Scope（0.1.0 界面真实支持的写操作）

在 Profile 详情页，若你持有 `iam.admin.profile.write`，会出现两个按钮：

- **编辑 Profile**：可改 Client Types、Enabled、Valid From / Valid Until，然后「保存」；
- **编辑 Resource Scope**：以表格形式列出当前 Scope，可**添加一行 / 删除一行**，每行填 `Type`（如 `PROJECT`）、`Reference ID`（如 `101`）、`Access`（`READ` / `WRITE`），然后「保存」。

例如给 Alice Reader 增加对 Project 202 的读权限，就在 Scope 编辑器里加一行 `PROJECT / 202 / READ` 后保存。

> 注意：**Profile 详情里不能改它绑定的 Template Version**（编辑表单没有该字段）。要改变一个 Profile 引用的版本，需在别处（Seeder / Management API / 数据层）完成——当前 0.1.0 Console 不提供此操作。

**完成检查**

- [ ] 你能在 Profile 402 的 Scope 编辑器里看到 `PROJECT/101/READ` 与 `PROJECT/101/WRITE` 两行。

## 7. 让 alice 得到 / 改变权限的现实路径

到这一步你就明白了：**0.1.0 Console 是“查看 + 有限编辑”工具，不是“从零搭建授权”的构造器。** 如果你要从空库把 alice 配置成 Reader / Editor，当前版本的现实路径是：

1. **先有 Permission**：由宿主代码声明（本教程 §3）；
2. **先有 Template + PUBLISHED Version**：Console 不能新建/发布 → 用 QuickStart Seeder（`MUER_EXAMPLE_SEED_DEMO=true` 或 `examples/quickstart` 的 Seeder）一次性建好 201/301、202/302；
3. **建 Profile 并绑定 Version**：Console 不能新建 Profile → Seeder / Management API 建 401 / 402；
4. **加 / 改 Scope**：这里 **Console 可以做**——在 Profile 详情「编辑 Resource Scope」里增删行并保存（对应 `PUT /iam/admin/profiles/{profileId}/scopes`）；
5. **alice 重新登录**：拿新 Token 验证权限变化。

如果你要走的正是这篇的 QuickStart 场景，最快的是直接跑 QuickStart Seeder 拿到 401/402，再用 Console 的 Profile / Scope 编辑体验“改一下 scope 看结果”。

> 本教程不展开 Management API 的请求细节；需要时参考 [HTTP API](/reference/http-api/) 与 [Management API 相关页](/management/console/)。

**完成检查**

- [ ] 你能说清：Console 里能做的是“编辑 DRAFT 版本权限、编辑 Profile 的 client/enabled、编辑 Profile 的 Scope”；
- [ ] 你能说清：Console 里不能做的是“新建 Template / 发布 Version / 新建 Profile / 改 Profile 绑定版本”，这些在 0.1.0 走 Seeder 或 Management API。

## 8. 其它页面速览

- **Users / User Detail**：查看用户与 Identity，展示授权版本号。
- **Sessions**：查看会话，可撤销（`iam.admin.session.revoke`）。
- **Audit**：审计事件（登录、授权、Session 变更等）。
- **Diagnostics**：对**当前登录的管理员**跑一次授权诊断（与管理面资源类型相关），用于排查 Console 自身某页为何打不开。

## 9. 你现在学会了什么

```text
Permission  = 开发者代码声明（只读列表）
Template/Version = 管理员配置（0.1.0 Console 只能改 DRAFT 版本的权限）
Profile     = 管理员配置（0.1.0 Console 能编辑属性，不能改绑定版本）
Scope       = 管理员配置（0.1.0 Console 能增删行并保存）
从空库搭建授权 = 0.1.0 走 Seeder / Management API
```

用 Console 排查 403 的闭环见[排障](/operations/troubleshooting/)，接口细节见 [HTTP API](/reference/http-api/)。
