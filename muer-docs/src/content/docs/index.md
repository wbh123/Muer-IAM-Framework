---
title: 木耳 Muer
description: Muer 为现代业务系统提供统一身份、认证、授权、会话、审计与诊断能力。
hero:
  tagline: 让身份能力，在每个系统中自然生长。Muer 为现代业务系统提供统一身份、认证、授权、会话、审计与诊断能力，同时保持清晰、可组合的接入边界。
  actions:
    - text: 快速开始
      link: /getting-started/quick-start/
      icon: right-arrow
      variant: primary
    - text: 查看文档
      link: /concepts/identity-principal/
    - text: GitHub
      link: https://github.com/wbh123/Muer-IAM-Framework
      icon: external
template: splash
---

Muer Identity 是面向现代业务系统的轻量 IAM 框架，让统一身份、认证、授权、会话、审计与诊断能力自然融入已有应用。

- Muer **不拥有**宿主的账号、密码或业务数据，它只回答三件事：谁是当前用户、能执行哪些权限、能访问哪些资源范围。
- 宿主通过少量 SPI（`IdentityAuthenticator`、`ResourceHierarchyProvider`、`MvcResourceDescriptorResolver`）把自己的用户源与业务资源映射进来。
- 版本为 `0.1.0-SNAPSHOT`（Release Candidate），**尚未发布到 Maven Central**。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| **认证** | 宿主实现 `IdentityAuthenticator` 校验凭据；IAM 校验 `clientType` 并签发 opaque token 与持久化 Session。 |
| **细粒度授权** | `AuthorizationEngine` 逐条校验身份域、客户端类型、活动 Profile、原子权限与资源范围，返回可解释的决策轨迹。 |
| **声明式权限** | 用 `@RequirePermission("document:read")` 声明 handler 所需权限；最终仍由同一个引擎决策，不存在第二套授权逻辑。 |
| **资源范围** | 宿主把业务资源映射为 `ResourceDescriptor`，由 `ResourceHierarchyProvider` 判断是否落在 scope 内；IAM 不查询业务表。 |
| **Profile** | 一个身份可有多个 Profile，每个绑定一个权限模板版本与一组 scope，区别于扁平的 Role。 |
| **Session** | MySQL 持久化权威 Session；Redis 仅作为 opaque token 索引。支持按需撤销单个 Session。 |
| **授权诊断** | 诊断接口复用同一引擎，返回每一步命中/拒绝的轨迹，便于排查 403。 |
| **审计** | 登录事件、会话与授权主体变更可审计落库。 |

Identity · Authentication · Authorization · Session · Audit · Diagnostics · Spring Boot

## 最小示例

宿主业务路由只要声明权限，IAM 负责检查：

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) {
    return documents.get(id);
}
```

真正决定允许与否的是 `AuthorizationEngine`：它校验身份域、客户端类型、活动 Profile、原子权限与资源范围。

## 从这里开始

- 想立刻上手？阅读[快速开始](/getting-started/quick-start/)，用真实 HTTP 请求走通完整链路。
- 想理解设计？从[核心概念](/concepts/identity-principal/)开始。
- 想接入已有系统？看[已有系统接入](/migration/overview/)。

## Roadmap（不在 0.1.0 范围内）

OAuth 2.0、OpenID Connect、SSO、SAML、LDAP、多租户、ABAC DSL、管理后台等能力不在本发布内，均记录在后续 Roadmap。
