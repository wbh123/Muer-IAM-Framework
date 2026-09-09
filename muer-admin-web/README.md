# muer-admin-web — Muer Admin Console

可选的 IAM 管理控制台（Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus）。

## 开发

```bash
npm ci
npm run api:generate   # OpenAPI iam.yaml → src/api/generated（生成物不提交）
npm run dev            # http://localhost:5173 ，/iam 代理到 http://localhost:8080
```

## 验证

```bash
npm run type-check
npm test
npm run build          # 产出 dist/
```

## 契约

前端不手写 DTO：`src/api/generated/` 全部由
`../muer-management-web/src/main/resources/openapi/iam.yaml` 生成。若新增/变更后端
端点，先修改 iam.yaml，再 `npm run api:generate`。

## 安全约定

- token 存 `sessionStorage`，随 `Authorization: Bearer` 发送；登出调用
  `POST /iam/auth/logout`。
- 菜单按 `GET /iam/auth/capabilities` 渲染，仅为 UI 优化；后端每条 `/iam/admin/**`
  都会经 `AuthorizationEngine` 重新授权。
- Session 页面不展示任何 token/Redis key；审计页只读。

更多部署说明见 [Muer Admin Console Deployment](https://muer.cloud/management/deploy/)。
