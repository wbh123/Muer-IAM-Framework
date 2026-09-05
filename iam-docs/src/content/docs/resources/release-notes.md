---
title: 发布说明
description: 当前版本 0.1.0-SNAPSHOT（Release Candidate）的状态与范围。
sidebar:
  order: 3
---

## 当前版本

**0.1.0-SNAPSHOT（Release Candidate）**，尚未发布到 Maven Central，也未作为正式 `0.1.0` 发布。

## 包含能力

- 认证：`IdentityAuthenticator` 适配、登录签发 Opaque Token、会话管理。
- 授权：`AuthorizationEngine`、模板版本、Profile、ResourceScope、MVC 拦截（method 优先于 class）。
- 诊断：`POST /iam/authorization/diagnostics` 返回决策步骤。
- 管理：用户/身份/Profile/Session 管理端点（需 `iam.admin.*` 权限）。
- 迁移友好：影子模式、数据投影、回滚能力。

## 运行前提

- MySQL（持久化）与 Redis（Token/Session）为必需基础设施。
- Spring Boot 自动配置，引入 `iam-spring-boot-autoconfigure` 并实现 `IdentityAuthenticator`、`MvcResourceDescriptorResolver`。

## 已知限制

- `audit/diagnostics/session` 的 `enabled` 属性已暴露，但当前自动配置未按其做条件化 bean。
- 本版本为候选发布，API 仍可能在正式版前调整。

## 源码参考

- 仓库：<https://github.com/wbh123/iam>
