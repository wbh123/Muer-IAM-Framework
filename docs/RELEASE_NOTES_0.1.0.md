# IAM Spring Boot Starter 0.1.0 Release Notes

## 状态

当前候选版本为 `0.1.0-SNAPSHOT`。本仓库尚未在本文件范围内创建 tag、GitHub Release
或 Maven publication；`0.1.0` 正式版本仍须通过最终 release gate 与人工批准后发布。

本版本采用“首版即包含管理控制台”的发布方案：**IAM Admin Console 属于 0.1.0 首发范围，
不是 0.2.0 的后续功能。**

## 范围

0.1.0 提供面向 Spring Boot 应用的完整可嵌入 IAM 基线：

- 宿主身份认证适配与 opaque token 登录；
- 基于 Permission、Permission Template Version、Authorization Profile 与 Resource Scope 的细粒度授权；
- Profile 切换、授权版本、Session 生命周期与撤销；
- MySQL 持久化、Redis token 索引、审计与可解释授权诊断；
- OpenAPI 驱动的 `/iam/**` Management API 与 `@RequirePermission` Servlet MVC 授权适配；
- **可选的 `iam-admin-web` 管理控制台**（Vue 3 + TypeScript + Element Plus），用于用户、Identity、Permission、Template、Profile、Scope、Session、Audit 与 Diagnostics 管理；
- **`iam-docs` 文档站**，覆盖接入、配置、授权模型、管理控制台、部署与运维说明。

管理控制台是 0.1.0 的正式能力，但仍然是**可选客户端**：宿主只依赖
`muer-spring-boot-starter` 即可使用 IAM，Starter 启动和 Management API 均不依赖
`iam-admin-web` 静态资源。

使用从 [Quick Start](QUICK_START.md) 开始；Admin Console 的开发、部署、首个管理员初始化与
安全边界见 [IAM Admin Console Deployment](IAM_ADMIN_CONSOLE_DEPLOYMENT.md)；可消费边界见
[Public API Reference](PUBLIC_API.md)。

## 0.1.0 管理控制台安全边界

- `/iam/admin/**` 不存在管理员角色旁路，每个请求都重新经过 `AuthorizationEngine`；
- 前端菜单与路由 Capability Guard 只改善用户体验，后端授权才是安全边界；
- `POST /iam/authorization/diagnostics` 保持“当前已认证 Principal 自诊断”语义，不要求 `iam.admin.*`，也不能指定其他用户/Profile；
- `muer-example` 的 `admin-demo / demo-pass` 仅在 `dev` Profile 且显式
  `muer.example.seed-admin=true` 时创建，生产环境不会自动创建默认管理员；
- 生产第一个管理员必须由受控 SQL / migration / deployment seeder 或宿主 initial provisioning 完成，不提供公开 bootstrap HTTP 后门。

## 已知边界和限制

- starter 的默认自动配置需要 Java 21、Spring Boot 4、MySQL `DataSource` 和
  `StringRedisTemplate`。
- 默认 `ResourceHierarchyProvider` 拒绝资源范围关系；使用 scope 授权前必须实现宿主
  资源适配器。
- `/iam/**` 的 security chain 是 stateless；宿主业务路由仍由宿主 security chain
  负责，并须显式复用 IAM filter 或 principal resolver。
- `muer-example` 是当前主要消费者示例。其完整 MySQL/Redis HTTP 集成验证依赖 Docker/
  Testcontainers 环境；普通使用者部署 IAM 不要求 Docker。
- Admin Console 当前通过 Vue 单元/组件测试、OpenAPI Client 生成、TypeScript 类型检查与
  production build 验证；浏览器级 Playwright E2E 可在后续补充，但不作为 0.1.0 阻塞项。
- 本版本不创建独立 IAM 服务，也不替代宿主已有的身份系统；迁移必须按 endpoint 分阶段
  进行，见 [IAM Migration Guide](IAM_MIGRATION_GUIDE.md)。

## 不属于本次范围

0.1.0 不新增 OAuth/OIDC/SSO 等认证协议，不把 IAM 拆成独立身份服务，也不自动创建生产
超级管理员。本分支只准备 0.1.0 Release Candidate；正式 artifact、Git tag 与 GitHub
Release 仍由最终发布步骤完成。

## 验证要求

0.1.0 最终合并/发布前至少要求以下远程验证全部成功：

- `Verify IAM Starter`；
- `Verify IAM Management API`；
- `Independent Consumer acceptance`；
- `Docker/Testcontainers consumer showcase`；
- `Verify IAM Admin Console`；
- `Verify IAM Documentation`。

既有 release gate 记录见
[iam-0.1.0-release-report.md](validation/iam-0.1.0-release-report.md)。最终审查应以目标 HEAD
对应的最新 GitHub Actions 结果为准。
