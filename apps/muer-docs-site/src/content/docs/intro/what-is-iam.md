---
title: Muer 是什么
description: 了解 IAM Spring Boot Starter 的定位、职责边界与设计出发点。
sidebar:
  order: 1
---

Muer 是一个**可嵌入**的 Spring Boot 组件，为宿主应用提供认证、细粒度授权、资源范围与 Session 治理能力。它不是一个独立的身份服务器，也不是又一个「认证中间件」全家桶——它把「当前用户是谁、能执行哪些权限、能访问哪些资源」作为**权威的运行时决策来源**，并让宿主保留自己已有的账号体系与业务数据。

## 它解决什么问题

在大型系统中，授权逻辑很容易散落各处：

- 每个 Controller 里手写 `if (user.hasRole("admin"))`；
- 权限判断里混杂着用户、组织、资源是否可见的业务判断；
- Session 与 Token 的生命周期无人统一管理；
- 出了 403 只能靠猜，因为没有一个「为什么拒绝」的答案。

IAM 把这几件事收拢成一套一致的模型，并对外提供**可解释的授权决策**。

## 职责边界

| Muer 负责 | Muer 不负责 |
| --- | --- |
| 校验宿主提供的凭据并签发 Token / Session | 保存宿主的明文密码或口令 |
| 判断某 permission 是否授予当前 principal | 创建宿主自己的用户表（可复用 IAM 用户投影，也可完全宿主侧持有） |
| 判断资源是否落在授权 scope 内 | 理解宿主业务资源（如「文档属于哪个项目」） |
| 统一管理 Session 生命周期与撤销 | 接管宿主全部 Spring Security 配置 |

关键原则是：**宿主永远是它自己的用户源与业务数据的拥有者**，Muer 通过少量 SPI 把它们接进来，再给出统一、可解释的授权结论。

## 与常见方案的区别

- 不是 OAuth2/OpenID Connect 服务器：0.1.0 不实现任何外部身份协议，认证只面向宿主自己 `IdentityAuthenticator` 背后的用户源。
- 不引入 JWT：Token 是**不透明**的，只是一把 Redis 索引的钥匙，内容不向客户端暴露，也不承担「状态自包含」的职责。
- 不把授权做成「角色即权限」：用 Permission + Profile + Resource Scope + Authorization Version 的组合表达更细的授权。

## 下一步

- 快速跑通完整链路，请看[快速开始](/getting-started/quick-start/)。
- 理解核心术语，请从 [Identity 与 Principal](/concepts/identity-principal/) 开始。
