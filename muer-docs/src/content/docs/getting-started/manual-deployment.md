---
title: 手动部署
description: 不依赖 Docker，手动准备 MySQL、Redis 和 Spring Boot 运行环境并完成 IAM Starter 配置。
sidebar:
  order: 4
---

IAM Spring Boot Starter 是**嵌入宿主应用**的组件，不是必须单独运行的 IAM 服务。因此部署的核心不是“启动一个 IAM 容器”，而是让宿主 Spring Boot 应用能够连接 MySQL、Redis，并加载 IAM Starter。

本页以手动配置为主。Docker、Docker Compose、云托管数据库都只是可选实现方式。

## 部署拓扑

最常见的结构是：

```text
Spring Boot Host Application
        │
        ├── MySQL 8.x
        │     └── IAM 持久化数据
        │
        └── Redis 7
              └── Opaque Token 索引
```

应用可以与 MySQL / Redis 部署在同一台主机，也可以连接内网数据库、云数据库或企业共享基础设施。

## 1. 准备 Java 与应用

运行环境需要 Java 21。

IAM 不限制宿主应用使用哪一种部署方式，你可以继续使用现有方式，例如：

- IDE 直接运行；
- `java -jar`；
- systemd / Windows Service；
- Kubernetes；
- Docker；
- 云平台应用服务。

关键要求只有一个：宿主 Spring Boot 应用能读取正确的 MySQL、Redis 与 `iam.*` 配置。

## 2. 手动准备 MySQL

项目发布基线使用 MySQL 8.4。已有兼容的 MySQL 8.x 服务时无需额外安装一套数据库。

### 创建数据库和账号

下面示例适用于应用与 MySQL 在同机的情况：

```sql
CREATE DATABASE iam_host
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'iam_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON iam_host.* TO 'iam_app'@'localhost';
```

如果应用在另一台机器：

- 把 `localhost` 换成实际应用主机或受控网段；
- 配置 MySQL 监听地址和防火墙；
- 优先使用私网；
- 不要把 3306 直接暴露到公网；
- 使用独立强密码或企业密钥管理方案。

### IAM 是否需要手工建表？

默认不需要。

```yaml
muer:
  schema:
    enabled: true
```

Starter 会使用宿主 `DataSource` 执行 `classpath:db/iam/migration` 中的 Flyway migration，并写入独立历史表：

```text
iam_flyway_schema_history
```

如果你的组织要求所有数据库变更必须由 DBA 或发布平台执行，可以先由部署系统应用同一套 migration，然后配置：

```yaml
muer:
  schema:
    enabled: false
```

不要在没有同步 schema 的情况下直接关闭迁移。

## 3. 手动准备 Redis

项目发布基线使用 Redis 7。

Redis 不需要提前创建表、Key 或 Namespace。IAM 会按 Token 产生索引数据。

单机 Redis 的基础安全配置通常至少包括：

```text
bind 127.0.0.1
protected-mode yes
port 6379
```

如果 Spring Boot 应用与 Redis 不在同机：

- 监听应用所在的私网地址；
- 使用安全组 / 防火墙限制来源；
- 按组织要求开启 Redis 认证；
- 不要直接暴露 6379 到公网。

多个应用共享同一个 Redis 时，给每个应用设置独立前缀，例如：

```yaml
muer:
  token:
    redis-prefix: order-service:iam
```

## 4. 配置 Spring Boot 数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://10.0.0.10:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
```

如果 MySQL 就在本机，可以使用 `127.0.0.1`。

连接账号至少需要对当前 IAM 所在数据库进行 schema migration 和运行时读写的权限。生产环境建议按组织数据库权限规范进一步收敛。

## 5. 配置 Redis 连接

无密码示例：

```yaml
spring:
  data:
    redis:
      host: 10.0.0.11
      port: 6379
```

启用认证时：

```yaml
spring:
  data:
    redis:
      host: 10.0.0.11
      port: 6379
      password: ${IAM_REDIS_PASSWORD}
```

这些是 Spring Boot 的 Redis 连接配置；IAM 自己只需要配置 Token TTL 和 Key 前缀。

## 6. 配置 IAM

推荐从下面这组配置开始：

```yaml
muer:
  enabled: true
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  client-types:
    - WEB
```

完整属性见[配置参考](/reference/configuration/)。

## 7. 完整 application.yml 示例

```yaml
spring:
  datasource:
    url: jdbc:mysql://10.0.0.10:3306/iam_host
    username: iam_app
    password: ${IAM_DB_PASSWORD}
  data:
    redis:
      host: 10.0.0.11
      port: 6379
      password: ${IAM_REDIS_PASSWORD:}

muer:
  enabled: true
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
  schema:
    enabled: true
    history-table: iam_flyway_schema_history
  client-types:
    - WEB
```

## 8. 启动时检查什么

普通使用者不需要执行 IAM 项目的完整测试，只需要确认部署本身正常：

1. Spring Boot 应用启动成功；
2. MySQL 没有连接错误；
3. Redis 没有连接错误；
4. `muer.schema.enabled=true` 时 Flyway migration 成功；
5. 合法宿主账号能够调用 `POST /iam/auth/login` 得到 `200`；
6. 使用返回 Token 调用 `GET /iam/auth/me` 得到 `200`。

如果业务已经接入 `@RequirePermission`，再选择一个典型接口确认允许 / 拒绝结果符合你的业务规则即可。

完整 Testcontainers、独立 Consumer 和安全回归测试由 IAM 仓库 CI 负责，不是部署者的必做步骤。

## 9. Docker 作为可选方案

如果开发机没有现成 MySQL 和 Redis，可使用仓库中的：

```text
examples/quickstart/docker-compose.yml
```

它只用于快速获得本地基础设施。使用 Docker 与手动安装不会改变 IAM 的 Spring Boot 配置模型。

## 10. 生产建议

- 数据库与 Redis 使用私网；
- 凭据通过环境变量、Secret Manager 或配置中心提供；
- 不把演示账号、演示密码和 `QuickStartDemoSeeder` 带入生产；
- 为不同应用设置不同 `muer.token.redis-prefix`；
- 保留 IAM Flyway 历史表；
- 变更 IAM schema 前先备份数据库；
- 反向代理环境继续阅读[反向代理](/operations/reverse-proxy/)；
- 上线前检查[生产检查清单](/operations/production-checklist/)。
