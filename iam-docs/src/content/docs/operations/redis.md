---
title: Redis 存储
description: 手动准备 Redis 7，并配置不透明 Token 索引、连接认证与应用隔离前缀。
sidebar:
  order: 2
---

## Redis 在 IAM 中做什么

Redis 用于把不透明 `accessToken` 快速映射到 `TokenRecord`。它是高性能索引层，不是授权数据的最终权威。

```text
Bearer Token
   ↓
Redis Token Index
   ↓
TokenRecord / IamPrincipal
   ↓
Authorization Engine
```

MySQL 仍保存持久 Session、Profile、Permission Template 与 Scope。

## 版本建议

项目发布验证基线使用 Redis 7。

已有 Redis 7 服务可以直接使用，不需要为 IAM 预建数据库结构、Key 或 Namespace。

## 手动部署时的基础配置

Redis 与应用同机时，一个常见的安全起点是：

```text
bind 127.0.0.1
protected-mode yes
port 6379
```

如果应用和 Redis 不在同一台主机：

- 监听受控私网地址；
- 通过安全组或防火墙只允许应用来源访问；
- 按组织安全规范配置认证；
- 不要直接把 6379 暴露到公网。

具体安装方式由操作系统和企业环境决定，IAM 不要求使用 Docker。

## Spring Boot 连接配置

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

这些是 Spring Boot 自身的 Redis 连接配置。

## IAM Token 配置

```yaml
iam:
  token:
    ttl: 8h
    redis-prefix: my-app:iam
  session:
    enabled: true
    touch-interval: 10m
```

其中：

- `iam.token.ttl`：Token 有效期，默认 `8h`；
- `iam.token.redis-prefix`：IAM Key 前缀，默认 `iam`。

## 多应用共享 Redis

如果多个应用共用一个 Redis，建议为每个应用使用不同前缀：

```yaml
# 订单系统
iam:
  token:
    redis-prefix: order-service:iam
```

```yaml
# 人力资源系统
iam:
  token:
    redis-prefix: hr-service:iam
```

这样可以降低 Token Key 和反向索引互相干扰的风险。

## Redis 数据丢失意味着什么

Redis 是 Token 索引层。Redis 数据被清空后，已有不透明 Token 可能无法继续解析，但这不应该改变 MySQL 中的持久授权事实。

因此：

- 不要把 Redis 当成唯一授权数据源；
- Session、Profile、Permission、Scope 的持久状态以 MySQL 为准；
- Redis 故障恢复策略应和应用的 Token / Session 运维策略一起设计。

## 部署成功的最小判断

普通使用者不需要跑 Redis 集成测试。只需要确认：

- Spring Boot 启动时 Redis 连接没有报错；
- 合法登录可以获得 Token；
- 携带 Token 调用受保护接口能够正常解析 Principal。

框架仓库的 Redis/Testcontainers 回归由 CI 负责。

## 源码参考

- `TokenRecord` / `AuthSession`：<https://github.com/wbh123/iam/tree/main/muer-session/src/main/java/io/github/muer/session>
- `IamBearerTokenFilter`：<https://github.com/wbh123/iam/tree/main/muer-spring-boot-autoconfigure/src/main/java/io/github/muer/autoconfigure>

下一步：阅读[MySQL 存储](/operations/mysql/)和[手动部署](/getting-started/manual-deployment/)。
