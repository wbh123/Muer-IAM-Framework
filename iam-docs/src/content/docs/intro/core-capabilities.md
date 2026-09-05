---
title: 核心能力
description: IAM Spring Boot Starter 提供的能力总览。
sidebar:
  order: 2
---

IAM 的能力分为几大块。理解这些能力对应到源码里的哪些类型，能帮你快速定位要用的 API。

## 认证（Authentication）

宿主通过实现 `IdentityAuthenticator` 提供登录凭据校验；IAM 负责：

- 校验登录请求携带的 `clientType` 是否在 `iam.client-types` 白名单内（在 IAM 层先拦截，不复制到宿主 adapter）；
- 调用宿主 adapter 拿到 `IamPrincipal`；
- 签发**不透明 Token** 与持久化 Session。

相关阅读：[认证](/authentication/login/)、[IdentityAuthenticator](/authentication/identity-authenticator/)。

## 细粒度授权（Authorization）

`AuthorizationEngine` 是授权的唯一权威：

```java
AuthorizationDecision decide(IamPrincipal principal, AuthorizationRequest request);
```

它逐条校验：身份域、客户端类型、活动 Profile、原子 Permission、Resource Scope，再交给可扩展的 `AuthorizationPolicy`，最后返回带逐步轨迹的 `AuthorizationDecision`。

相关阅读：[授权模型](/authorization/model/)、[AuthorizationEngine](/authorization/authorization-engine/)。

## 声明式权限（Declarative Permission）

在 MVC handler 上直接声明所需权限：

```java
@GetMapping("/api/documents/{id}")
@RequirePermission("document:read")
public Document get(@PathVariable String id) { ... }
```

`@RequirePermission` **只负责声明**，最终仍调用 `AuthorizationEngine` 决策，不会引入第二套授权逻辑。支持方法级优先于类级。

相关阅读：[@RequirePermission](/authorization/require-permission/)。

## 资源范围（Resource Scope）

IAM 不查询宿主的业务表，也不理解「文档属于哪个项目」。宿主用 `MvcResourceDescriptorResolver` 把路径变量解析成 `ResourceDescriptor`（含祖先 `parentPath`），再通过 `ResourceHierarchyProvider.isWithinScope(...)` 判断资源是否落在授权 scope 内。

相关阅读：[Resource 与 Scope](/concepts/resource-scope/)、[MvcResourceDescriptorResolver](/authorization/mvc-resource-descriptor-resolver/)。

## Profile 与会话（Profile & Session）

- **Profile**：一个身份可有多个 Profile，每个绑定一个权限模板版本与一组 scope，可切换；
- **Session**：MySQL 持久化权威 Session，Redis 只做 token 索引；
- **撤销**：支持撤销单个 Session，且不影响其它会话。

相关阅读：[Profile](/concepts/profile/)、[Profile 与 Session](/profile-session/profile/)。

## 诊断与审计（Diagnostics & Audit）

- **授权诊断**：复用同一引擎，返回每一步命中/拒绝的 `AuthorizationDecisionStep` 轨迹；
- **审计**：登录事件与会话变更可落库，供排查与合规使用。

相关阅读：[Authorization Diagnostics](/diagnostics/authorization-diagnostics/)。

## 源码对照

以上类型在仓库模块中的位置：

| 能力 | 关键源码 |
| --- | --- |
| 认证 | `iam-authentication/.../IdentityAuthenticator.java` |
| 授权引擎 | `iam-authorization/.../AuthorizationEngine.java` |
| 声明式权限 | `iam-spring-boot-autoconfigure/.../web/RequirePermission.java` |
| MVC 资源解析 | `iam-spring-boot-autoconfigure/.../web/MvcResourceDescriptorResolver.java` |
| 核心模型 | `iam-core/.../core/model/IamPrincipal.java` |
