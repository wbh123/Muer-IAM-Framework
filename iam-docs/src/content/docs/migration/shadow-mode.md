---
title: 影子模式
description: 在影子模式下让 IAM 并行评估授权但不拦截请求，安全地与既有系统对照。
sidebar:
  order: 2
---

## 解决什么问题

完全替换权限系统风险高。影子模式让你在不影响线上行为的前提下，观察 IAM 会如何决策，验证其规则是否与旧系统一致。

## 关键概念

IAM 的授权核心是 `AuthorizationEngine.decide(principal, request)`，它返回 `AuthorizationDecision(allowed, decisionCode, steps)` 而**不强制**拦截。因此可以把 IAM 放在旁路：对每个请求调用 `decide()`，记录其 `decisionCode` 与 `steps`（含 `AuthorizationDecisionStep(code, passed, reason)`），与旧系统结果对比，但不抛 `AuthorizationDeniedException`、不 deny。

## 实践步骤

1. 接入 `IdentityAuthenticator`，让登录产出 `IamPrincipal`（见 [数据投影](/migration/data-projection/)）。
2. 在现有拦截器/过滤器中调用 `engine.decide(...)`，仅写日志或落库，不阻断。
3. 用 `POST /iam/authorization/diagnostics` 复核单条请求的预期决策。
4. 差异收敛后，再切到 `@RequirePermission` 或 `engine.require(...)` 进入强制模式。

## 源码参考

- `AuthorizationEngine`：<https://github.com/wbh123/iam/blob/main/iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationEngine.java>
- 诊断接口 OpenAPI：<https://github.com/wbh123/iam/blob/main/iam-management-web/src/main/resources/openapi/iam.yaml>
