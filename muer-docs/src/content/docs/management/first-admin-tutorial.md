---
title: 第一次使用 Muer Admin Console
description: 按当前界面创建 Template、Version 与 Profile，并维护 Scope。
---

本页按当前 0.1.0 Console 的真实能力操作。生产空库请先完成[第一个管理员 Bootstrap](/management/bootstrap-first-admin/)；开发演示也可以使用 `muer-example` 的 dev-only 管理员。

## 1. 权限与配置的分工

```text
开发者代码                         管理员 Console
PermissionDefinitionProvider       创建 Permission Template
  → document:read                    → 创建 DRAFT Version
  → document:update                  → 选择 Permission 并发布
                                      → 创建 Profile 并绑定 Version
                                      → 创建和维护 Scope
```

Permission 页面只读，因为业务 Permission 必须由开发者在代码中声明。Template、Version、Profile 与 Scope 才是管理员维护的授权投影。

## 2. 启动并登录

Console 是独立静态前端，后端由宿主应用提供：

```bash
cd muer-admin-web
npm ci
npm run dev
```

默认打开 `http://localhost:5173`。使用宿主系统已有、并已通过 Bootstrap 获得 `iam.admin.*` 的账号登录。

仅做仓库本地演示时，可以按 `muer-example` 自身说明开启 `dev` Seeder，使用 `admin-demo / demo-pass / WEB`。这不是生产 Bootstrap，也不会成为默认生产账号。

## 3. 查看 Permission Registry

打开 **Permissions**，确认宿主 `PermissionDefinitionProvider` 注册的 Permission，例如 `document:read`、`document:update` 与 `iam.admin.*`。

这里没有新建或删除 Permission 的按钮。缺少 Permission 时应修正宿主 Provider 并重启应用，而不是写数据库。

## 4. 创建 Template

打开 **Templates**，点击 **新建模板**，填写：

- `Template Key`：稳定、唯一的业务 Key；
- `Name`：管理员可读名称；
- `Description`：可选说明。

创建成功后 Console 会进入 Template 详情页。

## 5. 创建并发布 Version

在 Template 详情点击 **新建版本**：

1. 从 Permission Registry 多选该版本包含的 Permission；
2. 创建 DRAFT；
3. DRAFT 阶段可继续编辑 Permission；
4. 核对无误后点击 **发布** 并确认。

发布后 Version 不可变，Console 也会只读展示。后续变更应创建新 DRAFT，而不是修改已发布版本。

## 6. 创建 Profile

打开 **Profiles**，点击 **新建 Profile**，选择或填写：

- User：来自当前宿主用户列表；
- Profile Name；
- Published Version：只能选择已发布版本；
- Client Types；
- Enabled；
- 可选的初始 Resource Scopes。

提交后 Profile 会绑定选中的 PUBLISHED Template Version。Console 当前支持从零创建 Profile，不需要 Seeder 或手写 SQL。

## 7. 查看和编辑 Profile

进入 Profile 详情可以看到：

- User ID 与绑定的 Template Version；
- Client Types、Enabled 与有效时间；
- Resource Scopes；
- 由 Template Version 计算出的 Effective Permissions。

持有 `iam.admin.profile.write` 时，可以编辑 Client Types、Enabled、有效时间和 Scope。Scope 编辑器支持添加/删除 `Type / Reference ID / Access` 行。

当前 Profile 详情编辑器**不能把已有 Profile 切换到另一个 Template Version**。需要版本升级时使用 Management API 或创建新的 Profile；不要直接改表。

## 8. 验证授权结果

让目标用户重新登录获得与当前授权投影一致的新 Token，然后验证：

1. 一个应允许的业务请求返回 `200`；
2. 一个缺 Permission 或 Scope 的请求返回 `403`；
3. Diagnostics 能解释拒绝步骤；
4. 审计页能看到对应管理操作。

## 9. 当前 Console 能力清单

| 能力 | 当前状态 |
| --- | --- |
| 查看 Permission Registry | 支持，只读 |
| 创建 Template | 支持 |
| 创建 DRAFT Version | 支持 |
| 编辑 DRAFT Permission | 支持 |
| 发布 Version | 支持 |
| 修改 PUBLISHED Version | 不支持，设计上不可变 |
| 创建 Profile 并绑定 PUBLISHED Version | 支持 |
| 编辑 Profile 属性与 Scope | 支持 |
| 切换已有 Profile 的 Template Version | 当前详情页不支持 |
| 查看/撤销 Session、查看 Audit、运行 Diagnostics | 支持，取决于管理员权限 |

所有按钮与路由守卫只改善体验；真正的安全边界始终是后端对 `/iam/admin/**` 的 `iam.admin.*` 校验。
