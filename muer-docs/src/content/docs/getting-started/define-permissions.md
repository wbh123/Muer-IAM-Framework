---
title: 定义权限
description: 通过 PermissionDefinitionProvider 在应用启动时声明业务权限。
sidebar:
  order: 4
---

权限代码由业务应用在源码中声明，不需要额外的权限服务或手工写入 IAM 表。

```java
@Configuration(proxyBeanMethods = false)
class DocumentPermissionConfiguration {
    @Bean
    PermissionDefinitionProvider documentPermissions() {
        return () -> List.of(
                new PermissionDefinition("document:read", "Read document", "Read a document"),
                new PermissionDefinition("document:update", "Update document", "Update a document"));
    }
}
```

Starter 会在应用就绪后收集全部 `PermissionDefinitionProvider`。同一代码可由多个 Provider 重复声明，但显示名或描述不一致会让启动失败，以避免不确定的权限元数据。

## 注册规则

- 新声明会写入权限目录；
- 已存在声明只更新显示名和描述；
- 不会删除未在本次声明中出现的历史权限；
- 不会改变权限的启用状态；
- 没有 Provider 的应用仍可正常启动。

代码、显示名和描述会去除首尾空白；代码和显示名最长为 191 个字符，描述最长为 500 个字符，且不能包含控制字符。代码可使用 `:`, `.`, `-` 等常见业务分隔符。

## 权限命名约定

框架把权限代码当作**不透明字符串**精确匹配，不解析其语义、不提供通配符。下面是代码库与示例共同遵循的约定，强烈建议新项目沿用，便于阅读、管理与排障：

- **`<域>:<动作>` 两段式**：`域` 表示资源或业务领域，`动作` 表示能力。示例使用 `document:read`、`document:update`；动作用单个动词（`read` / `write` / `update` / `delete` / `execute` / `approve` 等）。
- **全部用小写**：便于区分与检索，也避免跨环境拼写不一致。
- **跨团队/子系统按前缀隔离**：为不同域或团队分配稳定前缀（如 `order:*`、`crm:*`），避免撞名；同一套代码里保持一致的分隔风格（建议一个代码只用一个分隔符类型），不要 `:` 与 `.` 混用导致歧义。
- **管理权限以 `iam.admin.` 为前缀**：框架自带的管理能力使用 `iam.admin.*`（如 `iam.admin.diagnostics`），业务域不要占用该前缀。
- **一个动作一个代码**：不要造出表达“多个能力”的组合词，也不要依赖命名去模拟通配符。授权是基于原子代码的包含判断。
- **一旦发布可读、可管理**：权限代码会被持久化并进入模板、Profile 与审计。命名模糊或随发布随意改名会造成历史授权不可读。

**保留与防冲突**：不要新声明以 `iam.` 开头的业务权限，除非确属需要与 Muer 管理面协作。**改名是有代价的**：历史 Session 里的 `templateVersionId` 仍引用旧代码，需要新建模板版本并让用户重新分配 Profile（必要时提升 authorization version），详见[权限模板生命周期](/concepts/permission-template/)。

## 从业务代码到实际授权

声明权限只建立可管理的权限目录，不能直接授予用户。完整路径是：

1. 应用用 `PermissionDefinitionProvider` 声明业务权限；
2. 管理员将已声明权限组成 Permission Template；
3. 管理员将模板关联到 Authorization Profile；
4. 管理员为 Profile 配置 Resource Scope；
5. 用户以该 Profile 登录或切换后，业务端用 `@RequirePermission` 或 `AuthorizationEngine` 执行判断。

MVC 中引用了未声明的 `@RequirePermission` 代码时，Starter 只会记录警告，不会改变原有授权决定。应把这类警告视为应用配置问题并补齐声明。

下一步请阅读[权限管理](/getting-started/permission-management/)和[@RequirePermission](/authorization/require-permission/)。
