---
title: MySQL 存储
description: 手动准备 MySQL 并配置 IAM 的持久化权威、Flyway migration 与运行账号。
sidebar:
  order: 1
---

## MySQL 在 IAM 中做什么

MySQL 是 IAM 的**持久化权威（source of truth）**，保存 Session、Profile、Permission Template Version、Scope、审计等核心数据。

Redis 只负责快速 Token 索引，不能替代 MySQL 的持久化状态。

## 版本建议

项目发布验证基线使用 MySQL 8.4。部署时优先使用兼容的 MySQL 8.x，并在正式上线前按你的目标版本完成兼容性验证。

## 手动创建数据库

如果已有 MySQL，可以直接创建数据库和运行账号：

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

应用与数据库不在同一台机器时，把 `localhost` 替换成真实应用主机或受控网段。

生产建议：

- 使用独立强密码；
- 通过 Secret / 配置中心传递密码；
- 只允许应用所在网络访问 MySQL；
- 不要直接把 3306 暴露到公网；
- 按组织规范进一步收敛数据库权限。

## Spring Boot 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://10.0.0.10:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
```

同机部署时可使用 `127.0.0.1`。

## IAM 表如何创建

默认：

```yaml
iam:
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
```

Starter 会从：

```text
classpath:db/iam/migration
```

执行 Flyway migration，所以首次部署通常不需要手工逐表建表。

IAM 使用独立历史表 `iam_flyway_schema_history`，避免和宿主应用自己的 Flyway 历史表混淆。

## 由 DBA 管理 Schema

如果组织要求数据库变更统一由 DBA 或发布平台执行，可以：

1. 先应用当前版本对应的 IAM migration；
2. 确认数据库结构已经同步；
3. 再关闭 Starter 自动迁移：

```yaml
iam:
  schema:
    enabled: false
```

不要在 schema 尚未准备好的情况下关闭 migration。

## 与宿主业务表的关系

IAM 默认复用宿主 Spring Boot 的 `DataSource`。因此 IAM 表可以和宿主业务表位于同一数据库中。

如果你的系统希望物理隔离数据库，需要在宿主侧提供与 IAM 默认持久化适配兼容的数据源方案，而不是简单增加一个 IAM 配置项。

## 部署成功的最小判断

普通使用者无需跑数据库集成测试。启动应用时确认：

- DataSource 连接成功；
- Flyway 没有 migration 错误；
- 应用正常启动；
- 登录 / Session 等 IAM 接口可以正常工作。

完整 Testcontainers 验证由 IAM 仓库 CI 负责。

## 源码参考

- `MuerProperties`：<https://github.com/wbh123/iam/blob/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure/>
- IAM migration：<https://github.com/wbh123/iam/tree/main/muer-persistence-mybatis/src/main/resources/db/iam/migration>
- Session 模型：<https://github.com/wbh123/iam/tree/main/muer-session/src/main/java/io/github/muer/session>

下一步：阅读[Redis 存储](/operations/redis/)和[手动部署](/getting-started/manual-deployment/)。
