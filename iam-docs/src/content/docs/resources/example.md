---
title: 示例场景
description: QuickStart 演示数据：用户 alice、文档与权限是如何被 seed 出来的。
sidebar:
  order: 1
---

## 演示用户

`muer-example` 的 `ExampleIdentityAdapter` 提供：

- `alice` / 密码 `demo-pass` / `clientType=WEB` → 登录默认得到 `IamPrincipal(userId=101, activeProfileId=401, templateVersionId=301)`。
- `author-a` → 独立消费身份 `app-user-101`，editor 模板 302。
- `reader-b`(userId 102)、`disabled-c`(已禁用)、`operator-a/b`。

## Seeder 数据（QuickStartDemoSeeder，dev + iam.example.seed-demo=true）

- 权限：701 `document:read`、702 `document:update`
- 模板：201 quickstart-document-reader、202 quickstart-document-editor
- 版本：301（reader，PUBLISHED，含 701）、302（editor，PUBLISHED，含 701+702）
- Profile：401 alice-reader-project-101（user101/模板301/[WEB]/默认）、402 alice-editor-project-101（user101/模板302/[WEB]/非默认）
- Scope：401→`(PROJECT,101,READ)`；402→`(PROJECT,101,READ)+(PROJECT,101,WRITE)`

## 结论

alice 默认（401）只能读 project 101 内文档；`POST`（需 WRITE）被拒 `SCOPE_DENIED`。切到 402 后具备 `document:update` + PROJECT101 WRITE，可写。

## 文档路由（DocumentController）

- `GET /public/health` → 200 `"ok"`（无认证）
- `GET /api/documents/{id}` `@RequirePermission("document:read")` → 200 Document / 404
- `POST /api/documents/{id}` `@RequirePermission(value="document:update", access=WRITE)` → 200 / 404
- `Document`：`{"id","projectId","departmentId","status"}`；resolver 把 `{id}` 解析为 `ResourceDescriptor("DOCUMENT", id, parentPath=["PROJECT:"+projectId,"DEPARTMENT:"+departmentId], {})`

## 源码参考

- 演示：<https://github.com/wbh123/iam/blob/main/muer-example/src/main/java/io/github/muer/example/>
