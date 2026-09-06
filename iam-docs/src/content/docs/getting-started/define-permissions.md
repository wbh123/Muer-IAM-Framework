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

## 从业务代码到实际授权

声明权限只建立可管理的权限目录，不能直接授予用户。完整路径是：

1. 应用用 `PermissionDefinitionProvider` 声明业务权限；
2. 管理员将已声明权限组成 Permission Template；
3. 管理员将模板关联到 Authorization Profile；
4. 管理员为 Profile 配置 Resource Scope；
5. 用户以该 Profile 登录或切换后，业务端用 `@RequirePermission` 或 `AuthorizationEngine` 执行判断。

MVC 中引用了未声明的 `@RequirePermission` 代码时，Starter 只会记录警告，不会改变原有授权决定。应把这类警告视为应用配置问题并补齐声明。

下一步请阅读[权限管理](/getting-started/permission-management/)和[@RequirePermission](/authorization/require-permission/)。
