---
title: 用户如何获得权限
description: 用一个固定案例讲清 Permission、Permission Template、Template Version、Profile 与 Scope 五个概念如何串起来，最终决定一个请求是否被允许。
---

如果一个新人只读一页来理解 Muer，那就是这一页。本页用**固定的案例**把 Muer 授权的五个核心概念串成一条链，解释「一个业务请求最终为什么被放行或拒绝」。

> 固定案例贯穿全文：
> - **Alice**（用户）
> - **Project 101**（项目）与 **Project 202**（另一个项目）
> - **Document 1001**（属于 Project 101 的文档）
>
> 这与 [10~15 分钟快速开始](/getting-started/quick-start/) 里的种子数据一致。

## 从「业务代码要什么」说起

业务接口只声明一件事：**这个操作需要什么能力**。

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")        // 读文档需要这个能力
public Document get(@PathVariable String id) { ... }

@PostMapping("/api/documents/{id}")
@RequirePermission(value = "document:update", access = WRITE)  // 改文档需要这个能力
public Document update(...) { ... }
```

`document:read`、`document:update` 就是 **Permission**。代码只负责「开口」——真正的放行与否由后面这条授权链决定。

## 一条链：从 Permission 到 Scope

```text
业务代码需要的能力
│
├── document:read
└── document:update
        │
        ▼
Permission（系统能做什么）
        │
        ▼
Permission Template（把一组能力打成可复用套餐）
  例如：Document Reader = { document:read }
        │
        ▼
Template Version V1 · PUBLISHED（套餐的一次正式发布）
  例如：V1 = { document:read }
        │
        ▼
Alice 的 Profile（某个具体用户采用的授权身份）
  例如：alice-reader-project-101
        │
        ├── 绑定 Template Version V1  →  因此 Alice 拥有 document:read
        │
        └── 绑定 Scope                →  因此 Alice 只能在这些资源上使用该能力
              PROJECT / 101 / READ
```

一句话记法：

| 概念 | 一句话 |
| --- | --- |
| **Permission** | 系统能做什么（如 `document:read`） |
| **Template** | 把一组 Permission 打成可复用套餐 |
| **Template Version** | 套餐的一次正式发布（DRAFT / PUBLISHED / RETIRED） |
| **Profile** | 某个具体用户采用的授权身份，绑定一个已发布 Version + 一组 Scope |
| **Scope** | 限制这些能力可以作用在哪些资源上 |

## 为什么需要五层，而不是一个 Role

如果你只有「管理员 / 普通用户」两个角色，Muer 这五层确实偏重。但一旦需要表达：

- 同一个 Alice，在 **Project 101** 是只读，换到 **Project 202** 可能没有权限；
- 同一个用户，今天只能读，明天被授权成能写，**且改动要有审计与回滚**；
- 不能因为「写了 `document:update`」就默认他能改系统里所有文档。

你就需要把「能做什么」（Permission）和「能在哪些资源上做」（Scope）分开，并且让授予关系（Template Version → Profile）可以被版本化、可审计、可回滚。

## 最终授权例子：三种请求三种结果

授权引擎把 **Permission 与 Scope 分开判断，两者同时满足才放行**。

### 例一：读自己项目内的文档 → 允许

```
GET Document 1001   （属于 Project 101）

Permission：document:read        ✅  Alice 的 Profile 有这个能力
Scope：    PROJECT / 101 / READ  ✅  1001 落在 Project 101 内，且是读

结果：ALLOW
```

### 例二：改文档但只被授予了读 → 拒绝

```
POST Document 1001   （属于 Project 101）

Permission：document:update      ❌  Alice 的 Profile 只有 document:read

结果：DENY
```

### 例三：有读权限但不在授权范围内 → 拒绝

```
GET Document 2001    （属于 Project 202）

Permission：document:read        ✅  Alice 有这个能力
Scope：    Project 202           ❌  Alice 的 Scope 只覆盖 Project 101

结果：DENY（SCOPE_DENIED）
```

这三例说明：**Permission 决定「能不能做」，Scope 决定「能在哪些资源上做」**。缺了任一环都会拒绝，这正是 Muer 授权模型的整个核心。

## 下一步

- 想动手跑通这条链：先读 [10~15 分钟快速开始](/getting-started/quick-start/)。
- 深入单个概念：[Permission](/concepts/permission/)、[权限模板](/concepts/permission-template/)、[Profile](/concepts/profile/)、[资源与权限范围](/concepts/resource-scope/)。
- 想理解引擎到底按什么顺序判断：[授权决策流程](/authorization/authorization-engine/)。
