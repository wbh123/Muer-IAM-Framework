# IAM Spring Boot Starter 0.1.0 Release Notes

## 状态

当前候选版本为 `0.1.0-SNAPSHOT`。本仓库尚未在本文件范围内创建 tag、GitHub Release
或 Maven publication；发布版本必须在最终 release gate 和明确人工批准后才可确定。

## 范围

0.1.0 提供面向 Spring Boot 应用的 IAM starter：宿主身份认证适配、基于 permission、
profile 与 scope 的授权、opaque token 与 session 管理、审计、授权诊断、OpenAPI
管理接口，以及可选的 `@RequirePermission` Servlet MVC 拦截。

使用从 [Quick Start](QUICK_START.md) 开始；可消费边界见
[Public API Reference](PUBLIC_API.md)。

## 已知边界和限制

- starter 的默认自动配置需要 Java 21、Spring Boot 4、MySQL `DataSource` 和
  `StringRedisTemplate`。
- 默认 `ResourceHierarchyProvider` 拒绝资源范围关系；使用 scope 授权前必须实现宿主
  资源适配器。
- `/iam/**` 的 security chain 是 stateless；宿主业务路由仍由宿主 security chain
  负责，并须显式复用 IAM filter 或 principal resolver。
- `iam-example` 是当前唯一消费者示例。其完整 MySQL/Redis HTTP 集成验证依赖 Docker/
  Testcontainers 环境。
- 本版本不创建独立 IAM 服务，也不替代宿主已有的身份系统；迁移必须按 endpoint 分阶段
  进行，见 [IAM Migration Guide](IAM_MIGRATION_GUIDE.md)。

## 不属于本次范围

本次 release preparation 不新增认证协议或授权能力，不修改版本号，不发布 artifact，
不创建 Git tag 或 GitHub Release。后续产品想法应在 `0.2.0` 规划中单独评估。

## 验证记录

本地 release gate 记录在
[iam-0.1.0-release-report.md](validation/iam-0.1.0-release-report.md)。远程 CI 状态在
实际观察前为 `Pending`。
