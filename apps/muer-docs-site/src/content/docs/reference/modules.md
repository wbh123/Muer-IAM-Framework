---
title: 模块构成
description: IAM 各 Maven 模块的职责划分与依赖关系。
sidebar:
  order: 5
---

## 模块列表

- **muer-core**：核心模型与端口。`IamPrincipal`、`ScopeAccess`、`ResourceDescriptor`、`ResourceScope`、`ResourceHierarchyProvider`（端口）等。被其它模块依赖。
- **muer-authentication**：认证。`IdentityAuthenticator`、`LoginRequest`、`AuthenticationResult`、profile 切换服务。
- **muer-authorization**：授权引擎。`AuthorizationEngine`、`AuthorizationRequest`、`AuthorizationDecision`、`AuthorizationProfile`、`PermissionTemplateVersion`。
- **muer-session**：会话与令牌。`AuthSession`、`TokenRecord`、`LoginResult`，基于 Redis 存储。
- **muer-spring-boot-autoconfigure**：Spring Boot 自动配置。`MuerProperties`、`IamBearerTokenFilter`、`RequirePermission`、`MvcResourceDescriptorResolver`、`IamAuthorizationInterceptor`。
- **modules/muer-http-api**：HTTP 管理层。暴露 `/iam/**` 端点，含 `openapi/iam.yaml`。
- **examples/showcase**：演示。`ExampleIdentityAdapter`、`QuickStartDemoSeeder`、`DocumentController`（alice/demo-pass 场景）。

## 依赖方向

`autoconfigure` 依赖 `core/authentication/authorization/session`；`management-web` 与 `example` 为可独立运行的装配层。宿主一般只引入 `muer-spring-boot-autoconfigure` 并实现 `IdentityAuthenticator` 与 `MvcResourceDescriptorResolver`。

## 源码参考

- 仓库根：<https://github.com/wbh123/Muer-IAM-Framework>
- autoconfigure：<https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-spring-boot-autoconfigure/src/main/java/cloud/muer/autoconfigure/>
