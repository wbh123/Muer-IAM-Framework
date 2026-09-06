---
title: 安装
description: 把 IAM Spring Boot Starter 引入宿主应用，并准备 Java、MySQL、Redis 与宿主 SPI。
sidebar:
  order: 2
---

## 它解决什么问题

IAM 以「一个 Starter 依赖 + 宿主提供少量 SPI」的方式嵌入现有 Spring Boot 应用，不需要额外部署一套独立 IAM 服务。

## 依赖坐标

当前版本为 `0.1.0-SNAPSHOT`（Release Candidate），**尚未发布到 Maven Central**，只能从本地或内部仓库获取。

```xml
<dependency>
    <groupId>io.github.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

业务应用通常只需要这个聚合 Starter。

## 运行前置条件

- Java 21；
- Spring Boot 4；
- MySQL 8.x（发布验证基线为 MySQL 8.4）；
- Redis 7。

MySQL 和 Redis 可以是：

- 本机安装；
- 内网已有服务；
- 云托管服务；
- Docker / Kubernetes 中的服务。

**Docker 不是前置条件。** IAM 只关心宿主应用能否通过标准 Spring Boot 配置连接这些基础设施。

## 推荐接入顺序

1. 在现有 Spring Boot 工程添加 `muer-spring-boot-starter`；
2. 准备一个可读写的 MySQL 数据库账号；
3. 准备一个可连接的 Redis 7 服务；
4. 配置 `spring.datasource`、`spring.data.redis` 与 `iam.*`；
5. 实现宿主身份和资源范围 SPI；
6. 启动应用；
7. 调用登录与当前用户接口确认基础链路。

完整的 MySQL / Redis 手工配置流程见[手动部署](/getting-started/manual-deployment/)。

## 宿主必须提供的 SPI

Starter 不会替你持有用户与业务资源。根据使用能力注册 Spring Bean：

1. `IdentityAuthenticator`：校验宿主凭据并投影 `IamPrincipal`；
2. `ResourceHierarchyProvider`：当使用资源 Scope 时，判断业务资源是否位于 Scope 内；
3. `MvcResourceDescriptorResolver`：仅当 MVC 路由使用 `@RequirePermission` 时，把请求映射成 `ResourceDescriptor`。

示例实现：

- [ExampleIdentityAdapter.java](https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/muer/example/ExampleIdentityAdapter.java)
- [ExampleResourceHierarchyAdapter.java](https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/muer/example/ExampleResourceHierarchyAdapter.java)
- [ExampleDocumentResourceResolver.java](https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/muer/example/ExampleDocumentResourceResolver.java)

## 不需要做什么

普通接入者不需要：

- 运行 IAM 仓库的完整 Testcontainers 套件；
- 使用 Docker 复刻项目 CI 环境；
- 直接依赖 IAM 的 MyBatis Mapper / Repository 实现；
- 为 Redis 预创建 Key；
- 默认手工创建 IAM 数据表。

如果 `iam.schema.enabled=true`，Starter 会自动执行 IAM Flyway migration。

## 下一步

- 想最快接入：阅读[快速开始](/getting-started/quick-start/)；
- 想不用 Docker 手动部署：阅读[手动部署](/getting-started/manual-deployment/)；
- 查看完整参数：阅读[基础配置](/getting-started/configuration/)与[配置参考](/reference/configuration/)。
