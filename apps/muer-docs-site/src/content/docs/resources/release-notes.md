---
title: 发布说明
description: Muer 0.1.0 正式版本的状态、能力范围与运行前提。
sidebar:
  order: 3
---

## 当前版本

**Muer 0.1.0** 是首个正式发布版本。主要 Maven 消费入口为：

```text
cloud.muer:muer-spring-boot-starter:0.1.0
```

0.1.0 首发范围为：**Starter + Management API + Muer Admin Console + Permission Registration + Runtime Observability + 文档站**。Admin Console 属于首发能力，但仍是可选客户端，Starter 本身不依赖前端。

## 包含能力

- 认证：`IdentityAuthenticator` 适配、登录签发 Opaque Token、Session 管理；
- 授权：`AuthorizationEngine`、Permission Template Version、Profile、Resource Scope、`@RequirePermission`；
- 诊断：当前 Principal 可通过 `POST /iam/authorization/diagnostics` 查看真实决策步骤；
- 管理 API：Users、Identity、Permissions、Templates、Profiles、Scopes、Sessions、Audit、Overview；
- Admin Console：Vue 3 + TypeScript 管理端，覆盖 Dashboard、用户、模板、Profile、Session、Audit、Diagnostics 与个人安全中心；
- 文档站：QuickStart、手动 MySQL/Redis 配置、Management Console、部署和安全说明；
- 运行时观测：可选 Spring Boot Actuator / Micrometer 集成；
- 迁移友好：影子模式、数据投影与回滚说明。

## Admin Console 边界

- `/iam/admin/**` 始终由后端 `AuthorizationEngine` 重新授权；
- 前端菜单和 Router Guard 不是安全边界；
- `admin-demo / demo-pass` 只有 `dev + muer.showcase.seed-admin=true` 时才创建；
- 生产环境没有默认管理员，也没有公开 bootstrap HTTP 接口；
- 第一个生产管理员通过受控 provisioning 创建。

## 运行前提

Starter 默认需要：

- Java 21；
- Spring Boot 4；
- MySQL 8.x；
- Redis 7。

Admin Console 开发/构建额外需要 Node.js 22+ 与 npm。

MySQL / Redis 不要求使用 Docker；可以使用本机、局域网、云服务或容器部署。

## 已知限制

- `audit/diagnostics/session` 的 `enabled` 属性已暴露，但当前自动配置未按其做条件化 bean；
- Admin Console 目前使用单元/组件测试、TypeScript 类型检查、OpenAPI 生成与 production build 做自动验证，浏览器级 Playwright E2E 可后续补充；
- 0.1.0 不提供 OAuth 2.0、OpenID Connect、SAML、LDAP 或单点登录协议。

## 继续阅读

- [快速开始](/getting-started/quick-start/)
- [Management Console](/management/console/)
- [手动部署](/getting-started/manual-deployment/)
- [生产检查清单](/operations/production-checklist/)
