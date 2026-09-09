---
title: 权限管理
description: 从代码里声明的 document:read，到 Alice 真正拿到 Document 1001 的访问权限——理解 Permission、Template、Version、Profile、Scope 的完整链路，并用管理控制台走通它。
sidebar:
  order: 5
---

如果你是从零开始，建议先读完[从零接入 Muer](/getting-started/from-zero-tutorial/)。本页回答一个具体问题：

> **代码里声明了 `document:read`，Alice 要怎样才能真的读到 Document 1001？**

答案不是「Alice 属于管理员角色」或「给 Alice 打勾一个权限」。Muer 用一条**分层的授权投影**把「系统有什么能力」和「某个用户拿到哪些能力」分开。理解这条链路，后面所有 403 都能自己定位。

---

## 本页完成后你会得到什么

- 能说清 `Permission → Template → Version → Profile → Scope` 每一步的存在意义；
- 明白为什么 **Permission 必须由代码声明**、管理员不能后台随手造；
- 能用 Admin Console（或 Management API）为 Alice 建好一套可用的 Reader 授权；
- 能用统一的**最终授权 = Permission AND Scope AND 其他约束**模型判断一个请求为何被允许/拒绝；
- 有一份可复现的「完成检查」与常见错误清单。

## 为什么需要「权限管理」这一层

`document:read` 只是**系统有能力做某件事**，它不等于**谁被允许做这件事**。真正上线后你需要处理两类完全不同的需求：

| 需求 | 谁来回答 | 会变化多频繁 |
| --- | --- | --- |
| 系统有没有「读文档」这个能力？ | 开发者（代码） | 几乎不变，随版本发布 |
| Alice 能不能读 Project 101 里的文档？ | 管理员（运行时） | 常变，随人员/项目调整 |

如果只把权限当成「给用户打勾」，那么 100 个能读文档的人就要维护 100 份清单；哪天想把「读」升级成「读 + 下载 + 评论」，你得改 100 处。权限管理这一层把「**能力组合**」抽成可复用的模板，把「**谁在用哪套组合**」抽成 Profile，把「**在哪些资源上**」抽成 Scope——让一次修改能作用于成组的人，而不是逐人维护。

---

## 贯穿案例

本页与全站教程统一使用同一个例子：

```text
用户：     Alice（宿主用户 id = 101）
项目：     Project 101
文档：     Document 1001（属于 Project 101）
权限码：   document:read、document:update
目标：     让 Alice 只能读（不能改）Project 101 里的文档
```

后面所有的模板名、Profile 名、Scope 都围绕这个例子，不再切到 Order / HR / Finance 等别的领域。

---

## 为什么 Permission 必须由代码声明

`document:read` 是**应用代码本身的一项能力**。它出现在哪、做什么，只有代码知道。

> 如果允许管理员在后台随便造一个 `document:super-read`，但业务代码里从没使用过这个 Permission，那么这个权限没有任何意义——没有任何接口会被它「打开」。

职责因此清晰分成两半：

```text
开发者（Developer）
  → 用 PermissionDefinitionProvider 定义系统有哪些能力
  → 用 @RequirePermission 声明哪些接口需要哪个能力

管理员（Administrator）
  → 决定谁获得哪些已声明的能力
  → 组合模板、发布版本、分配 Profile 与 Scope
```

所以 `PermissionDefinitionProvider` **不等于给用户授权**：它只注册能力目录。注册后 Muer 不会自动创建任何 Template / Profile，也不会给任何人放行（见下方链路）。

## 谁是「声明 Permission 的代码」

在宿主应用里加一个 `@Configuration`，返回一个提供 `PermissionDefinition` 列表的 Bean：

```java
@Configuration(proxyBeanMethods = false)
public class MuerPermissionConfiguration {

    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
            new PermissionDefinition(
                "document:read",        // 稳定机器代码：业务与接口共同引用的唯一标识
                "Read a document",      // 管理界面显示名称
                "Read a document inside a project"), // 给管理员看的说明
            new PermissionDefinition(
                "document:update",
                "Update a document",
                "Update a document inside a project"));
    }
}
```

字段含义以真实 API 为准：`PermissionDefinition(code, displayName, description)`。`code` 用 `资源:动作` 的冒号小写约定（`document:read`），最长 191 字符，作为跨层引用的唯一 Key。

启动时 Muer 把这些定义注册进 Permission Registry。打开 Admin Console 的 **Permissions** 页就能看到它们——这一页是**只读**的，因为业务能力只能由代码声明。

> 想在 Registry 里多一个能力？改 `MuerPermissionConfiguration` 并重启应用，而不是直接插数据库。否则你只是造了一个「从未被任何接口使用」的孤儿码。

## 关键值从哪里来：Registry 里的 `document:read`

`code` 是贯穿全链的“接头暗号”：

```text
PermissionDefinition("document:read", ...)     // 代码声明
@RequirePermission("document:read")            // Controller 声明接口需要它
Template Version 里勾选 document:read          // 管理员把能力放进套餐
Profile → 该 Version → 授权生效                // Alice 拿到这套能力
```

四处以同一个字符串 `document:read` 相连。任何一处打错（例如 Provider 里是 `document:Read`、接口里是 `document:read`），授权就永远不命中。

---

## 完整链路：从 `document:read` 到 Alice 真的能读

把权限管理理解成「**能力套餐的组装与分发**」。一条完整链路是：

```text
① Permission Registry 出现 document:read      （代码已声明，应用已重启）
        ↓
② 创建 Permission Template  "Document Reader"
        ↓
③ 创建 V1 DRAFT（草稿版本）
        ↓
④ 把 document:read 加入 V1 DRAFT
        ↓
⑤ 发布 V1 → PUBLISHED（发布后不可改）
        ↓
⑥ 创建 Profile "Alice Reader"，绑定用户 Alice(101)
        ↓
⑦ 把 Profile 绑定到 PUBLISHED V1
        ↓
⑧ 给 Profile 加 Scope：PROJECT / 101 / READ
        ↓
⑨ Alice 重新登录（拿到指向 Profile 的新 Token）
        ↓
⑩ GET /api/documents/1001 → 200
```

下面逐段解释每一层为什么存在、在哪里完成。前五步（①~⑤）走一次「发布一套能力」；后五步（⑥~⑩）把能力分给具体的人。

### 为什么要有 Template：能力套餐

如果 100 个用户都需要 `document:read + document:comment + document:download`，你不该给 100 个用户各维护三行权限。

`PermissionTemplate` 是一个**可复用的权限组合定义**——「文档读者」应该能干什么，定义一次，反复使用：

```text
Template "Document Reader"
  像产品名称：一个稳定的、可读的业务 Key
```

创建它只需要一个稳定 `Template Key`、一个可读 `Name`、一段可选描述。真正放哪些 Permission 不直接写在 Template 上，而写在它的版本里。

### 为什么还要有 Template Version：已发布套餐不能静默改

Template 是一套能力的“产品线”，而 `TemplateVersion` 是它的一次**正式发布**。用类比：

```text
Template           = 产品名称（Document Reader）
Template Version   = V1 / V2（一次正式发布）
```

```text
"Document Reader" Template

  V1  PUBLISHED
    ├── document:read
    └── document:download

  V2  DRAFT（正在编排，还没发布）
    ├── document:read
    ├── document:download
    └── document:comment
```

**已经 PUBLISHED 的 Version 不可修改**。原因很直接：如果有人已经在用 V1，而你直接在 V1 上加权限，现有用户的权限会在**没有任何审计的情况下被静默改变**。安全做法是：

1. 从 V1 派生一个 V2 DRAFT；
2. 在 DRAFT 里调整 Permission；
3. 想清楚影响后**发布** V2；
4. 需要的人再切换到 V2。

所以「发布」是一个**不可逆的版本固化动作**：DRAFT 随便改，PUBLISHED 只能看。

### 为什么要有 Profile：某个具体用户正在用哪套身份

Template / Version 是**通用套餐**，谁都能引用。但「Alice 现在实际采用哪套」需要一个归属到具体用户的记录——这就是 **Profile（授权档案）**。

```text
Profile = 某个用户实际采用的一份授权身份

"Alice Reader"
  ├── userId          = 101（宿主用户 Alice）
  ├── Template Version= "Document Reader" V1（PUBLISHED）
  └── Scope           = PROJECT / 101 / READ
```

一句话区分：

```text
Template = 通用权限套餐（不绑定人）
Profile  = 某个具体用户正在使用的一份授权身份（绑定 userId）
```

一个宿主用户可以有多个 Profile（例如「Alice Reader」「Alice Editor」），但**同一时间登录只带一个**——这就是 `IamPrincipal.activeProfileId`。切换 Profile 就等于切换 Alice 这次会话采用的授权身份。

### 为什么还要有 Scope：在哪片资源上

`document:read` 告诉引擎「要的是读文档能力」，但 Alice 有读权限不等于能读**所有**文档。Scope 把授权限定到具体资源节点：

```text
Scope "PROJECT / 101 / READ"
  scopeType    = PROJECT    （哪种资源层级）
  scopeRefId   = 101        （哪一个具体节点）
  accessMode   = READ       （只读，不含写）
```

对每个受保护请求，最终判断是：

```text
最终授权 = Permission 匹配（能做什么）
         AND Resource Scope 匹配（在哪个资源上做）
         AND 其他安全约束（身份域、客户端类型、Profile 是否有效等）
```

任何一个 AND 条件不满足都拒绝：

```text
GET Document 1001        document:read ✅   PROJECT 101 ✅   → ALLOW
POST Document 1001       document:update ❌ PROJECT 101 ✅   → DENY（缺能力）
GET Document 2001        document:read ✅   PROJECT 202 ❌   → DENY（超出资源）
```

关于 Scope 的层级与覆盖（为什么 `PROJECT / 101` 能盖住 `DOCUMENT / 1001`），见[资源作用域](/authorization/resource-scope/)。

---

## 在哪里完成这些步骤

- **代码能做的**（声明能力、标注接口）：见[定义权限](/getting-started/define-permissions/)与[从零接入](/getting-started/from-zero-tutorial/)。
- **运行时由管理员做的**（①~⑧ 的模板/版本/Profile/Scope）：用 **Admin Console** 或 **Management API**。上手操作见[第一次使用 Admin Console](/management/first-admin-tutorial/)。
- **本地学习快速预览**：Quick Start Seeder 会预置一套 Reader/Editor 演示数据，省去手工点按；但**生产空库不能开启 Seeder**，第一个管理员必须走显式 [Bootstrap](/management/bootstrap-first-admin/)。

开发者通常不需要重复实现权限管理后端——Muer 已经随 Starter 提供 Management API 与可选控制台。宿主也可以基于同一份 OpenAPI 生成自己的管理界面。

:::caution[不要直接写表]
不要直接写 `iam_*` 表、MyBatis mapper 或 Redis Key。直接写入会绕过授权版本、审计与兼容性边界。模板、版本、Profile、Scope 一律通过 Management API / Console / 公开应用 SPI 操作。
:::

---

## 完成检查

在浏览器里完成①~⑧并用 Alice 登录后，逐项核对：

```text
□ Permissions 页能看到 document:read 与 document:update（来自代码注册）
□ Templates 页存在 "Document Reader"，且有 V1 PUBLISHED（不是 DRAFT）
□ Profiles 页存在 "Alice Reader"，绑定用户 101 与 PUBLISHED V1
□ "Alice Reader" 的 Scope 含 PROJECT / 101 / READ
□ Alice 用新 Token 调 GET /api/documents/1001 → 200
□ Alice 调 POST /api/documents/1001 → 403（无 document:update）
□ Alice 调 GET /api/documents/2001 → 403（超出 PROJECT 101 Scope）
□ Diagnostics 能解释最后一次 403 是 PERMISSION_DENIED 还是 SCOPE_DENIED
```

如果 ⑤ 的 V1 仍是 DRAFT 状态，Profile 在创建时通常无法选中它（只能绑 PUBLISHED）——这是常见的卡点。

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| Registry 里没有 `document:read` | Provider 没声明或没重启。修正 `MuerPermissionConfiguration` 后重启应用。 |
| Console 里造了个 `document:super-read`，接口却永不放行 | 代码里没有该能力。Permission 必须由 `PermissionDefinitionProvider` 声明，管理员只负责分发已声明能力。 |
| Profile 建不了、选不到 Version | 你试图绑定一个 DRAFT。Profile 只能绑定 **PUBLISHED** Version，先把版本发布。 |
| Alice 仍 403 `PERMISSION_DENIED` | Alice 的 Profile 绑定版本里没勾选该 Permission，或她带了错误的 `activeProfileId`。 |
| Alice 仍 403 `SCOPE_DENIED` | 能力有，但 Scope 没覆盖目标资源。检查 accessMode（READ vs WRITE）与 `scopeRefId` 是否为 101。 |
| 想改已发布套餐的权限 | 不能改 PUBLISHED。派生新 DRAFT → 调整 → 发布 V2 → 需要的人切到 V2。 |
| 想让改动立即对旧 Token 生效 | 改动授权投影后，旧 Token 可能仍引用旧 Profile。按需提升 `authorizationVersion` 或撤销会话，见[授权版本](/concepts/authorization-version/)。 |

## 下一步

- [资源作用域](/authorization/resource-scope/)——Scope 的层级与“为什么 PROJECT 能盖住 DOCUMENT”。
- [授权引擎](/authorization/authorization-engine/)——真实决策每一步怎么走、怎么读 decision steps。
- [第一次使用 Admin Console](/management/first-admin-tutorial/)——本页链路在界面里逐点操作。
