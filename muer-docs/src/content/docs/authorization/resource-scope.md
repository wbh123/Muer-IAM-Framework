---
title: 资源权限范围
description: 用真实资源树讲清 ResourceScope、ResourceDescriptor 与 ResourceHierarchyProvider 如何共同决定「一个请求落在哪片资源上」。
sidebar:
  order: 4
---

如果你正在第一次接入，请先读完[从零接入 Muer → 管理投影与资源](/getting-started/from-zero-tutorial/)。本页用一棵**能看见的资源树**讲清楚：为什么 Alice 有 `document:read` 权限，却不能读所有文档——因为还差一层 **Scope（资源作用域）**。

## 本页完成后你会得到什么

- 看到一张资源树时，能立刻判断给定 Scope 覆盖哪些叶子资源；
- 能说清 `ResourceScope` 的三个字段各是什么、为什么是这三个；
- 能说清 `ResourceDescriptor.parentPath` 是干嘛的、写成什么格式；
- 明白 `ResourceHierarchyProvider` 为什么存在、它的判断案例有哪些；
- 能用一句话回答「为什么 Document 1001 会被 Project 101 的 Scope 覆盖」。

## 从一个具体场景开始

Permission（如 `document:read`）回答**「能做什么」**。但系统里往往有成千上万份文档，Alice 能读文档 ≠ 能读所有文档。Scope 回答第二个问题：**「在哪些资源上做」**。

先看一棵真实的业务资源树：

```text
Company
└── Department 10
    ├── Project 101
    │   ├── Document 1001
    │   └── Document 1002
    │
    └── Project 102
        └── Document 2001
```

假设 Alice 的授权里带着这样一条 Scope：

```text
Scope：PROJECT / 101 / READ
```

不写代码，先凭直觉回答：Alice 能访问下面哪些资源？

| Alice 想访问 | 结果 | 为什么 |
| --- | --- | --- |
| Document 1001 | ✅ | 在 Project 101 下，且是读 |
| Document 1002 | ✅ | 同样在 Project 101 下，且是读 |
| Document 2001 | ❌ | 属于 Project 102，不在 Scope 内 |
| Document 1001（WRITE） | ❌ | Scope 只给了 READ，没有写 |

这就是 Scope 的全部直觉：**一个「在某类资源节点上的、限定了读写方式」的授权边界**。下面把「直觉」翻译成 Muer 的三个真实概念，逐一对应。

## 概念一：ResourceScope——这条边界长什么样

`ResourceScope` 是 Muer 表示一条作用域的 record，就三个字段：

```java
record ResourceScope(
        String scopeType,     // 哪种资源层级
        String scopeRefId,    // 哪一个具体节点
        ScopeAccess accessMode) { }  // READ / WRITE
```

| 字段 | 本页例子 | 含义 | 为什么需要它 |
| --- | --- | --- | --- |
| `scopeType` | `PROJECT` | 资源层级类型，例如 `PROJECT` / `DEPARTMENT` / `DOCUMENT` | 区分「作用在项目这一层」还是「作用在文档这一层」 |
| `scopeRefId` | `101` | 该层级里的具体节点标识 | 定位「是 101 这个项目，不是 202」 |
| `accessMode` | `READ` | `ScopeAccess.READ` 或 `WRITE` | 区分「只能读」还是「还能写」 |

`scopeType` 和 `scopeRefId` 拼起来，就是节点在资源树里的“地址”（`PROJECT:101`）；`accessMode` 决定这个地址上允许什么操作。三者缺一不可：只说 `PROJECT` 不知道是哪个项目，只说 `101` 不知道它是项目还是部门，只说 `READ` 不知道在哪里读。

## 概念二：ResourceDescriptor——请求正在访问哪个叶子节点

引擎收到一个 HTTP 请求时，它只知道 URL（如 `/api/documents/1001`），**不知道** 1001 属于哪个项目。它需要一个 `ResourceDescriptor` 来描述「当前这个请求指向哪片叶子、挂在哪个父节点下」：

```text
HTTP Request
GET /api/documents/1001
    ↓
DocumentService.find("1001")         // 宿主业务查询
    ↓
Document(projectId=101)              // 查到它属于 Project 101
    ↓
ResourceDescriptor(
  resourceType = "DOCUMENT",
  resourceId   = "1001",
  parentPath   = ["PROJECT:101"],    // 从下往上挂的父层级
  attributes   = {})
```

> 引擎**不会**自动去查你的文档表。`ResourceDescriptor` 是宿主（你的 Resolver）告诉 Muer：“这个请求正在访问 DOCUMENT / 1001，它属于 PROJECT:101。” 谁去查文档表、怎么查，是宿主自己的事。

`ResourceDescriptor` 的真实字段是 `(resourceType, resourceId, parentPath, attributes)`。前两个和 `ResourceScope` 的 `scopeType/scopeRefId` 用法一致；`attributes` 是可选附加信息；最需要理解的是 `parentPath`。

### parentPath：让「父层级 Scope」能盖住「子资源」

`parentPath` 记录这个资源**从下往上的父层级链**。例如一个文档既挂在项目下、又挂在部门下：

```text
ResourceDescriptor(
  resourceType = "DOCUMENT",
  resourceId   = "1001",
  parentPath   = ["PROJECT:101", "DEPARTMENT:10"])
```

它的作用正是让一个「项目级」或「部门级」的 Scope 也能命中这个文档：

```text
Scope PROJECT / 101 / READ    能否覆盖 DOCUMENT / 1001？
  → parentPath 含 "PROJECT:101"  → 是

Scope DEPARTMENT / 10 / READ  能否覆盖 DOCUMENT / 1001？
  → parentPath 含 "DEPARTMENT:10"  → 是（若实现也支持部门级）

Scope PROJECT / 202 / READ    能否覆盖 DOCUMENT / 1001？
  → parentPath 不含 "PROJECT:202"  → 否
```

`parentPath` 里每一项的格式是 `"类型:标识"`（`PROJECT:101`），与 Scope 的 `scopeType + ":" + scopeRefId` 对齐——这样引擎才能把两者拼起来比较。格式错（比如写成 `101` 或 `Project101`）会让 Scope 永远命不中，是排障时的高发点。

## 概念三：ResourceHierarchyProvider——谁来执行“在不在里面”

`parentPath` 只是数据，真正回答「这条 Scope 到底包不包含这个资源」的是宿主实现的一个接口：

```java
public interface ResourceHierarchyProvider {
    boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope);
}
```

它拿到「被访问的资源」和「用户的一条 Scope」，返回 true/false。一个能处理 `PROJECT → DOCUMENT` 两层结构的示例实现长这样：

```java
@Component
public class DocumentResourceHierarchyProvider
        implements ResourceHierarchyProvider {

    @Override
    public boolean isWithinScope(ResourceDescriptor resource, ResourceScope scope) {
        // 资源本身就是 Scope 指向的节点（如 Scope 直接写 DOCUMENT/1001）
        if (resource.resourceType().equals(scope.scopeType())
                && resource.resourceId().equals(scope.scopeRefId())) {
            return true;
        }
        // 否则看资源是否挂在 Scope 所指的父节点下
        return resource.parentPath().contains(
            scope.scopeType() + ":" + scope.scopeRefId());
    }
}
```

给四个判断案例（用上面的资源树）：

| 资源 | Scope | 结果 | 依据 |
| --- | --- | --- | --- |
| `DOCUMENT / 1001`，parent=`PROJECT:101` | `PROJECT / 101 / READ` | ✅ | parentPath 含 `PROJECT:101` |
| `DOCUMENT / 1001`，parent=`PROJECT:101` | `DOCUMENT / 1001 / READ` | ✅ | 资源类型与 ID 直接匹配 Scope |
| `DOCUMENT / 1001`，parent=`PROJECT:101` | `PROJECT / 202 / READ` | ❌ | parentPath 不含 `PROJECT:202` |
| `DOCUMENT / 1001`，parent=`PROJECT:101` | `DEPARTMENT / 10 / READ` | ❌/✅ | 取决于 parentPath 是否含 `DEPARTMENT:10`（本例不含则 ❌） |

:::note[为什么引擎自己不实现层级判断]
Muer 不知道你的资源长什么样：项目下有没有部门、文档能不能属于多个项目，只有宿主清楚。所以 Muer 把“资源之间的包含关系”留给宿主的 `ResourceHierarchyProvider` 回答。真实系统可以查组织、租户、项目或目录关系，但**必须返回确定性结果**，不能一次 true 一次 false。
:::

## 谁创建 ResourceDescriptor

不是引擎，也不是 Muer 自动推断，而是宿主的 **Resolver**。在 Spring MVC 场景下你实现 `MvcResourceDescriptorResolver`，从请求路径变量里读出文档 id、查出它属于哪个项目、构造 `ResourceDescriptor`。详见[MVC 资源描述解析器](/authorization/mvc-resource-descriptor-resolver/)。

## 与 Permission 的关系：AND

Scope **不能替代 Permission**，两者是 AND 关系，缺一不可：

```text
GET Document 1001        document:read ✅   PROJECT 101 READ ✅   → ALLOW
POST Document 1001       document:update ❌ PROJECT 101 WRITE ?   → DENY（缺能力）
GET Document 2001        document:read ✅   PROJECT 202   ❌      → DENY（超出资源）
GET Document 1001(WRITE) document:update ✅ PROJECT 101 WRITE ❌  → DENY（Scope 只给 READ）
```

> Scope 是用来**收窄**授权边界的。不要为了图省事用宽泛 Scope 绕过 Permission 检查——能力与范围必须同时成立。

## 一种特别的 Scope：管理资源由 Starter 内置处理

Muer 自己的 Management API 使用的资源（`IAM_ADMIN/*`、`IAM_*`）层级，由 Starter 内置的规则解释，宿主**不需要**为这些 Muer 管理资源写 `ResourceHierarchyProvider`。宿主的 Provider 只负责你的业务资源（`PROJECT`、`DOCUMENT`、`DEPARTMENT` 等）。`IAM_ADMIN/*` 不会自动覆盖 `DOCUMENT/1001` 这类宿主资源。见[初始化第一个管理员](/management/bootstrap-first-admin/)。

## 完整接口定义见参考

本页只解释概念与链路，字段与签名清单见 [Resource Scope Reference](/authorization/authorization-engine/) 的模型与[公共 API 参考](/reference/public-api/)。

## 完成检查

```text
□ 我能用资源树说出：PROJECT/101/READ 覆盖 1001、1002，不覆盖 2001
□ 我能说出 ResourceScope 三字段：scopeType/scopeRefId/accessMode 各是什么
□ 我能解释 parentPath 为什么写成 "PROJECT:101" 而不是别的格式
□ 我的 ResourceHierarchyProvider 对同一资源+Scope 返回确定结果
□ 一个同时缺 Permission 或 Scope 的请求，我能判断该看哪一环
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 有 `document:read` 但请求仍 403 `SCOPE_DENIED` | Profile 的 Scope 没覆盖该资源，或 `accessMode` 是 READ 而请求要 WRITE。 |
| `PROJECT / 101` 怎么也盖不住 `DOCUMENT / 1001` | Resolver 构造的 `parentPath` 少了 `PROJECT:101`，或格式写成 `101`。 |
| 想按部门授权却始终失效 | 检查你给文档的 `parentPath` 是否真的包含 `DEPARTMENT:10`，且 hierarchy 支持部门级判断。 |
| 开了很宽的 Scope（如 `* / * / WRITE`） | 权限面过大。Scope 应与实际资源树层级匹配，避免绕过 Permission 检查。 |
| 为 Muer 的 `IAM_ADMIN/*` 自己写 hierarchy | 不需要。管理资源层级由 Starter 内置；宿主只声明业务资源。 |

## 下一步

- [MVC 资源描述解析器](/authorization/mvc-resource-descriptor-resolver/)——Resolver 怎么构造 ResourceDescriptor。
- [授权引擎](/authorization/authorization-engine/)——Scope 检查在整条决策链的第几步。
- [权限管理](/getting-started/permission-management/)——Scope 怎么在完整链路里被组合进 Profile。
