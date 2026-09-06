# IAM Admin Console 部署与验收指南

`iam-admin-web` 是 IAM 0.1.0 的**可选**管理控制台。它不要求 Docker，产物是纯静态
`dist/`，可以交给 Nginx / Apache / CDN，也可以由 Spring Boot 静态托管。

```
┌────────────────────────────┐        ┌──────────────────────────────┐
│  Browser / IAM Admin SPA   │  /iam  │  Spring Boot (IAM /iam/**)  │
│  static: dist/             │ ─────▶ │  /iam/auth, /iam/admin, ... │
└────────────────────────────┘        └──────────────────────────────┘
```

## 1. 构建

要求 Node.js 22+，包管理 npm。

```bash
cd iam-admin-web

npm ci
npm run api:generate
npm run type-check
npm run test
npm run build
```

`dist/` 是唯一需要部署的静态产物。`src/api/generated/` 属于生成物，不提交仓库。

普通部署者不需要运行仓库完整测试；上面的 `type-check/test/build` 主要用于开发者和发布验收。若只是部署已构建好的 `dist/`，可直接部署静态产物。

## 2. 后端要求

- Spring Boot 宿主应用引入 `muer-spring-boot-starter`，提供 MySQL（IAM schema 迁移）与 Redis。
- 宿主提供 `IdentityAuthenticator` 与 `ResourceHierarchyProvider` 适配器；IAM 自己不做用户名/密码验证。
- 管理端能访问 `/iam/**`，并拿到具有 `iam.admin.*` 权限的 Profile。
- 每条 `/iam/admin/**` 请求仍由 `AuthorizationEngine` 按 Permission + Scope + Policy 重新授权。

MySQL / Redis 可以手动安装、使用已有内网服务、云服务或容器。Admin Console 不要求 Docker。

## 3. Nginx 参考配置

```nginx
server {
    listen 443 ssl http2;
    server_name iam.example.com;

    ssl_certificate     /etc/letsencrypt/live/iam.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/iam.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    root /srv/iam-admin-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /iam/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_pass_header Authorization;
    }
}
```

也可把前端挂在 `/admin/`，但需要同步配置 Vite base、静态路径与 SPA history fallback。

### 代理头 / CORS / 同源

- 生产推荐**同源**：静态前端与 `/iam/**` 同一 Origin，无 CORS。
- 若前端与 API 不同源，只对显式白名单 Origin 开放 CORS；不要使用 `Access-Control-Allow-Origin: *`。
- 若宿主位于可信反向代理之后，按组织网络边界处理 `X-Forwarded-For` / `X-Forwarded-Proto`。

## 4. 前端环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `VITE_IAM_API_BASE_URL` | 空（同源） | 开发/跨源调试时指向后端，如 `http://localhost:8080` |

开发模式 `vite dev` 会把 `/iam` 代理到 `http://localhost:8080`。

## 5. CSP 与 Token 安全

- 生产建议配置 CSP，并根据实际静态资源来源收紧 `script-src/connect-src/frame-ancestors`。
- token 只存 `sessionStorage`，随 `Authorization: Bearer` 头发送；禁止：
  - `console.log(token)`；
  - token 出现在 URL query；
  - 错误日志/审计元数据携带 token；
  - 用 Cookie 伪装 IAM session。
- 登出必须调用 `POST /iam/auth/logout`，而不是只清浏览器。

## 6. Spring Boot 静态托管（可选）

可以由宿主应用或外部 Web Server 托管 `dist/`。生产环境更推荐独立静态服务器/CDN + 反向代理，而不是把前端构建工具带入后端运行环境。

## 7. 本地手工启动（muer-example）

先准备：

- MySQL 8.x 空数据库；
- Redis 7；
- Java 21；
- Node.js 22+；
- npm。

后端常用环境变量：

```text
SPRING_PROFILES_ACTIVE=dev
IAM_EXAMPLE_SEED_ADMIN=true
IAM_EXAMPLE_JDBC_URL=jdbc:mysql://127.0.0.1:3306/iam_example
IAM_EXAMPLE_DB_USERNAME=iam
IAM_EXAMPLE_DB_PASSWORD=<your-password>
IAM_EXAMPLE_REDIS_HOST=127.0.0.1
IAM_EXAMPLE_REDIS_PORT=6379
```

启动 `IamExampleApplication` 后，再启动前端：

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

浏览器访问 Vite 输出地址，通常为 `http://localhost:5173`。

### 开发演示管理员

> ⚠️ `admin-demo / demo-pass` 只用于本地开发与演示，生产环境不会自动创建该账户。

必须同时满足：

```text
SPRING_PROFILES_ACTIVE=dev
IAM_EXAMPLE_SEED_ADMIN=true
```

登录信息：

```text
username: admin-demo
password: demo-pass
clientType: WEB
```

Seed 创建 IAM Admin Template、Profile 以及示例管理 Scope；每个 `/iam/admin/**` 请求仍经 `AuthorizationEngine` 授权，不存在 Role bypass。

## 8. 手工验收清单

普通使用者无需跑仓库完整 CI。建议在专用开发数据库完成以下浏览器验收：

### 登录与导航

- `admin-demo / demo-pass / WEB` 可以登录；
- Dashboard 正常加载；
- Users、Permissions、Templates、Profiles、Sessions、Audit、Diagnostics 页面可进入；
- `/account` 在登录后可访问。

### 用户与授权配置

- 用户列表和详情可读取；
- Identity / Profile / Session 子信息可读取；
- 修改一个开发 Profile 或 Scope 后保存成功，刷新页面仍能读取新值；
- Template/Profile 的只读与可编辑状态符合后端真实状态约束。

### Session 与 Audit

- 撤销一个测试 Session 后，目标 Session 对应 Token 失效；
- 其他独立 Session 不被连带撤销；
- Audit 页面能够查询到相关管理操作。

### Diagnostics 与权限边界

- Diagnostics 能返回 ALLOW/DENY、decisionCode 和决策步骤；
- `POST /iam/authorization/diagnostics` 仍是当前 Principal 的 self-diagnostics；
- 对缺少某项 `iam.admin.*` Capability 的测试用户，前端路由进入 403；
- 即使绕过前端直接请求 `/iam/admin/**`，后端也必须返回 403。

### Logout

- 点击退出后前端状态清理；
- 后端 logout 已执行；
- 原 Token 不能继续访问受保护接口。

如果这些路径符合预期，就足以完成普通手工验收；Testcontainers、Independent Consumer 等完整回归由项目 CI 负责。

## 9. 生产第一个管理员（部署期 Bootstrap）

生产环境**没有**默认账户、没有万能密码，也不提供类似
`POST /iam/bootstrap/admin` 的公开 HTTP 初始化端点。

第一个管理员应通过受控流程创建，例如：

```text
创建业务用户
    ↓
创建 Identity
    ↓
创建 IAM Admin Permission Template + PUBLISHED Version
    ↓
创建 Admin Profile 并绑定该 Version
    ↓
按宿主 hierarchy 授予管理所需 Resource Scope
    ↓
启动 Admin Console
    ↓
之后由 Console 管理其他管理员
```

可使用受控 SQL、migration、deployment seeder 或宿主系统自己的 initial provisioning。

Permission Code 必须与当前后端源码/OpenAPI 一致；Resource Scope 也不能作为 bypass，生产宿主必须按自己的资源层级实现 `ResourceHierarchyProvider`。

## 10. 说明与边界

- Admin Console 是 0.1.0 正式能力，但仍是可选客户端；Starter 正常运行不依赖前端。
- 管理控制台页面隐藏和 Router Guard 只属于 UX，真实权限始终由后端执行。
- 生产账户凭据由宿主 `IdentityAuthenticator` 对接的身份源负责，IAM 不保存宿主密码。
