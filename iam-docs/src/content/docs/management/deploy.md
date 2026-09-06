---
title: 安装与部署
description: IAM Admin Console 的本地构建、后端要求与 Nginx 部署指引。
sidebar:
  order: 2
---

## 构建

```bash
cd iam-admin-web
npm ci
npm run api:generate   # 契约单一来源：muer-management-web/.../openapi/iam.yaml
npm run type-check
npm run test
npm run build          # 产出 dist/
```

产物 `dist/` 为纯静态文件，可交给 Nginx / Apache / CDN；不需要 Docker。

## 后端要求

宿主应用只需要启动 `muer-spring-boot-starter`，并满足：

- MySQL（IAM schema 由迁移脚本管理）与 Redis；
- 宿主实现 `IdentityAuthenticator` 与 `ResourceHierarchyProvider`；
- 为控制台管理员准备一个 Profile，其 Template Version 包含所需的 `iam.admin.*`，
  Scope 覆盖管理目标。

## 反向代理示例

Nginx：

```nginx
location / {
    try_files $uri $uri/ /index.html;   # SPA
}
location /iam/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host              $host;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

完整的安全清单（HTTPS、CSP、CORS、Token 存储、代理头）见仓库
`docs/IAM_ADMIN_CONSOLE_DEPLOYMENT.md`。

## 环境变量

`VITE_IAM_API_BASE_URL` 用于开发/跨源指向后端；生产默认同源，由上述反向代理转发
`/iam/**`。
