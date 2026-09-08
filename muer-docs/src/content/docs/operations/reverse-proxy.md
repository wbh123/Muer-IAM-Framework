---
title: 反向代理部署
description: 在 Nginx 等反向代理后部署 IAM 时，正确传递 Bearer 令牌与客户端真实信息。
sidebar:
  order: 3
---

## 解决的问题

IAM 的认证依赖 `Authorization: Bearer <token>` 头，会话与审计依赖真实客户端 IP、User-Agent。经反向代理后若不加配置，这些值会被代理地址覆盖，导致审计失真或限流误判。

## 关键概念

- `LoginRequest` record 含 `ipAddress`、`userAgent`、`clientInstance` 等，这些值由登录请求头/代理头提取。
- 令牌透传：代理必须把 `Authorization` 头原样转发给 IAM 后端，不得剥离。
- 真实客户端信息：通过 `X-Forwarded-For`、`X-Forwarded-Proto`、`X-Real-IP` 传递，后端据此解析 `ipAddress`。
- 端点（来自 OpenAPI）：`POST /iam/auth/login`、`POST /iam/auth/logout`、`GET /iam/sessions` 等均走代理前缀 `/iam`。

## 真实示例

Nginx 透传配置：

```nginx
location /iam/ {
  proxy_pass http://iam-backend:8080;
  proxy_set_header Authorization $http_authorization;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 源码

- `LoginRequest`：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/
- OpenAPI：https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-management-web/src/main/resources/openapi/iam.yaml

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
