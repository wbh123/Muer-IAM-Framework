---
title: 10~15 分钟快速开始
description: 直接运行仓库示例，完成登录、授权允许、授权拒绝与诊断闭环。
sidebar:
  order: 1
---

这条路径只做一件事：让你尽快看到 Muer 的真实授权效果。这里不要求手写认证器、权限 Provider、资源 Resolver 或业务 Controller；这些都已在仓库的 [`examples/quickstart`](https://github.com/wbh123/Muer-IAM-Framework/tree/main/examples/quickstart) 中准备好。

完成后你会验证：

| 请求 | Alice Reader |
| --- | --- |
| `GET /api/documents/1001` | `200` |
| `POST /api/documents/1001` | `403` |
| `GET /api/documents/2001` | `403` |

`1001` 属于 Alice 可读的 Project 101；她没有 `document:update`，也没有 Project 202 的 Scope。

## 1. 环境要求

- Git；
- Java 21；
- Maven 3.9+；
- MySQL 8.x 与 Redis 7；
- 可选：Docker Compose，用于启动本地 MySQL / Redis。

检查版本：

```bash
java -version
mvn -version
git --version
```

PowerShell 使用同样的三个命令。

## 2. 获取项目

### Bash

```bash
git clone https://github.com/wbh123/Muer-IAM-Framework.git
cd Muer-IAM-Framework
```

### PowerShell

```powershell
git clone https://github.com/wbh123/Muer-IAM-Framework.git
Set-Location Muer-IAM-Framework
```

## 3. 构建 Muer

0.1.0 Release Candidate 尚未发布到 Maven Central，先安装到本机 Maven 仓库：

```bash
mvn clean install -DskipTests
```

成功后，Quickstart 可以解析 `cloud.muer:muer-spring-boot-starter:0.1.0-SNAPSHOT`。

## 4. 准备 MySQL 与 Redis

如果已有可用服务，跳到下一节并设置自己的连接变量。否则使用示例附带的 Compose：

```bash
docker compose -f examples/quickstart/docker-compose.yml up -d
docker compose -f examples/quickstart/docker-compose.yml ps
```

PowerShell 命令相同。默认会准备 `localhost` 上的 MySQL 8.4 与 Redis 7。Docker 只是本地基础设施的可选启动方式，不是 Muer 的运行前提。

## 5. 启动 Quickstart

演示授权数据必须同时满足 `dev` Profile 和显式开关，默认不会写入。

### Bash

```bash
export SPRING_PROFILES_ACTIVE=dev
export MUER_QUICKSTART_SEED_DEMO=true
export MUER_DB_PASSWORD=iam-secret
mvn -f examples/quickstart/pom.xml spring-boot:run
```

### PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:MUER_QUICKSTART_SEED_DEMO = 'true'
$env:MUER_DB_PASSWORD = 'iam-secret'
mvn -f examples/quickstart/pom.xml spring-boot:run
```

若使用自己的基础设施，还可设置 `MUER_JDBC_URL`、`MUER_DB_USERNAME`、`MUER_REDIS_HOST`、`MUER_REDIS_PORT` 与 `MUER_REDIS_PASSWORD`。等待应用在 `http://localhost:8080` 启动完成。

:::caution[Seeder 只用于本地演示]
`muer.quickstart.seed-demo` 由 `examples/quickstart` 自己定义，**不是** Starter 的 `MuerProperties` 公共配置。不要在生产环境开启它，也不要依靠演示 Seeder 创建生产管理员。
:::

## 6. Alice 登录

账号是 `alice / demo-pass`，客户端类型是 `WEB`。

### Bash

```bash
curl -sS http://localhost:8080/iam/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"demo-pass","clientType":"WEB"}'
```

### PowerShell

```powershell
$login = Invoke-RestMethod -Method Post \
  -Uri 'http://localhost:8080/iam/auth/login' \
  -ContentType 'application/json' \
  -Body '{"username":"alice","password":"demo-pass","clientType":"WEB"}'
$login
```

响应包含不透明 `accessToken`。把它保存为当前终端变量：

```bash
TOKEN='<paste-access-token>'
```

```powershell
$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }
```

## 7. 验证允许结果：200

### Bash

```bash
curl -i http://localhost:8080/api/documents/1001 \
  -H "Authorization: Bearer $TOKEN"
```

### PowerShell

```powershell
Invoke-WebRequest 'http://localhost:8080/api/documents/1001' -Headers $headers
```

预期 `200`。Alice 的 Reader Profile 包含 `document:read`，Scope 覆盖 `PROJECT / 101 / READ`。

## 8. 验证拒绝结果：403

### Bash

```bash
curl -i -X POST http://localhost:8080/api/documents/1001 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"PUBLISHED"}'

curl -i http://localhost:8080/api/documents/2001 \
  -H "Authorization: Bearer $TOKEN"
```

### PowerShell

```powershell
try {
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/api/documents/1001' \
    -Headers $headers -ContentType 'application/json' -Body '{"status":"PUBLISHED"}'
} catch { $_.Exception.Response.StatusCode.value__ }

try {
  Invoke-WebRequest 'http://localhost:8080/api/documents/2001' -Headers $headers
} catch { $_.Exception.Response.StatusCode.value__ }
```

两个请求都应返回 `403`：前者缺少 `document:update`，后者超出 Project 101 Scope。

## 9. 用 Diagnostics 解释 403

Diagnostics 只分析当前已认证 Principal，不允许指定其他用户或 Profile。

### Bash

```bash
curl -sS http://localhost:8080/iam/authorization/diagnostics \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"permissionCode":"document:update","domain":"EXAMPLE","clientType":"WEB","resourceType":"PROJECT","resourceId":"101","scopeAccess":"WRITE"}'
```

### PowerShell

```powershell
Invoke-RestMethod -Method Post \
  -Uri 'http://localhost:8080/iam/authorization/diagnostics' \
  -Headers $headers -ContentType 'application/json' \
  -Body '{"permissionCode":"document:update","domain":"EXAMPLE","clientType":"WEB","resourceType":"PROJECT","resourceId":"101","scopeAccess":"WRITE"}'
```

响应中的 `allowed=false`、`decisionCode` 和 `steps` 会指出拒绝发生在哪一层。

同一个 Token 还可以调用 `GET /iam/auth/me` 查看当前 Principal；进阶验证可通过 `POST /iam/authorization/profiles/{profileId}/switch` 切换 Profile，并通过 `POST /iam/sessions/{sessionId}/revoke` 撤销当前用户自己的 Session。完整语义见 [HTTP API](/reference/http-api/)。

## 10. 配置边界

Starter 的正式公共配置来自 `MuerProperties`，包括：

```text
muer.enabled
muer.schema.enabled
muer.schema.history-table
muer.token.ttl
muer.token.redis-prefix
muer.session.enabled
muer.session.touch-interval
muer.audit.enabled
muer.diagnostics.enabled
muer.client-types
```

Quickstart 示例自己的 `muer.quickstart.seed-demo` 只有示例含义。两者共享 `muer` YAML 前缀，并不表示后者属于 Starter 公共 API。

## 11. 下一步

- [从零接入 Muer](/getting-started/from-zero-tutorial/)：理解并亲手实现示例里的各个适配器；
- [第一次使用 Admin Console](/management/first-admin-tutorial/)：从界面创建 Template、Version 与 Profile；
- [初始化第一个管理员](/management/bootstrap-first-admin/)：生产空库的安全 Bootstrap；
- [配置参考](/reference/configuration/)：核对全部正式属性；
- [排障](/operations/troubleshooting/)：处理登录、401 与 403。

你已经完成了最短闭环：启动现成示例 → 登录 → 验证 `200` → 验证 `403` → Diagnostics 定位原因。
