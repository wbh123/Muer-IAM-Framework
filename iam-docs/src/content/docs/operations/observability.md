---
title: 可观测性
description: 了解 Muer 的治理界面、健康检查与 Micrometer 运行指标。
---

Muer 提供三类互补能力：管理控制台用于治理状态与审计，Actuator 健康检查用于框架可用性，Micrometer 指标用于运行趋势。三者都不返回 token、用户、资源或权限代码。

## 健康检查

当宿主应用引入 Spring Boot Health / Actuator 类库时，Starter 提供 `muerHealthIndicator`。它返回 `UP` 和 `enabled: true`，仅表示框架已装配；不会探测 MySQL、Redis 或外部身份源。基础设施健康应由宿主应用各自的健康贡献者负责。

## Micrometer 指标

当运行时存在 `MeterRegistry` 时，Starter 自动注册指标适配器；没有注册表时使用无操作实现，不要求额外配置，也不影响认证、授权或会话行为。

| 名称 | 类型 | 标签 |
| --- | --- | --- |
| `muer.authentication.attempts` | Counter | `result`（`success` / `failure` / `other`）、`clientType`（`WEB` / `MOBILE` / `API` / `SERVICE` / `other`） |
| `muer.authorization.decisions` | Counter | `outcome`（`allow` / `deny`） |
| `muer.authorization.duration` | Timer | 无 |
| `muer.sessions.created` | Counter | 无 |
| `muer.sessions.revoked` | Counter | 无 |
| `muer.token.lookups` | Counter | `result`（`hit` / `miss` / `error` / `other`） |

指标不会带 user ID、用户名、IP、token、session ID、资源标识、权限代码或自定义决策代码等高基数/敏感维度。指标系统发生异常也不会改变原业务结果。

## 与治理界面的区别

管理控制台和 Management API 面向权限模板、Profile、Scope、会话和审计的治理；Micrometer 面向聚合运行信号；健康检查面向进程可用性。不要把其中任一项当作另外两项的替代品。
