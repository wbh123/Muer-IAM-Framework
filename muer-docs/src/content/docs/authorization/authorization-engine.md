---
title: 授权引擎
description: AuthorizationEngine 如何按固定顺序产出可解释的 AuthorizationDecision——含真实决策顺序、decisionCode 与 steps 的用途。
sidebar:
  order: 5
---

如果你在排查“某个请求为何被拒”，先读本页；本页给你一份**真实决策顺序**，好让你在看 `decisionCode` 和 `steps` 时知道卡在哪一步。想看 Scope/Permission 的概念分别见[资源作用域](/authorization/resource-scope/)与[权限管理](/getting-started/permission-management/)。

## 解决什么问题

所有“是否放行”的判断都集中在 `AuthorizationEngine`，保证注解、拦截器、自定义调用走**同一套决策逻辑与可审计结果**。你不会在一个接口上看到“规则 A”，在另一个接口看到“规则 B”。

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
default void require(IamPrincipal principal, AuthorizationRequest request) // 拒绝时抛 AuthorizationDeniedException
```

- **AuthorizationRequest**：`(permissionCode, domain, clientType, resource, scopeAccess)`——描述“要做什么权限、在什么域/客户端/资源上、读写方式”。
- **AuthorizationDecision**（record）：`(allowed, decisionCode, steps)`——结论 + 一个代表最终结果的主代码 + 逐步明细。
- **AuthorizationDecisionStep**（record）：`(code, passed, reason)`——决策链上每一步的单项结果。

## 真实决策顺序（DefaultAuthorizationEngine）

以默认实现 `DefaultAuthorizationEngine` 为准，它是**顺序短路**的：每一步不过，立刻返回拒绝，不再往下走。顺序是：

```text
AuthorizationRequest
    ↓
1. IDENTITY_DOMAIN      身份域是否匹配（principal 与 request 的 domain 一致？）
    ↓
2. CLIENT_TYPE          客户端类型是否匹配
    ↓
3. ACTIVE_PROFILE       当前 Profile 是否有效（存在、属于该用户、启用、未撤销、
                        允许该 clientType、在有效期内、模板版本一致）
    ↓
4. ATOMIC_PERMISSION    该 Permission 码是否在用户权限集合里
    ↓
5. RESOURCE_SCOPE       Profile 的 Scope（且 accessMode 匹配）是否覆盖该资源
    ↓
6. POLICY:*             宿主注入的扩展 AuthorizationPolicy 逐条评估
    ↓
AuthorizationDecision
```

> 注意：**ACTIVE_PROFILE 校验在 Permission 之前**。也就是说，Profile 失效（被禁用、过期、模板版本被换）会先于“有没有这个权限”被拦下。这在排查 403 时很关键——先看是不是 Profile 层挂了，而不是权限层。

## 决策代码（真实）

| decisionCode | 含义 | 卡在哪一步 |
| --- | --- | --- |
| `ALLOWED` | 放行 | 走完全部步骤 |
| `IDENTITY_DOMAIN_MISMATCH` | 身份域不匹配 | 1 |
| `CLIENT_TYPE_MISMATCH` | 客户端类型不匹配 | 2 |
| `PROFILE_MISSING` | 无 activeProfile 或查不到 | 3 |
| `PROFILE_UNAVAILABLE` | 查询 Profile 抛异常 | 3 |
| `PROFILE_OWNER_MISMATCH` | Profile 不属于该 principal | 3 |
| `PROFILE_DISABLED` | Profile 被禁用 | 3 |
| `PROFILE_REVOKED` | Profile 被撤销 | 3 |
| `PROFILE_CLIENT_DENIED` | Profile 不允许该 clientType | 3 |
| `PROFILE_NOT_YET_VALID` / `PROFILE_EXPIRED` | Profile 未生效 / 已过期 | 3 |
| `PROFILE_TEMPLATE_MISMATCH` | Profile 模板版本与 principal 引用不一致 | 3 |
| `PERMISSION_DENIED` | 权限不足（Permission 层） | 4 |
| `SCOPE_DENIED` | 超出资源作用域 | 5 |
| 扩展策略返回的 code | 宿主策略拒绝 | 6 |

## 一个真实决策示例

Alice 带着 `activeProfileId=401` 的 Profile 登录（身份域 `EXAMPLE`、客户端 `WEB`），请求：`permission=document:update`、`resource=DOCUMENT/1001`、`access=WRITE`。若她的 Profile 只有 `document:read`，决策会是：

```text
IDENTITY_DOMAIN    EXAMPLE 匹配             PASS
CLIENT_TYPE        WEB 匹配                PASS
ACTIVE_PROFILE     401 有效                PASS
ATOMIC_PERMISSION  document:update 缺失    FAIL
RESOURCE_SCOPE     （未执行）               -
-------------------------------------------
PERMISSION_DENIED
```

若把请求改成她有的 `document:read`、且资源属于 `PROJECT/101`、Scope 恰好是 `PROJECT/101/READ`，则一路到 `RESOURCE_SCOPE` 通过 → `ALLOWED`。若资源换成 `PROJECT/202` 的文档，则 `RESOURCE_SCOPE` FAIL → `SCOPE_DENIED`。

> 上面的 steps 是否在某一失败点提前停止、以及 Profile 校验介入的具体时机，都以 `DefaultAuthorizationEngine` 的真实实现为准——上面顺序即按源码整理。

## steps 的价值：诊断与审计，不是前端判断

`AuthorizationDecision.steps` 是一组 `(code, passed, reason)`。它的用途**不是**给前端做权限判断，而是**解释“这次请求为什么被允许/拒绝”**，供诊断与审计：

```text
steps 是“为什么”的账本：
  → 排障时看哪一步 passed=false、reason 是什么
  → 审计时能重现当时的判定链路
```

在 Web 场景，最终 HTTP 状态由拦截器决定（401/403/404/500），`decisionCode` 与 `steps` 进一步说明 403 是缺 Permission 还是超 Scope。宿主自诊断可用 `POST /iam/authorization/diagnostics` 查看当前登录用户的决策步骤（见[授权诊断](/diagnostics/authorization-diagnostics/)）。

## 源码

<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authorization/src/main/java/cloud/muer/authorization/DefaultAuthorizationEngine.java>

相关：见 [authorization-policy](/authorization/authorization-policy/)、[resource-scope](/authorization/resource-scope/) 与 [require-permission](/authorization/require-permission/)。
