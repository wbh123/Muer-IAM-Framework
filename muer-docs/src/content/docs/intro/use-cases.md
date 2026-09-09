---
title: 适合哪些项目
description: 了解 IAM Spring Boot Starter 适合解决哪类授权与 Session 问题，以及哪些场景并不合适。
sidebar:
  order: 3
---

## 它解决什么问题

当 Spring Boot 应用需要回答「这个用户能不能对这条数据做这件事」时，授权逻辑往往被写死在各处：Controller 里散落的 `if` 判断、角色与权限混淆、资源是否可见的口径不一致、出 403 只能靠猜。IAM 把这类判断收拢成统一、可解释的决策来源。

## 典型适用场景

- **多项目 / 多部门隔离**：用户只能访问自己项目、部门内的资源。用 Resource Scope 配合 `ResourceHierarchyProvider` 表达「文档属于项目 101」的空间约束。
- **同一用户多身份**：一个自然人可拥有 reader、editor 等多个 Profile，运行时切换，不同 Profile 携带不同 permission 与 scope。
- **可解释的拒绝**：每次授权都返回 `AuthorizationDecision`（含 `decisionCode` 与 `steps`），拒绝原因不再靠猜。
- **统一 Session 治理**：登录签发 Session，支持查询、撤销、撤销其他会话。

## 示例：项目内只读

Alice 的 reader profile 在 project 101 具有 `document:read` 与 READ scope，读取 `1001` 返回 200；跨项目读取 `2001` 返回 403。完整链路见[快速开始](/getting-started/quick-start/)。

## 不太适合

- 需要对外提供 OAuth2/OIDC 授权服务器的场景（0.1.0 不实现外部身份协议）；
- 需要无状态 JWT、把声明塞进 token 的架构（Token 在此为不透明令牌）。

## 下一步

从[架构总览](/intro/architecture/)了解模块划分，或直接[安装](/getting-started/installation/)。
