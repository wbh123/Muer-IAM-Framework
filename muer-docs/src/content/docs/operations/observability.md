---
title: 可观测性
description: 了解 Muer 的治理界面、健康检查与 Micrometer 运行指标——以及 muer=UP 到底意味着什么。
---

## 本页完成后你会得到什么

- 能说清 Muer 三类可观测能力各看什么、别混用；
- 能从零配好 Actuator 的 `health / metrics / prometheus` 暴露；
- 能看懂 `GET /actuator/health` 的返回，并**正确解读 `muer=UP` 的边界**；
- 需要 Prometheus 时知道加什么依赖、看哪些指标。

Muer 提供三类互补能力：**管理控制台**治理状态与审计、**Actuator 健康检查**看框架可用性、**Micrometer 指标**看运行趋势。三者都不返回 token、用户、资源或权限代码。

## 先回答一个高频问题：什么时候用哪个

| 你的需求 | 用哪个 | 路径 / 方式 |
| --- | --- | --- |
| 判断应用进程是否活着 | Health | `GET /actuator/health` |
| 看某个具体 Meter 的当前值 | Metrics | `GET /actuator/metrics/<名称>` |
| Prometheus 抓取指标 | Metrics + Prometheus registry | `GET /actuator/prometheus` |
| 查某个授权为什么被拒 | 授权诊断 / 审计（不是指标） | `POST /iam/authorization/diagnostics`、`/iam/admin/audit` |
| 治理权限模板 / Profile / Scope | Management API / Console | `/iam/admin/**` |

健康与指标是“进程活着吗 / 在变坏吗”的聚合信号；要定位“具体哪个用户、哪个权限”请回到授权诊断与审计，不要把高基数身份数据塞进指标。

## 健康检查

当宿主应用引入 Spring Boot Actuator / Health 类库时，Starter 提供 `muerHealthIndicator`。它返回 `UP` 与 `enabled: true`，**仅表示 Muer 的框架 Bean 已装配**，不会探测 MySQL、Redis 或外部身份源。

### 从零启用 Actuator 并暴露端点

Actuator 是可选依赖，Muer Starter 不会强制引入。先在宿主应用添加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

再在 `application.yml` 里把需要的端点加入暴露名单（否则默认只暴露 `health`，且不展示明细）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - metrics          # 需要 Prometheus 时再加 prometheus
          - prometheus
  endpoint:
    health:
      show-details: always  # 让 /actuator/health 返回各组件明细（含 muer）
```

版本由 Spring Boot Dependency Management 统一管理，不要在示例里手工固定版本。

### 预期返回（在 show-details 下）

配好 `show-details` 后，`GET /actuator/health` 里应能看到 `muer` 组件：

```json
{
  "status": "UP",
  "components": {
    "muer": {
      "status": "UP",
      "details": { "enabled": true }
    }
  }
}
```

:::note[返回可能随你的 exposure / show-details 设置而变]
上面是 **`show-details: always`** 时的形态。若用默认 `never`，`/actuator/health` 通常只返回顶层 `{"status":"UP"}`，看不到 `components.muer`。是否能看到、能看到多少，取决于 Spring Boot 的 exposure 与 show-details 配置，以实际运行结果为准——不要假设固定字段。
:::

### 关键边界：muer = UP 不代表一切正常

```text
muer = UP  表示：Muer 的框架 Bean 已正确装配。
         不表示：MySQL 正常
                 Redis 正常
                 LDAP 正常
                 宿主 UserService 正常
```

`MuerHealthIndicator` 的实现只报告“框架可用”，**不做任何数据源探测**。所以：

- 想确认 MySQL/Redis 是否连通，看 Spring Boot 自带的 `db`/`redis` 健康组件；
- 想确认宿主用户服务正常，看你自己注册的 HealthIndicator；
- 不要因为 `muer=UP` 就误判基础设施健康，也不要在 `muer` 组件里叠加数据源探测（那是宿主与各组件各自的职责）。

## Micrometer 指标

当运行时存在 `MeterRegistry` 时，Starter 自动注册指标适配器；没有注册表时使用无操作实现，不要求额外配置，也不影响认证、授权或会话行为。

需要 Prometheus 格式时，再添加（可选）：

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 指标清单

| 名称 | 类型 | 标签 |
| --- | --- | --- |
| `muer.authentication.attempts` | Counter | `result`（`success` / `failure` / `other`）、`clientType`（`WEB` / `MOBILE` / `API` / `SERVICE` / `other`） |
| `muer.authorization.decisions` | Counter | `outcome`（`allow` / `deny`） |
| `muer.authorization.duration` | Timer | 无 |
| `muer.sessions.created` | Counter | 无 |
| `muer.sessions.revoked` | Counter | 无 |
| `muer.token.lookups` | Counter | `result`（`hit` / `miss` / `error` / `other`） |

指标不会带 user ID、用户名、IP、token、session ID、资源标识、权限代码或自定义决策代码等高基数/敏感维度。指标系统异常也不会改变原业务结果。

### 查看

指标在你自己的 Actuator / Micrometer 端点暴露，Muer 不额外开端口。启用暴露后可访问：

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

## 与治理界面的区别

管理控制台和 Management API 面向权限模板、Profile、Scope、会话和审计的治理；Micrometer 面向聚合运行信号；健康检查面向进程可用性。不要把其中任一项当作另外两项的替代品。

## 完成检查

```text
□ 引入 spring-boot-starter-actuator 后，/actuator/health 返回 200
□ 配好 show-details 后能看到 muer 组件（status UP, details.enabled=true）
□ 我理解 muer=UP 不代表 MySQL/Redis/LDAP/宿主用户服务正常
□ 想抓 Prometheus 时已加 micrometer-registry-prometheus 并把 prometheus 加入 exposure
□ 我能用 metrics 端点查 muer.authentication.attempts / muer.authorization.decisions
□ 我没有把 user ID / token / 权限码等高基数数据放进指标
```

## 常见错误

| 现象 | 原因与修法 |
| --- | --- |
| `/actuator/health` 看不到 `muer` 组件 | 未配 `show-details`，或未引入 Actuator / Health 类库；Muer Starter 不强制引入。 |
| 访问 `/actuator/prometheus` 404 | 没加 `micrometer-registry-prometheus` 依赖，或没把 `prometheus` 加进 exposure.include。 |
| 误以为 `muer=UP` 就表示数据库正常 | `muer` 只报告框架装配。数据源健康看 Spring Boot 自带的 `db`/`redis` 组件。 |
| 把 user id / 权限码写成 metric 标签 | 违反高基数/敏感维度约束。这类信息属于诊断与审计，不进指标。 |

## 下一步

- [授权诊断](/diagnostics/authorization-diagnostics/)——定位“某个请求为何被拒”的正确入口。
- [审计](/diagnostics/audit/)——管理操作与授权结果的可审计记录。
- [生产环境检查清单](/operations/production-checklist/)——上线前逐项核对。
