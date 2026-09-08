---
title: 可观测性
description: 了解 Muer 的治理界面、健康检查与 Micrometer 运行指标。
---

Muer 提供三类互补能力：管理控制台用于治理状态与审计，Actuator 健康检查用于框架可用性，Micrometer 指标用于运行趋势。三者都不返回 token、用户、资源或权限代码。

## 健康检查

当宿主应用引入 Spring Boot Health / Actuator 类库时，Starter 提供 `muerHealthIndicator`。它返回 `UP` 和 `enabled: true`，仅表示框架已装配；不会探测 MySQL、Redis 或外部身份源。基础设施健康应由宿主应用各自的健康贡献者负责。

### 从零启用 Actuator

Actuator 是可选依赖，Muer Starter 不会强制引入。先在宿主应用添加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

启动应用后，将 `health`、`metrics`（以及需要 Prometheus 时的 `prometheus`）加入
`management.endpoints.web.exposure.include`，再访问 `GET /actuator/health`。版本由 Spring Boot
Dependency Management 统一管理，不要在示例中手工固定版本。

## Micrometer 指标

当运行时存在 `MeterRegistry` 时，Starter 自动注册指标适配器；没有注册表时使用无操作实现，不要求额外配置，也不影响认证、授权或会话行为。

需要 Prometheus 格式时，再添加：

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

| 名称 | 类型 | 标签 |
| --- | --- | --- |
| `muer.authentication.attempts` | Counter | `result`（`success` / `failure` / `other`）、`clientType`（`WEB` / `MOBILE` / `API` / `SERVICE` / `other`） |
| `muer.authorization.decisions` | Counter | `outcome`（`allow` / `deny`） |
| `muer.authorization.duration` | Timer | 无 |
| `muer.sessions.created` | Counter | 无 |
| `muer.sessions.revoked` | Counter | 无 |
| `muer.token.lookups` | Counter | `result`（`hit` / `miss` / `error` / `other`） |

指标不会带 user ID、用户名、IP、token、session ID、资源标识、权限代码或自定义决策代码等高基数/敏感维度。指标系统发生异常也不会改变原业务结果。

## 如何查看与使用

指标在你自己的 Actuator / Micrometer 端点暴露，Muer 不额外开端口。启用 `management.endpoints.web.exposure.include=health,metrics,prometheus` 后可访问：

```bash
# 框架是否装配
curl -s localhost:8080/actuator/health | jq .components.muer

# 认证尝试计数（含结果与 clientType 维度）
curl -s localhost:8080/actuator/metrics/muer.authentication.attempts

# 授权决策计数与耗时
curl -s localhost:8080/actuator/metrics/muer.authorization.decisions
curl -s localhost:8080/actuator/metrics/muer.authorization.duration
```

接入 Prometheus 后，典型告警/查询方向：

- **登录风暴或异常来源**：`rate(muer_authentication_attempts_total{result="failure"}[5m])` 持续升高时排查凭据爆破或身份源故障；
- **授权被大面积拒绝**：`rate(muer_authorization_decisions_total{outcome="deny"}[5m])` 上升时，结合授权诊断或审计定位是权限变更、Profile 失效还是 Scope 缺失；
- **授权耗时**：`muer_authorization_duration_seconds_max` 偏离基线时排查数据库/缓存延迟。

这些是聚合信号，只用来发现“异常趋势”，具体“哪个用户/哪个权限”请回到授权诊断与审计页定位，避免把高基数身份数据放进指标。

## 与治理界面的区别

管理控制台和 Management API 面向权限模板、Profile、Scope、会话和审计的治理；Micrometer 面向聚合运行信号；健康检查面向进程可用性。不要把其中任一项当作另外两项的替代品。
