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
| 管理 Web | `modules/muer-http-api` | 登录、Session、Profile、诊断等 HTTP 端点（见 [OpenAPI](https://github.com/wbh123/Muer-IAM-Framework/blob/main/contracts/openapi/iam.yaml)） |
| 自动装配 | `muer-spring-boot-autoconfigure` | Bearer Filter、`RequirePermission` 拦截器、`MuerProperties` |
| 聚合 | `muer-spring-boot-starter` | 聚合上述依赖，宿主只需引入它 |
| 示例 | `muer-example` | 唯一消费应用，演示完整链路 |

## 宿主需要实现哪些 Bean

Muer 不是一个身份服务器，而是嵌入宿主应用的组件：持久化、Token、授权引擎、Session、Bearer 过滤等 Bean 都由 Starter 自动装配并给出安全默认。宿主真正需要关心的是下面 4 个“接缝”（SPI），它们决定宿主如何把**自己的**用户、资源与权限接进来。其余 Bean 若无必要不必覆盖。

| Bean | 提供方 | 不提供时的默认行为 | 需要它的场景 |
| --- | --- | --- | --- |
| `IdentityAuthenticator` | **宿主**（可选 Bean） | 默认实现恒返回空 → 登录总是失败 | 只要想认证就必须提供 |
| `PermissionDefinitionProvider` | 宿主（Bean 集合） | 无 Provider → 空注册，应用仍可启动 | 想声明并管理业务权限 |
| `ResourceHierarchyProvider` | 宿主（可选 Bean） | 默认恒返回 `false` → 任何 Scope 判断都拒绝 | 想启用 Resource Scope |
| `MvcResourceDescriptorResolver` | 宿主（可选 Bean） | 无 Resolver → `@RequirePermission` 接口返回 `500` | 想用声明式 MVC 授权 |
| 其余（引擎/Token/Session/迁移/过滤等） | Starter | — | 默认即可，无需覆盖 |

**为什么默认很“安全”**：四个 SPI 的缺省实现都是“什么都不放行”——登录失败、Scope 全拒、MVC 拒绝。这样即使宿主漏配，也不会意外放行，而是明确失败。因此：

- **最小接入（只做认证）**：实现 `IdentityAuthenticator`，配好 MySQL / Redis 即可登录并读取 Principal。
- **完整接入（权限 + 资源 + 声明式）**：在上者基础上再实现 `PermissionDefinitionProvider`（声明权限）、`ResourceHierarchyProvider`（真实层级判断，否则 Scope 恒拒）、`MvcResourceDescriptorResolver`（把请求映射为业务资源）。

### 三个接口与责任

1. `IdentityAuthenticator`：校验凭据并投影 `IamPrincipal`（源码 [IdentityAuthenticator.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-authentication/src/main/java/cloud/muer/authentication/IdentityAuthenticator.java)）；
2. `ResourceHierarchyProvider`：判断资源是否落在 scope 内（源码 [ResourceHierarchyProvider.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/muer-core/src/main/java/cloud/muer/core/port/ResourceHierarchyProvider.java)）；
3. `MvcResourceDescriptorResolver`：把请求解析为 `ResourceDescriptor`（源码 [mvc-resource-descriptor-resolver](/authorization/mvc-resource-descriptor-resolver/)）。

`PermissionDefinitionProvider` 用于在启动时声明“系统有哪些能力”，详见[定义权限](/getting-started/define-permissions/)。

## 下一步

按[快速开始](/getting-started/quick-start/)跑通链路，或先阅读[安装](/getting-started/installation/)。
