---
title: 木耳 Muer
description: Muer 为现代业务系统提供统一身份、认证、授权、会话、审计与诊断能力。
hero:
  tagline: 让身份能力，在每个系统中自然生长。Muer 为现代业务系统提供统一身份、认证、授权、会话、审计与诊断能力，同时保持清晰、可组合的接入边界。
  actions:
    - text: 15 分钟快速开始
      link: /getting-started/quick-start/
      icon: right-arrow
      variant: primary
    - text: 用户如何获得权限
      link: /concepts/how-permissions-work/
    - text: GitHub
      link: https://github.com/wbh123/Muer-IAM-Framework
      icon: external
template: splash
---

Muer 是嵌入 Spring Boot 应用的轻量 IAM（Identity and Access Management，身份与访问管理）框架。它回答三件事：**当前用户是谁、能执行哪些权限、能访问哪些资源范围**。

## 第一次接触 Muer？

新人建议按下面这条路径走，每步都给一个明确的阅读目标：

| 步骤 | 内容 | 预计时间 | 说明 |
| --- | --- | --- | --- |
| ① | [Muer 是什么](/intro/what-is-iam/) | 3 分钟 | 定位与边界：它帮你解决什么问题、不做什么 |
| ② | [10~15 分钟快速开始](/getting-started/quick-start/) | 15 分钟 | 用真实 HTTP 请求跑通「登录 → 授权 → 资源范围」全链路 |
| ③ | [用户如何获得权限](/concepts/how-permissions-work/) | 10 分钟 | 用一个固定案例理解 Permission / Template / Version / Profile / Scope |
| ④ | [从零接入自己的 Spring Boot](/getting-started/from-zero-tutorial/) | 30 分钟 | 把 Muer 接进你自己的项目 |
| ⑤ | [准备上线](/operations/production-checklist/) | — | 部署与上线前检查 |

想立刻知道「这工具是不是我要的」，先读下面的「Muer 是 / 不是什么」。

## Muer 是 / 不是什么

| Muer 是 | Muer 不是 |
| --- | --- |
| 可嵌入 Spring Boot 应用的 **IAM Starter** | 独立部署的身份服务器（没有独立 IAM 服务要部署） |
| 统一提供身份、权限、资源范围、会话与审计 | 不接管宿主的用户表 |
| 可接入你**已有的**用户系统 | 不要求你迁移密码 |
| 提供 Permission + Scope 的**细粒度授权** | 当前不是 OAuth / OIDC Provider（见 [路线图](#roadmap)） |
| 可与现有 Spring Security 配合 | 不要求重写你所有的业务认证体系 |

## Muer 怎么知道要接什么

接入 Muer，你不需要先学会任何框架内部类名。宿主只需要回答四件事：

1. **用户如何完成认证** —— 登录时校验谁的账号密码；
2. **系统定义了哪些业务权限** —— 例如 `document:read` 表示「能读文档」；
3. **业务资源之间是什么关系** —— 例如「文档挂在哪个项目下」；
4. **当前请求正在访问哪个业务资源** —— 例如「这次 GET 在访问文档 1001」。

这四件事分别对应四个扩展点（SPI），等你理解了上面的职责，再认识它们的名字：

| 你要回答的问题 | 对应扩展接口 |
| --- | --- |
| 用户如何完成认证 | `IdentityAuthenticator` |
| 系统有哪些业务权限 | `PermissionDefinitionProvider` |
| 业务资源间的关系 | `ResourceHierarchyProvider` |
| 当前请求访问哪个资源 | `MvcResourceDescriptorResolver` |

> 先想清楚「我要告诉 Muer 什么事」，再记这些接口名——正文里的正式名称始终保留，但你不必从类名开始。

## 什么时候适合用 Muer

- 你已有 **Spring Boot** 应用，并已有自己的用户体系；
- 希望把权限、资源范围、会话、审计收拢成**一套可解释、可审计**的决策来源；
- 需要 **Permission + Scope** 的细粒度授权，而不仅是「管理员 / 普通用户」两个角色；
- 希望每次 403 都能得到**为什么被拒绝**的答案，而不是靠猜。

## 什么时候可能不适合

- 你只需要 `ADMIN` / `USER` 两个简单角色——Muer 可能比实际需求更完整；
- 你需要成熟 **OAuth 2.0 / OIDC Identity Provider**——Muer 0.1.0 当前不提供（见 [路线图](#roadmap)）；
- 组织已有统一 IAM / IdP，只需让应用作为 **OAuth Resource Server**——通常没必要在应用内再建一套 Muer 授权体系。

## 最小示例

宿主业务路由只要声明权限，Muer 负责检查：

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) {
    return documents.get(id);
}
```

`@RequirePermission` 只声明「这个接口需要什么能力」。真正决定放行与否的是 Muer 的授权引擎：它校验身份域、客户端类型、活动授权身份、原子权限与资源范围，缺一不可。用一个案例看它们如何串起来，请读[用户如何获得权限](/concepts/how-permissions-work/)。

## 从这里开始

- 🚀 **第一次使用**：读[15 分钟快速开始](/getting-started/quick-start/)，用真实 HTTP 请求走通完整链路。
- 📦 **完整可运行示例**：想要一份对照源码？直接看 GitHub 的 [`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart)——它与快速开始教程一一对应，`mvn test` 即可验证。
- 🔌 **接入已有系统**：已经有自己的用户表 / Role / 部门数据？看[已有系统如何接入](/migration/overview/)，把用户源与资源接进来。
- 🛡️ **管理权限**：想决定「谁拥有什么」？看[权限管理](/getting-started/permission-management/)与[管理控制台](/management/console/)。
- 🚢 **准备上线**：要部署并做健康检查？看[手动部署](/getting-started/manual-deployment/)、[可观测性](/operations/observability/)与[上线前检查清单](/operations/production-checklist/)。

## Roadmap

Muer Admin Console 属于 0.1.0 首发能力。后续计划包括：OAuth 2.0、OpenID Connect、SSO、SAML、LDAP、多租户、ABAC DSL。
