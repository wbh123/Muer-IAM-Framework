# Muer 0.1.0 命名空间迁移

`0.1.0` 尚未发布稳定 Maven Artifact，因此这是从历史 IAM 身份到最终 Muer 身份的最后一次 breaking cutover。稳定版只承诺最终 `cloud.muer`，不提供旧 Maven 坐标的兼容别名。

## 三段迁移路径

1. 历史 IAM namespace：`io.github.iamstarter` 与 `iam-spring-boot-starter`；
2. 过渡 Muer namespace：`io.github.muer` 与 `muer-*` artifacts；
3. 最终 Muer namespace：`cloud.muer` 与 `muer-*` artifacts。

## 最终技术身份

| 类别 | 最终值 |
| --- | --- |
| Java package root | `cloud.muer` |
| Maven group | `cloud.muer` |
| Starter | `cloud.muer:muer-spring-boot-starter:0.1.0` |
| Spring prefix | `muer.*` |
| Website | `https://muer.cloud` |
| Repository | `wbh123/Muer-IAM-Framework` |

迁移 Java 包、Maven 坐标、自动配置导入、活动文档和示例配置；Spring 属性使用 `muer.*`，例如 `muer.enabled`、`muer.token.ttl` 和 `muer.session.enabled`。

## 保持不变的运行时契约

以下是 IAM 领域或存储兼容性，而不是品牌 namespace，不要重命名：

- HTTP API：`/iam/**`；
- 数据库表与历史表：`iam_*`；
- 权限码：`iam.admin.*`；
- Redis 默认物理 value：`iam`（配置键仍为 `muer.token.redis-prefix`）。

回滚通过 Git revert 完成，不涉及数据库 rename migration。正式发布前只需恢复上一提交并重新验证，不创建 `v0.1.0` tag、GitHub Release 或 Maven 发布。
