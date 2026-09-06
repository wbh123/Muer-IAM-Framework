---
title: 项目结构
description: 了解 IAM 的多模块 Maven 布局、宿主应用需要关心的边界，以及演示模块的角色。
sidebar:
  order: 5
---

## 它解决什么问题

IAM 是一个多模块 Maven 工程，但普通宿主应用通常只需要依赖 `muer-spring-boot-starter`。了解模块边界有助于避免业务代码直接依赖内部实现。

## 宿主应用真正需要关心的模块

### `muer-spring-boot-starter`

业务应用的统一依赖入口。

### `muer-spring-boot-autoconfigure`

负责 Spring Boot 自动配置、Bearer Token 解析、声明式 MVC 授权等集成能力。宿主通常通过 Starter 间接获得它。

### 公共模型 / SPI

业务代码可能直接使用这些公共类型：

- `IamPrincipal`
- `IdentityAuthenticator`
- `AuthorizationEngine`
- `ResourceHierarchyProvider`
- `ResourceDescriptor`
- `@RequirePermission`
- `MvcResourceDescriptorResolver`

稳定候选边界见[Public API](/reference/public-api/)。

## 内部模块

仓库还包含认证、授权、Session、Audit、Diagnostics、MyBatis Persistence、Management Web 等模块，用来隔离不同职责。

普通 Consumer 不应为了“方便”直接导入：

- MyBatis Mapper；
- Repository implementation；
- persistence implementation；
- `internal` / `impl` 包。

## `muer-example` 的角色

`muer-example` 是仓库中的 Consumer Showcase，用于证明 Starter 可以被独立 Spring Boot 应用消费。

它同时提供 QuickStart 的 Alice / Document 示例，但**不是**要求用户照搬的生产项目模板。生产应用应该保留自己的用户模型、业务资源和部署方式，只接入 IAM 公共 API / SPI。

## 文档与测试的边界

- 用户文档负责说明如何配置、启动和调用 IAM；
- `muer-example` 展示真实接入方式；
- Testcontainers、独立 Consumer 验收与完整回归由项目 CI 负责。

因此普通使用者无需复制仓库测试环境，也不需要为了部署 IAM 运行所有测试。

## 下一步

- 手动准备运行环境：[手动部署](/getting-started/manual-deployment/)
- 查看 Starter 配置：[配置参考](/reference/configuration/)
- 查看公共 API：[Public API](/reference/public-api/)
