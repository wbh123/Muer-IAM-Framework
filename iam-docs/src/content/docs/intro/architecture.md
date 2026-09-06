---
title: 架构总览
description: 了解 IAM 的模块划分、分层职责与宿主接入方式。
sidebar:
  order: 4
---

## 它解决什么问题

IAM 不是一个独立身份服务器，而是一组**可嵌入**的 Spring Boot 模块。理解分层，才能知道哪一层该由宿主实现、哪一层由 starter 提供。

## 分层与模块

| 层 | 模块 | 职责 |
| --- | --- | --- |
| 核心模型 | `muer-core` | 定义 `IamPrincipal`、`ResourceDescriptor`、`ResourceScope` 等模型与端口（如 `ResourceHierarchyProvider`） |
| 认证 | `muer-authentication` | `IdentityAuthenticator`、`LoginRequest`、`AuthenticationResult`、Profile 切换 |
| 授权 | `muer-authorization` | `AuthorizationEngine`、`AuthorizationRequest`、`AuthorizationDecision`、`AuthorizationProfile`、`PermissionTemplateVersion` |
| 会话 | `muer-session` | `AuthSession`、`TokenRecord` 及其生命周期 |
| 持久化 | `muer-persistence-mybatis` | Schema 迁移与数据访问 |
| 诊断 | `muer-diagnostics` | 复用同一引擎的授权诊断 |
| 管理 Web | `muer-management-web` | 登录、Session、Profile、诊断等 HTTP 端点（见 [OpenAPI](https://github.com/wbh123/iam/blob/main/muer-management-web/src/main/resources/openapi/iam.yaml)） |
| 自动装配 | `muer-spring-boot-autoconfigure` | Bearer Filter、`RequirePermission` 拦截器、`MuerProperties` |
| 聚合 | `muer-spring-boot-starter` | 聚合上述依赖，宿主只需引入它 |
| 示例 | `muer-example` | 唯一消费应用，演示完整链路 |

## 宿主接入的三个 SPI

1. `IdentityAuthenticator`：校验凭据并投影 `IamPrincipal`（源码 [IdentityAuthenticator.java](https://github.com/wbh123/iam/blob/main/muer-authentication/src/main/java/io/github/muer/authentication/IdentityAuthenticator.java)）；
2. `ResourceHierarchyProvider`：判断资源是否落在 scope 内（源码 [ResourceHierarchyProvider.java](https://github.com/wbh123/iam/blob/main/muer-core/src/main/java/io/github/muer/core/port/ResourceHierarchyProvider.java)）；
3. `MvcResourceDescriptorResolver`：把请求解析为 `ResourceDescriptor`。

## 下一步

按[快速开始](/getting-started/quick-start/)跑通链路，或先阅读[安装](/getting-started/installation/)。
