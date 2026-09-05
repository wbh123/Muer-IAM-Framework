---
title: 安装
description: 把 IAM Spring Boot Starter 引入宿主应用，并准备好运行所需的基础设施与 SPI 实现。
sidebar:
  order: 2
---

## 它解决什么问题

IAM 以「一个 starter 依赖 + 宿主提供少量 SPI」的方式接入，无需独立部署服务。本页说明最小依赖与前置条件。

## 依赖坐标

当前版本为 `0.1.0-SNAPSHOT`（Release Candidate），**尚未发布到 Maven Central**，只能从本地或内部仓库获取。生产应用只需引入聚合 starter：

```xml
<dependency>
    <groupId>io.github.iamstarter</groupId>
    <artifactId>iam-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 运行前置条件

- Java 21；
- 一个关系型数据库（演示与默认使用 MySQL 8.4，schema 由 starter 自动迁移）；
- Redis 7（存储不透明 Token 与 Session 索引）；
- Spring Boot 4。

## 宿主必须提供的三个 SPI

starter 不会替你持有用户与业务资源，需要宿主实现并注册为 Spring Bean：

1. `IdentityAuthenticator`：校验凭据并投影 `IamPrincipal`，源码见 [IdentityAuthenticator.java](https://github.com/wbh123/iam/blob/main/iam-authentication/src/main/java/io/github/iamstarter/authentication/IdentityAuthenticator.java)；
2. `ResourceHierarchyProvider`：判断资源是否落在 scope 内，源码见 [ResourceHierarchyProvider.java](https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/port/ResourceHierarchyProvider.java)；
3. `MvcResourceDescriptorResolver`：把 MVC 请求解析为 `ResourceDescriptor`（仅在用 `@RequirePermission` 时需要）。

示例实现可参考 `iam-example` 模块：[ExampleIdentityAdapter.java](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleIdentityAdapter.java)。

## 下一步

配置连接与可调项见[配置](/getting-started/configuration/)；想直接跑通请用[快速开始](/getting-started/quick-start/)。
