# IAM Admin Console 部署指南（development）

`iam-admin-web` 是 IAM Starter 的**可选**管理控制台。它不要求 Docker，产物是纯静态
`dist/`，可以交给 Nginx / Apache / CDN，也可以由 Spring Boot 静态托管。

```
┌────────────────────────────┐        ┌──────────────────────────────┐
│  Browser / IAM Admin SPA   │  /iam  │  Spring Boot (IAM /iam/**)    │
│  static: dist/             │ ─────▶ │  /iam/auth, /iam/admin, ...    │
└────────────────────────────┘        └──────────────────────────────┘
```

## 1. 构建

要求 Node.js 22+，包管理 npm。

```bash
cd iam-admin-web

npm ci                 # 与 package-lock.json 一致
npm run api:generate   # 由 ../iam-management-web/src/main/resources/openapi/iam.yaml 生成 TypeScript client（不提交）
npm run type-check
npm run test
npm run build          # 产出 dist/
```

`dist/` 是唯一需要部署的静态产物。`src/api/generated/` 属于生成物，不提交仓库。

## 2. 后端要求

- Spring Boot 宿主应用引入 `iam-spring-boot-starter`，提供 MySQL（IAM schema 迁移）与 Redis。
- 宿主提供 `IdentityAuthenticator` 与 `ResourceHierarchyProvider` 适配器；IAM 自己不做
  用户名/密码验证。
- 管理端能访问 `/iam/**`，并拿到具有 `iam.admin.*` 权限的 Profile。也就是说“IAM 管理
  IAM”：登录用户的 active profile 对应的 Template Version 必须包含所需的
  `iam.admin.*` permission code，且其 resource scope 通过宿主 hierarchy 适配器判定为
  覆盖管理目标（例如给 Profile 授予 `IAM_ADMIN_CONSOLE` 类 scope）。

## 3. Nginx 参考配置

```nginx
server {
    listen 443 ssl http2;
    server_name iam.example.com;

    # TLS 必须；生产禁用 TLSv1 / TLSv1.1
    ssl_certificate     /etc/letsencrypt/live/iam.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/iam.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # 前端静态文件（iam-admin-web/dist）
    root /srv/iam-admin-web/dist;
    index index.html;

    # SPA history 回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反向代理到 Spring Boot（IAM 路由固定前缀 /iam）
    location /iam/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP         $remote_addr;
        # opaque Bearer token 通过 Authorization 头传递，绝不落入 URL/cookie
        proxy_pass_header Authorization;
    }
}
```

也可把前端挂在 `/admin/`：`location /admin/ { alias /srv/iam-admin-web/dist/; try_files ... }`。

### 代理头 / CORS / 同源

- 生产推荐**同源**：静态前端与 `/iam/**` 同一 Origin，无 CORS。
- 若前端与 API 不同源，只对显式白名单 Origin 开放 CORS；**不要** `Access-Control-Allow-Origin: *`，
  尤其不要配合 Bearer 凭证。
- Spring Boot 侧若需要读取客户端 IP，从 `X-Forwarded-For` 信任的第一跳取值；该值也会被
  login 记入 `iam_login_event.ip_address` 与 `iam_session.ip_address`。
- 若宿主本身已在 Nginx 之后还有一层 LB，只信任内网入口写入的 `X-Forwarded-For`。

## 4. 前端环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `VITE_IAM_API_BASE_URL` | 空（同源） | 开发/跨源调试时指向后端，如 `http://localhost:8080` |

开发模式 `vite dev` 会把 `/iam` 代理到 `http://localhost:8080`。

## 5. CSP 与 Token 安全

- 生产响应建议 CSP：
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'self'; frame-ancestors 'none'`
  （Element Plus 内联样式需要 `style-src 'unsafe-inline'`；如不内联 CSS 可收紧）。
- token 只存 `sessionStorage`，随 `Authorization: Bearer` 头发送；**禁止**：
  - 前端 console.log token；
  - token 出现在 URL query；
  - 错误日志/审计元数据携带 token；
  - 用 Cookie 伪装 IAM session。
- 登出必须调用 `POST /iam/auth/logout`，而不是只清浏览器。

## 6. Spring Boot 静态托管（可选，不推荐生产）

```java
registry.addResourceHandler("/admin/**")
        .addResourceLocations("file:/srv/iam-admin-web/dist/");
```

## 7. 说明与边界

- Starter 正常运行**不依赖** admin 前端；只部署后端时 `/iam/**` Management API 依然可用。
- 管理控制台自身的每条 `/iam/admin/**` 请求都会再次经过 `AuthorizationEngine` 细粒度
  授权。菜单隐藏只是 UI 优化，不是安全边界。

## 8. 本地开发启动体验（iam-example，Explicit Opt-In）

> ⚠️ `admin-demo / demo-pass` **只**用于本地开发与演示，生产环境不会自动创建该账户。

同时满足下面两个条件才会创建开发管理员：

```text
spring.profiles.active=dev   (或 SPRING_PROFILES_ACTIVE=dev)
iam.example.seed-admin=true  (或 IAM_EXAMPLE_SEED_ADMIN=true)
```

默认均为 `false`：普通 dev 与“只有配置项、没开 dev profile”都不会执行 seed。

后端（iam-example）：

```bash
SPRING_PROFILES_ACTIVE=dev IAM_EXAMPLE_SEED_ADMIN=true \
  IAM_EXAMPLE_JDBC_URL=jdbc:mysql://localhost:3306/iam_example ... \
  mvn -pl iam-example -am spring-boot:run
```

前端：

```bash
cd iam-admin-web
npm ci
npm run api:generate
npm run dev
```

然后用下面的凭据在 Web 页面登录（Client Type 选择 `WEB`）：

```text
username: admin-demo
password: demo-pass
```

Seed 会创建（id 均为 iam-example 专用，不会出现在宿主生产 schema）：

- 用户 `admin-demo` + Identity `identity-admin-demo`（EXAMPLE 域）；
- `IAM Admin Console` Template 的 PUBLISHED v1，包含全部真实 `iam.admin.*`
  permission code（以源码为准，共 18 个：overview/user/identity/permission/
  template/profile/scope/session/audit/diagnostics/authorization-version…）；
- Profile `admin-console`，Scope `IAM_ADMIN:*` READ + WRITE —— 该 Scope 只对
  `IAM_*` 管理资源生效，不覆盖宿主业务资源（PROJECT 等仍需各自 scope）。

> `iam-admin-web` 里的菜单按 `/iam/auth/capabilities` 渲染；后端不做任何角色旁路，
> 每条 `/iam/admin/**` 依旧经 `AuthorizationEngine` 授权。

## 9. 生产第一个管理员（部署期 Bootstrap）

生产环境**没有**默认账户、**没有**万能密码，也**不提供**类似
`POST /iam/bootstrap/admin` 的公开 HTTP 初始化端点（避免首次启动暴露、初始化竞态与
安全风险）。第一个管理员应通过受控的部署流程创建，例如：受控 SQL / migration /
deployment seeder，或宿主系统自己的 initial provisioning。

推荐流程：

```text
创建业务用户
    ↓
创建 Identity
    ↓
创建「IAM Admin」Permission Template + PUBLISHED Version（18 个 iam.admin.*）
    ↓
创建 Admin Profile 并绑定该 Version
    ↓
按宿主 hierarchy 授予管理所需的 Resource Scope（含 READ/WRITE 与资源范围）
    ↓
启动 Admin Console，用该账户登录
    ↓
后续管理员由 Console 内部管理
```

要点：

- Permission Code 必须与后端源码一致，不要自己造码（模板权限以真实 `iam.admin.*`
  为准，见 `docs/PUBLIC_API.md` 与 OpenAPI）。
- Resource Scope 不是 bypass：必须让 profile 的 scope 覆盖你要管理的目标；示例应用用
  `IAM_ADMIN:*` 覆盖 `IAM_*` 资源，是**示例宿主**的 hierarchy 规则，生产宿主需按自己的
  资源模型定义等价规则。
- 管理账户的初始凭据由你的 provisioning 流程发放与轮换；IAM 侧只认
  `IdentityAuthenticator` 的验证结果。
