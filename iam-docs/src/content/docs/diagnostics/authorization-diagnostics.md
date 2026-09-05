---
title: 授权诊断
description: 通过 AuthorizationDecision 与决策步骤定位权限、Scope、Profile 等授权拒绝原因。
sidebar:
  order: 1
---

## 解决的问题

业务接口返回 `403 IAM_ACCESS_DENIED` 时，客户端不应该看到内部授权细节，但开发者和管理工具仍需要知道究竟是 Permission、Scope、Profile 还是身份域导致拒绝。

授权诊断使用真实 `AuthorizationEngine` 的决策结果，不在文档或前端重新实现一套授权逻辑。

## 关键概念

- `AuthorizationDecision`：`(allowed, decisionCode, steps)`；
- `AuthorizationDecisionStep`：`(code, passed, reason)`；
- 成功决策码：`ALLOWED`；
- 常见拒绝码：`PERMISSION_DENIED`、`SCOPE_DENIED`、`IDENTITY_DOMAIN_MISMATCH`、`CLIENT_TYPE_MISMATCH` 以及 Profile 系列；
- 接口：`POST /iam/authorization/diagnostics`。

## 请求接口

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/iam/authorization/diagnostics` |
| Auth | Bearer Token |
| 成功 | HTTP `200`，返回 `AuthorizationDecision` |

请求体示例：

```json
{
  "permissionCode": "document:update",
  "domain": "EXAMPLE",
  "clientType": "WEB",
  "resourceType": "PROJECT",
  "resourceId": "101",
  "scopeAccess": "WRITE"
}
```

## 预期结果

如果 Reader Profile 缺少 `document:update`，响应仍为 HTTP `200`，但业务决策中：

```text
allowed = false
decisionCode = PERMISSION_DENIED
```

`steps` 会给出授权引擎实际经过的判断步骤，适合后台诊断和排障。

## 使用边界

面向普通业务客户端的 `403` 默认响应不会泄漏 Permission、Profile、Scope、Token 或完整决策步骤。详细诊断应只开放给受控的管理 / 排障场景。

## 源码

- `AuthorizationDecision` / `AuthorizationDecisionStep`：<https://github.com/wbh123/iam/tree/main/iam-authorization/src/main/java/io/github/iamstarter/authorization>
- `DefaultAuthorizationEngine`：同上目录
