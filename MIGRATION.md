# Muer 0.1.x 命名空间迁移

本次发布为 breaking change。Java 包、Maven 坐标和 Spring 配置必须一次性迁移：

| 旧值 | 新值 |
| --- | --- |
| `io.github.iamstarter` | `io.github.muer` |
| `io.github.iamstarter:iam-spring-boot-starter` | `io.github.muer:muer-spring-boot-starter` |
| `iam.enabled` | `muer.enabled` |
| `iam.client-types` | `muer.client-types` |
| `iam.session.*` | `muer.session.*` |
| `iam.audit.*` | `muer.audit.*` |
| `iam.diagnostics.*` | `muer.diagnostics.*` |

认证、授权、客户端类型限制、Profile、会话撤销隔离和诊断权限边界没有变化。既有数据库表、Flyway 迁移路径、历史表与 Redis 物理键继续保留，避免引入生产数据迁移。

当前源码仓库仍为 `wbh123/iam`。创建并迁移到 `muer/muer.github.io` 后，将 Pages 构建环境改为 `SITE_URL=https://muer.github.io` 和 `BASE_PATH=/`。
