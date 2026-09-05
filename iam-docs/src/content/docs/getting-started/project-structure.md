---
title: 项目结构
description: 了解 IAM 的多模块 Maven 布局、宿主应用需要关心的边界，以及演示模块的角色。
sidebar:
  order: 4
---

## 它解决什么问题

IAM 是多模块 Maven 工程。理解哪些模块是「内部实现」、哪些是「宿主可直接依赖的边界」，可以避免把内部包误引入生产应用。

## 模块布局

仓库根下包含以下模块：

| 模块 | 角色 |
| --- | --- |
| `iam-core` | 核心模型与端口（`IamPrincipal`、`ResourceDescriptor`、`ResourceHierarchyProvider`） |
| `iam-authentication` | 认证与登录（`IdentityAuthenticator`、`LoginRequest`、`AuthenticationResult`） |
| `iam-authorization` | 授权引擎与模型（`AuthorizationEngine`、`AuthorizationProfile`、`PermissionTemplateVersion`） |
| `iam-session` | 会话与令牌（`AuthSession`、`TokenRecord`） |
| `iam-persistence-mybatis` | Schema 迁移与持久化 |
| `iam-diagnostics` | 授权诊断 |
| `iam-management-web` | 管理 HTTP 端点（[OpenAPI](https://github.com/wbh123/iam/blob/main/iam-management-web/src/main/resources/openapi/iam.yaml)） |
| `iam-spring-boot-autoconfigure` | 自动装配 Web 层（`IamProperties`、`IamBearerTokenFilter`、`RequirePermission`、`MvcResourceDescriptorResolver`、`IamAuthorizationInterceptor`） |
| `iam-spring-boot-starter` | 聚合依赖，宿主唯一需要引入的模块 |
| `iam-example` | 唯一消费应用，演示完整链路与 SPI 实现 |
| `iam-audit` / `iam-tests` | 审计与测试支撑 |

## 宿主边界

- **只依赖** `iam-spring-boot-starter`；不要直接依赖 `iam-persistence-mybatis` 或内部实现模块。
- 用脚本 `scripts/verify-consumer-public-api.sh` 验证消费应用未导入 IAM 内部包。

## 演示数据位于何处

`iam-example` 中的 [`QuickStartDemoSeeder`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/QuickStartDemoSeeder.java) 写入演示用户、profile 与文档，仅在 `dev` profile 且 `iam.example.seed-demo=true` 时运行，**切勿用于生产**。

## 下一步

按[快速开始](/getting-started/quick-start/)跑通；理解术语请从[Identity 与 Principal](/concepts/identity-principal/)开始。
