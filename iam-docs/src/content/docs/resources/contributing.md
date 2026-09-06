---
title: 贡献指南
description: 如何为 IAM 提交问题、代码与文档。
sidebar:
  order: 5
---

## 代码仓库

IAM 源码位于 GitHub：<https://github.com/wbh123/iam>（分支 `main`）。

## 模块约定

- 核心模型放 `muer-core`，端口（如 `ResourceHierarchyProvider`）以接口形式暴露，由宿主或装配层实现。
- 认证/授权/会话逻辑分别在 `muer-authentication`/`muer-authorization`/`muer-session`。
- Spring 装配与 Web 契约放 `muer-spring-boot-autoconfigure`；HTTP 端点与 `openapi/iam.yaml` 放 `muer-management-web`。

## 开发流程

1. Fork 并在 `main` 切出特性分支。
2. 本地以 `dev`  profile + `muer.example.seed-demo=true` 启动 `muer-example`，用 alice/demo-pass 验证。
3. 保持 API 与本文档站一致：新增/修改端点须同步更新 `iam.yaml` 与 `src/content/docs/`。
4. 提交信息清晰说明动机与影响范围。

## 文档约定

- 内容用 Markdown（`.md`），每页含 `title/description/sidebar.order`。
- 不出现 `TODO/Coming soon/TBD`；不编造配置/端点/字段。
- 版本统一写作 `0.1.0-SNAPSHOT`（尚未发布）。
- 源码引用用 GitHub blob 链接（见各页"源码参考"）。

## 提交 Pull Request

在 GitHub 发起 PR，描述问题背景、变更与验证方式；维护者会在候选版本内评审合并。

## 许可证

Muer 采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) 授权，仓库根目录的 [`LICENSE`](https://github.com/wbh123/iam/blob/main/LICENSE) 为许可证全文。
