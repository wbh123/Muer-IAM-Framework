---
title: 自定义授权规则
description: 区分两层——引擎的“策略层”（AuthorizationPolicy 扩展）与 MVC 拦截器的“HTTP 错误映射”，并用真实案例看懂 403 之后发生了什么。
sidebar:
  order: 6
---

如果你正在排查一个 403，本页帮你分清“**引擎判断**”和“**HTTP 呈现**”是两回事：引擎负责能不能放行，拦截器负责把它翻译成 `401/403/404/500` 和机器可读的错误码。

## 两个层次，别混淆

```text
引擎层（能放行吗？）
  DefaultAuthorizationEngine.decide(...)
    → 身份域 → 客户端 → Profile → Permission → Scope
    → 再到宿主注入的扩展策略（AuthorizationPolicy）逐条评估
    → 产出 AuthorizationDecision

拦截层（怎么告诉客户端？）
  IamAuthorizationInterceptor
    → 把“无主体 / 解析失败 / 决策拒绝”翻译成 HTTP 状态 + application/problem+json
```

- **引擎层**决定语义：`decisionCode`（如 `PERMISSION_DENIED`、`SCOPE_DENIED`）；
- **拦截层**决定呈现：HTTP 码（401/403/404/500）与 `IAM_*` 错误码。

一句话：**引擎说“为什么不行”，拦截器说“用哪个 HTTP 码告诉你不行”。**

## 内置错误码映射（拦截层）

`IamAuthorizationInterceptor` 先解析 `@RequirePermission`（方法优先于类），随后按固定顺序短路：

| HTTP | ProblemDetail code | 含义 |
| --- | --- | --- |
| 401 | `IAM_UNAUTHENTICATED` | 请求没带有效登录主体 |
| 500 | `IAM_RESOURCE_RESOLUTION_UNAVAILABLE` | 接口被保护但没有可用的 Resolver |
| 404 | `IAM_RESOURCE_NOT_FOUND` | Resolver 返回 empty，资源视为不存在 |
| 403 | `IAM_ACCESS_DENIED` | 引擎决策拒绝（具体原因看 `decisionCode`） |

`IAM_ACCESS_DENIED` 只说明“被拒”，**具体卡在哪一步由引擎的 `decisionCode` 进一步回答**——例如 `SCOPE_DENIED` 表示权限有但超出资源范围，`PERMISSION_DENIED` 表示根本没这个权限。

## 扩展策略层：AuthorizationPolicy

默认引擎在走完内建步骤（身份域/客户端/Profile/Permission/Scope）后，还会逐个评估宿主注入的 `AuthorizationPolicy`，作为**收尾的扩展判断**。这是把“某段时间禁止访问某类资源”“只允许特定来源”等额外规则接进来的点。

接口很简单：

```java
@FunctionalInterface
public interface AuthorizationPolicy {
    AuthorizationPolicyResult evaluate(IamPrincipal principal, AuthorizationRequest request);
}
```

返回值用静态工厂构造：

```java
AuthorizationPolicyResult.allow(code, reason);   // 放行
AuthorizationPolicyResult.deny(code, reason);    // 拒绝，code/reason 进入 decision steps
```

宿主把它作为 Bean 交给引擎（构造 `DefaultAuthorizationEngine` 时的 `policies` 列表）。任一策略返回 deny，该请求的 `decisionCode` 就变成这个策略的 code。

> 扩展策略属于引擎“第 6 步”；如果只是想表达“业务资源在不在某个项目”，那通常应该用 Scope / hierarchy 而不是额外策略。策略适合**横向、与具体资源树无关**的约束。

## 一个真实 HTTP 案例

Alice（`EXAMPLE`/`WEB`，Profile 401）调 `POST /api/documents/1001`，接口标了 `@RequirePermission(value="document:update", access=WRITE)`：

```text
拦截器：有登录主体 ✅
  Resolver 解析出 DOCUMENT/1001 ✅
  引擎 decide(...)：
    IDENTITY_DOMAIN ✅
    CLIENT_TYPE ✅
    ACTIVE_PROFILE 401 ✅
    ATOMIC_PERMISSION document:update ❌     ← 卡在这
  决策拒绝 → PERMISSION_DENIED
拦截器翻译 → 403 IAM_ACCESS_DENIED
body → application/problem+json，内附 decisionCode=PERMISSION_DENIED
```

如果换成 Alice 有 `document:update` 但访问的是 `PROJECT/202` 的文档，则引擎在 `RESOURCE_SCOPE` 失败，返回 `SCOPE_DENIED`——HTTP 仍是 403，但 `decisionCode` 不同，这就是区分“缺能力”和“超范围”的钥匙。

## 完成检查

```text
□ 我能说清：引擎给语义（decisionCode），拦截器给 HTTP 呈现（IAM_* 码）
□ 面对 403，我会先看 decisionCode 是 PERMISSION_DENIED 还是 SCOPE_DENIED
□ 我知道扩展策略是引擎内建步骤之后的 AuthorizationPolicy 评估
□ 我不会用 HTTP 码代替 decision steps 去判断“哪个权限/范围出问题”
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| 想给“某资源在某范围外”加策略 | 通常用 Scope / hierarchy，而不是扩展策略；策略适合横向约束。 |
| 403 只看到 `IAM_ACCESS_DENIED` 不知道卡哪 | 看 body 里的 `decisionCode` 或跑一次授权诊断。 |
| 以为 403/401 就代表“没权限” | 401 是没登录主体；403 才是已登录但被拒。二者诊断方向不同。 |
| 扩展策略没生效 | 确认它作为 Bean 传给了 `DefaultAuthorizationEngine` 的 policies，并检查返回的 deny code。 |

## 下一步

- [授权引擎](/authorization/authorization-engine/)——引擎第 1~6 步与 decision steps。
- [资源作用域](/authorization/resource-scope/)——Scope 层如何决定“能不能读某文档”。
- [require-permission](/authorization/require-permission/)——注解怎么驱动拦截器与引擎。
