---
title: 安装与环境准备
description: 把 Muer 引入宿主应用，并准备 Java、MySQL、Redis 与宿主 SPI。
sidebar:
  order: 2
---

## 它解决什么问题

Muer 以「一个 Starter 依赖 + 宿主提供少量 SPI」的方式嵌入现有 Spring Boot 应用，不需要额外部署一套独立 IAM 服务。

:::note[本页职责边界]
本页只回答：**怎么拿到 Muer、支持什么 Java / Spring Boot、默认需要什么基础设施、Starter 坐标是什么**。它不教你怎么跑通、也不逐参数解释：

- 想「先跑成功」→ 读[10~15 分钟快速开始](/getting-started/quick-start/)；
- 想「怎么接入我自己的项目」→ 读[从零接入自己的项目](/getting-started/from-zero-tutorial/)；
- 想「查每个参数」→ 读[配置项参考](/reference/configuration/)。
:::

## 依赖坐标

Muer 0.1.0 的主要消费入口是下面这个聚合 Starter：

```xml
<dependency>
    <groupId>cloud.muer</groupId>
    <artifactId>muer-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

正式版本发布到 Maven Central 后，普通业务项目不需要 clone Muer 源码，也不需要额外声明 Muer 专用仓库；按照普通 Maven Central 依赖使用即可。

如果你正在修改 Muer 源码，或希望在正式仓库同步完成前验证当前 checkout，也可以在仓库根目录执行：

```bash
mvn clean install -DskipTests
```

这会把当前版本安装到本机 Maven 仓库，供示例或本地宿主项目优先解析。

### 企业内部仓库

如果组织统一使用 Nexus / Artifactory，也可以把 Muer 构件同步到内部仓库；业务项目仍使用相同的 `cloud.muer:muer-spring-boot-starter:0.1.0` 坐标。

## 运行前置条件

- Java 21；
- Spring Boot 4；
- MySQL 8.x（发布验证基线为 MySQL 8.4）；
- Redis 7。

上面的 MySQL / Redis 是 **Muer 0.1.0 默认 Starter 实现**运行所需的持久化 Repository 与默认 Token/Session 快速状态存储。若宿主替换了对应的公共 SPI 实现，其基础设施要求由宿主实现自行决定。

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
4. 配置 `spring.datasource`、`spring.data.redis` 与 `muer.*`；
5. 实现宿主身份和资源范围 SPI；
6. 启动应用；
7. 调用登录与当前用户接口确认基础链路。

完整的 MySQL / Redis 手工配置流程见[手动部署](/getting-started/manual-deployment/)。

## 宿主必须提供的 SPI

Starter 不会替你持有用户与业务资源。根据你使用的能力注册对应 Spring Bean：

| Bean | 必需？ | 作用 |
| --- | ---: | --- |
| `IdentityAuthenticator` | 登录时必需 | 校验宿主凭据并投影 `IamPrincipal` |
| `PermissionDefinitionProvider` | 推荐 | 用代码注册业务权限（`document:read` 等） |
| `ResourceHierarchyProvider` | 用 Scope 时需要 | 判断业务资源是否位于 Scope 内 |
| `MvcResourceDescriptorResolver` | MVC Scope 时需要 | 把 `@RequirePermission` 请求映射成 `ResourceDescriptor` |

示例实现：

- [ExampleIdentityAdapter.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/examples/showcase/src/main/java/cloud/muer/showcase/ExampleIdentityAdapter.java)
- [ExampleResourceHierarchyAdapter.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/examples/showcase/src/main/java/cloud/muer/showcase/ExampleResourceHierarchyAdapter.java)
- [ExampleDocumentResourceResolver.java](https://github.com/wbh123/Muer-IAM-Framework/blob/main/examples/showcase/src/main/java/cloud/muer/showcase/ExampleDocumentResourceResolver.java)

## 最小接入模式

**不要以为四个 Bean 一个都不能少。** 它们是分层可选的：

```text
只要登录
  IdentityAuthenticator

登录 + 业务权限
  + PermissionDefinitionProvider

资源级权限（Scope / @RequirePermission）
  + ResourceHierarchyProvider
  + MvcResourceDescriptorResolver

完整治理
  + Admin Console
  + Audit
  + Observability
```

想从最小链路起步，先只接 `IdentityAuthenticator`，跑通「添加 Starter → 登录 → `/iam/auth/me`」，再按需往上加。

## 不需要做什么

普通接入者不需要：

- 运行 IAM 仓库的完整 Testcontainers 套件；
- 使用 Docker 复刻项目 CI 环境；
- 直接依赖 IAM 的 MyBatis Mapper / Repository 实现；
- 为 Redis 预创建 Key；
- 默认手工创建 IAM 数据表。

如果 `muer.schema.enabled=true`，Starter 会自动执行 IAM Flyway migration。

## 下一步

- 想最快跑通一个真实接口：阅读[15 分钟快速开始](/getting-started/quick-start/)（可直接对照 [`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)）；
- 想不用 Docker 手动部署：阅读[手动部署](/getting-started/manual-deployment/)；
- 查看完整参数：阅读[基础配置](/getting-started/configuration/)与[配置参考](/reference/configuration/)。
