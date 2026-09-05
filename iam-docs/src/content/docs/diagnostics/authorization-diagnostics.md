---
title: 授权诊断
description: 利用诊断端点与决策步骤（AuthorizationDecisionStep）排查授权被拒的根因。
sidebar:
  order: 1
---

## 解决的问题

授权被拒时，调用方只知道 `403 IAM_ACCESS_DENIED`，却不清楚是缺权限、作用域不符还是身份域不匹配。诊断功能把引擎内部的逐步判定（steps）暴露出来，便于定位。

## 关键概念

- `AuthorizationDecision` record：`(allowed, decisionCode, steps)`；`steps` 为 `List<AuthorizationDecisionStep>`。
- `AuthorizationDecisionStep` record：`(code, passed, reason)`——记录每一步是否通过及原因。
- 成功决策码：`ALLOWED`。
- 拒绝决策码（真实，来自 `DefaultAuthorizationEngine`）：`PERMISSION_DENIED`、`SCOPE_DENIED`、`IDENTITY_DOMAIN_MISMATCH`、`CLIENT_TYPE_MISMATCH`，以及 Profile 系列（如 `PROFILE_DISABLED`、`PROFILE_REVOKED`）。
- 诊断端点：`POST /iam/authorization/diagnostics` → `200`（属性 `iam.diagnostics.enabled` 已暴露）。

## 真实示例

```bash
curl -X POST https://iam.example.com/iam/authorization/diagnostics \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "permissionCode": "document:update", "resourceId": "1001" }'
```

返回含 `steps` 数组，可看到 `SCOPE_DENIED` 之类的具体拒绝码与 `reason`。

## 源码

- `AuthorizationDecision` / `AuthorizationDecisionStep`：https://github.com/wbh123/iam/blob/main/iam-authorization/src/main/java/io/github/iamstarter/authorization/
- `DefaultAuthorizationEngine`：同上目录

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
