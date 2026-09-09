---
title: 把请求映射成业务资源
description: 为什么需要 Resolver、它如何把请求翻译成 ResourceDescriptor、parentPath 怎么填，以及常见的解析失败。
sidebar:
  order: 3
---

如果你正在第一次接入，先读完[从零接入 → 把 HTTP 请求描述成资源](/getting-started/from-zero-tutorial/)。本页把「Resolver 为什么存在、每一步代码在干什么、parentPath 填错会怎样」讲透。

## 本页完成后你会得到什么

- 能说出：**Muer 不会自动查询你的业务表**，Resolver 是宿主告诉 Muer“这个请求在访问什么业务资源”的唯一入口；
- 能读懂一份 `MvcResourceDescriptorResolver` 实现，并把它的逻辑拆成四块；
- 知道 `parentPath` 该填成什么格式、为什么那样填；
- 知道解析不到资源时会发生什么（HTTP 404 等）。

## 为什么需要 Resolver

授权引擎要判断“Alice 能不能读这个文档”，它需要知道**这个请求正在访问哪个具体资源、它挂在哪个父节点下**。但 HTTP 请求只有 URL：

```text
GET /api/documents/1001
```

引擎从这个 URL 里**看不出** 1001 属于哪个 Project。谁来补上这一步？Muer 不会——它不碰你的文档表。**Resolver 就是那个补位的人**：

```text
HTTP Request
GET /api/documents/1001
    ↓
Resolver 调宿主 DocumentService.find("1001")
    ↓
Document(projectId=101)
    ↓
ResourceDescriptor("DOCUMENT", "1001", parentPath=["PROJECT:101"])
    ↓
交给 AuthorizationEngine（结合 Profile 的 Scope 判断）
```

一句话：**Resolver 是宿主告诉 Muer“这个 HTTP 请求正在访问什么业务资源”。** 没有它，引擎无法做资源级（Scope）判断。

## 接口签名

`MvcResourceDescriptorResolver` 只有一个方法：

```java
Optional<ResourceDescriptor> resolve(HttpServletRequest request,
                                     HandlerMethod handlerMethod);
```

- 能解析出业务资源 → `Optional.of(resourceDescriptor)`;
- 解析不到（如资源不存在、路径变量缺失）→ `Optional.empty()`。

## 一份实现，拆成四块看

以 Quick Start 的 `DocumentResourceResolver` 为例。别把它当一整段背，它其实只有四块：

```java
@Component
public class DocumentResourceResolver implements MvcResourceDescriptorResolver {
    private final DocumentService documents;   // 宿主业务服务

    public DocumentResourceResolver(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public Optional<ResourceDescriptor> resolve(
            HttpServletRequest request, HandlerMethod handlerMethod) {

        // ① 从 Spring MVC 请求属性里读取路径模板变量 {id}
        //    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE 存放的是
        //    URL 里被 @PathVariable 捕获的变量（本例即 /api/documents/{id} 的 id）。
        Object value = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> variables)) return Optional.empty();
        Object id = variables.get("id");
        if (!(id instanceof String documentId)) return Optional.empty();

        // ② 用 documentId 去查宿主业务数据（DocumentService 是宿主自己的仓储）
        return documents.find(documentId).map(document ->
            // ③ 构造 Muer 能理解的资源描述
            new ResourceDescriptor(
                "DOCUMENT",                                   // resourceType：资源层级类型
                document.id(),                                // resourceId：具体叶子节点 ID
                List.of("PROJECT:" + document.projectId()),   // ④ parentPath：挂到哪个父节点
                Map.of())                                     // attributes：可选附加信息
        );
        // ② 查询返回 empty → 文档不存在 → 整个方法返回 Optional.empty()
    }
}
```

四块各自的任务：

```text
① 从 Spring MVC 读取路径变量        —— 拿到 documentId
② 查询宿主业务数据                 —— 找到它属于哪个 Project
③ 构造 ResourceDescriptor          —— 类型 + ID
④ 填 parentPath                    —— 声明它挂在哪个父节点下（见下）
```

:::note[关键认知]
Muer **不会**自动查询 Document 数据库。即使你的 `DocumentService` 换成真实仓储、里面是 Oracle/Postgres，Resolver 的职责不变：**把“这个请求在访问哪片业务资源”告诉 Muer**。业务查询逻辑永远是宿主自己的。
:::

## parentPath 单独讲清楚

`parentPath` 是新手最常填错、却最关键的一个字段。它的作用：**让一个「父层级」的 Scope 也能盖住这个子资源**。

它是一串 `"类型:标识"` 的列表，表示这个资源**从自身往上的父层级**。例如：

```text
ResourceDescriptor(
  resourceType = "DOCUMENT",
  resourceId   = "1001",
  parentPath   = ["PROJECT:101", "DEPARTMENT:10"])
```

含义：Document 1001 属于 `PROJECT:101`，而 `PROJECT:101` 又挂在 `DEPARTMENT:10` 下。这样引擎看到 Scope `PROJECT / 101 / READ` 时，会在 `parentPath` 里找 `"PROJECT:101"`，找到了就说明这条 Scope 覆盖该文档。

为什么是 `"类型:标识"`（`PROJECT:101`）而不是别的写法？因为它是用来和 Scope 的 `scopeType + ":" + scopeRefId` **逐字对拼**的。Scope 是 `("PROJECT","101",READ)`，那么比较字符串就是 `"PROJECT:101"`。你填 `"101"`、`"Project101"`、`"PROJECT-101"` 都对不上，Scope 就永远不命中。

> 若层级有真实顺序语义（如 DEPARTMENT 在 PROJECT 之上），以真实实现为准；示例里 `PROJECT → DOCUMENT` 两层，`parentPath` 通常只放 `PROJECT:xxx` 这一层就够。

## 失败语义

| 情况 | 结果 | 说明 |
| --- | --- | --- |
| 没注册任何 Resolver，但接口被 `@RequirePermission` 保护 | 500 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | 引擎无法解析业务资源 |
| Resolver 返回 `Optional.empty()` | 404 `IAM_RESOURCE_NOT_FOUND` | 该资源被视为不存在/不可解析 |
| Resolver 抛异常 | 视处理而定 | 应捕获并返回 empty 或让宿主异常处理器处理 |

## 完成检查

```text
□ 我能说出 Resolver 在授权链中发生在 Scope 判断之前
□ 我能在代码里指出“读路径变量 / 查业务数据 / 构造资源”这三处
□ 我给 document 填的 parentPath 是 "PROJECT:101" 这类格式，与 Scope 的 type:id 能对拼
□ 我的业务里不存在某文档时，Resolver 返回 Optional.empty()（→ 404），而不是造一个假资源
□ 若文档可能跨项目/多父，我已想清 parentPath 应含哪些父节点
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 有 Resolver 但一直 500 `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | Bean 类型/包名写错没被注册；确认实现类被 Spring 扫描且实现接口正确。 |
| 一直 404 `IAM_RESOURCE_NOT_FOUND` | Resolver 返回了 empty。检查读到的路径变量名是否与 `@PathVariable` 一致、业务查询是否真有数据。 |
| `PROJECT/101` 的 Scope 盖不住文档 | `parentPath` 填错格式（如 `101`）或漏了 `PROJECT:101` 这一层。 |
| 文档挂在多父/多项目下判断不对 | 把该资源的所有父层级都放进 `parentPath`，并在 `ResourceHierarchyProvider` 里按确定规则判断。 |

## 下一步

- [资源作用域](/authorization/resource-scope/)——parentPath 如何参与 Scope 覆盖判断。
- [资源层级 Provider](/authorization/authorization-engine/)——`isWithinScope` 怎么消费 Resolver 产出的 ResourceDescriptor。
- [从零接入 → 资源与授权](/getting-started/from-zero-tutorial/)——整条链路的完整实现。
