---
title: MySQL 存储
description: MySQL 是 IAM 的持久化权威，保存会话、Profile 与权限模板等核心数据。
sidebar:
  order: 1
---

## 解决的问题

IAM 需要可靠、可审计、可回放的权威数据源。MySQL 承担**持久化权威（source of truth）**角色：会话、Profile、权限模板版本、作用域等全部落库，保证重启、Redis 失效后授权判定依然正确。

## 关键概念

- MySQL 持久化：sessions（`AuthSession`）、profiles（`AuthorizationProfile`）、permission templates（`PermissionTemplateVersion`）、scopes（`ResourceScope`）等。
- 表结构由 Flyway 管理：配置 `iam.schema.enabled`（默认 `true`）、`iam.schema.history-table`（默认 `iam_flyway_schema_history`）。
- 类比：**MySQL 是正式档案室**，Redis 只是高速索引卡（见 [Redis 架构](/operations/redis/)）。
- 关键点：Redis 绝不能成为授权的「真相来源」——任何决策都必须可回溯到 MySQL 中的持久数据。

## 真实示例

配置数据源与 schema 初始化（application.yml）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iam
    username: iam
    password: ${IAM_DB_PASSWORD}
iam:
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
```

## 源码

- 配置 `IamProperties`：https://github.com/wbh123/iam/blob/main/iam-spring-boot-autoconfigure/src/main/java/io/github/iamstarter/autoconfigure/
- Session 模型：https://github.com/wbh123/iam/blob/main/iam-session/src/main/java/io/github/iamstarter/session/

当前版本：`0.1.0-SNAPSHOT`（Release Candidate），尚未发布。
