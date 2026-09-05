---
title: 公开 API
description: IAM 对外暴露的核心接口与模型，供宿主编程集成。
sidebar:
  order: 2
---

## 认证相关

- `IdentityAuthenticator`（函数式接口）
  `Optional<IamPrincipal> authenticate(LoginRequest request);`
  宿主实现它以把现有登录映射为 IAM 主体。
- `LoginRequest`：`(username, password, clientType, clientInstance, ipAddress, userAgent, deviceType, osName, browserName, appVersion, requestId)`；`username/password/clientType` 必填，另有便捷构造器。
- `AuthenticationResult`：`(accessToken, sessionId, expiresAt, principal)`，login 与 profile switch 均返回它。

## 主体与授权模型

- `IamPrincipal`：`(userId, identityId, identityDomain, activeProfileId, templateVersionId, clientType, authorizationVersion)`。
- `AuthorizationEngine`（接口）
  `AuthorizationDecision decide(IamPrincipal, AuthorizationRequest);`
  `default void require(IamPrincipal, AuthorizationRequest)`（拒绝抛 `AuthorizationDeniedException`）。
- `AuthorizationRequest`：`(permissionCode, domain, clientType, resource, scopeAccess)`。
- `AuthorizationDecision`：`(allowed, decisionCode, steps)`，`steps` 为 `List<AuthorizationDecisionStep(code, passed, reason)>`。
- `ScopeAccess`：枚举 `READ`、`WRITE`。
- `ResourceDescriptor`：`(resourceType, resourceId, parentPath, attributes)`。
- `ResourceScope`：`(scopeType, scopeRefId, accessMode)`。
- `ResourceHierarchyProvider`（接口）：`boolean isWithinScope(ResourceDescriptor, ResourceScope);`

## 会话与配置

- `AuthSession`：`(sessionId, userId, clientType, clientInstance, ipAddress, userAgent, loginAt, lastSeenAt, expiresAt, revokedAt, logoutAt, revokeReason)`，含 `revoked()/revoke(at,reason)/touch(at)`。
- `TokenRecord`：`(sessionId, principal, expiresAt)`。
- `AuthorizationProfile`：`(profileId, userId, profileName, templateVersionId, clientTypes, enabled, revoked, validFrom, validUntil, scopes)`。
- `PermissionTemplateVersion`：`(versionId, templateId, versionNumber, status, permissions)`，`status` 为 `DRAFT/PUBLISHED/RETIRED`。

## 源码参考

- 入口总览：<https://github.com/wbh123/iam/blob/main/iam-core/src/main/java/io/github/iamstarter/core/model/>
- `AuthorizationEngine`：<https://github.com/wbh123/iam/blob/main/iam-authorization/src/main/java/io/github/iamstarter/authorization/AuthorizationEngine.java>
