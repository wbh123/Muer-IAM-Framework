---
title: 快速开始
description: 从空环境启动 MySQL、Redis 与 Spring Boot，用真实 HTTP 请求验证登录、权限、scope、profile、诊断与 session 撤销。
sidebar:
  order: 1
---

本文用仓库中唯一的消费应用 `iam-example`，从空环境启动 MySQL 8.4、Redis 7 和 Spring Boot 4，然后通过真实 HTTP 请求验证登录、permission、scope、profile、诊断和 session 撤销。当前版本仍是 `0.1.0-SNAPSHOT`（Release Candidate），尚未发布到 Maven Central。

演示账号和密码均为匿名本地数据。演示种子会清空目标库中的 IAM 表，所以只能连接 QuickStart 专用数据库，绝不能用于共享、测试验收或生产数据库。种子同时受 `dev` profile 和 `iam.example.seed-demo=true` 保护；缺少任一条件时都不会运行。

## 1. 前置条件

- Java 21；
- Maven；
- Docker 与 Docker Compose v2；
- `curl` 和 `jq`。

以下命令都从仓库根目录执行。先加载仅供本机演示的环境变量，再启动基础设施：

```bash
set -a
source examples/quickstart/.env.example
set +a

docker compose \
  --env-file examples/quickstart/.env.example \
  -f examples/quickstart/docker-compose.yml \
  up -d --wait
```

Compose 仅启动 [`mysql:8.4`](https://github.com/wbh123/iam/blob/main/examples/quickstart/docker-compose.yml) 和 [`redis:7-alpine`](https://github.com/wbh123/iam/blob/main/examples/quickstart/docker-compose.yml)，端口只绑定本机回环地址。如默认的 `3306` 或 `6379` 已被占用，可复制 `.env.example` 为本地文件并修改 `IAM_EXAMPLE_DB_PORT` 或 `IAM_EXAMPLE_REDIS_PORT`，然后在后续命令中用该文件替换 `.env.example`；JDBC URL 会引用修改后的数据库端口。

## 2. 构建并运行 `iam-example`

`iam-example` 的生产依赖只有 `iam-spring-boot-starter`。构建可执行 jar：

```bash
mvn -B -pl iam-example -am -DskipTests package
```

保持环境变量已加载，在当前终端启动应用：

```bash
java -jar iam-example/target/iam-example-0.1.0-SNAPSHOT.jar
```

应用默认监听 `http://localhost:8080`。启动时 starter 执行 IAM schema 迁移，随后 [`QuickStartDemoSeeder`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/QuickStartDemoSeeder.java) 写入 Alice、reader/editor profile、`document:read`/`document:update`、project `101` scope，以及宿主持有的文档 `1001`/`2001`。

另开一个终端，设置请求地址：

```bash
export IAM_EXAMPLE_BASE_URL=http://localhost:8080
```

健康检查是宿主公开路由，不需要 token：

```bash
curl --fail-with-body -sS "$IAM_EXAMPLE_BASE_URL/public/health"
# ok
```

## 3. 登录 reader profile

凭据由宿主的 [`ExampleIdentityAdapter`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleIdentityAdapter.java) 校验；starter 不保存明文密码。登录 Alice：

```bash
LOGIN_RESPONSE="$(curl --fail-with-body -sS \
  -X POST "$IAM_EXAMPLE_BASE_URL/iam/auth/login" \
  -H 'Content-Type: application/json' \
  --data '{"username":"alice","password":"demo-pass","clientType":"WEB"}')"

export IAM_READER_TOKEN="$(jq -r '.accessToken' <<<"$LOGIN_RESPONSE")"
export IAM_READER_SESSION_ID="$(jq -r '.sessionId' <<<"$LOGIN_RESPONSE")"
jq '{sessionId, expiresAt, principal}' <<<"$LOGIN_RESPONSE"
```

新 token 激活 profile `401`（`alice-reader-project-101`）。查看当前 principal：

```bash
curl --fail-with-body -sS \
  "$IAM_EXAMPLE_BASE_URL/iam/auth/me" \
  -H "Authorization: Bearer $IAM_READER_TOKEN" | jq
```

返回 `200`，其中 `activeProfileId` 为 `401`。也可查看当前账号可切换的两个 profile：

```bash
curl --fail-with-body -sS \
  "$IAM_EXAMPLE_BASE_URL/iam/authorization/profiles" \
  -H "Authorization: Bearer $IAM_READER_TOKEN" | jq
```

## 4. 验证 permission 与 project scope

文档路由由宿主 [`DocumentController`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/DocumentController.java) 持有，并用 `@RequirePermission` 声明 permission；[`ExampleDocumentResourceResolver`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleDocumentResourceResolver.java) 把路径变量解析成带 `PROJECT` 父路径的 `ResourceDescriptor`。

reader 具有 project `101` 的 READ scope，因此读取文档 `1001` 返回 `200`：

```bash
curl --fail-with-body -sS \
  "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H "Authorization: Bearer $IAM_READER_TOKEN" | jq
```

reader 没有 `document:update`，写入同一文档返回 `403`：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $IAM_READER_TOKEN" \
  --data '{"status":"PUBLISHED"}'
# 403
```

文档 `2001` 属于 project `202`；即使 reader token 有效，跨 project 读取仍返回 `403`：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  "$IAM_EXAMPLE_BASE_URL/api/documents/2001" \
  -H "Authorization: Bearer $IAM_READER_TOKEN"
# 403
```

## 5. 查看真实授权诊断

诊断接口调用同一个 `AuthorizationEngine`，不会在文档或前端重新计算授权。用 reader token 诊断 project `101` 上的写入请求：

```bash
curl --fail-with-body -sS \
  -X POST "$IAM_EXAMPLE_BASE_URL/iam/authorization/diagnostics" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $IAM_READER_TOKEN" \
  --data '{
    "permissionCode":"document:update",
    "domain":"EXAMPLE",
    "clientType":"WEB",
    "resourceType":"PROJECT",
    "resourceId":"101",
    "scopeAccess":"WRITE"
  }' | jq
```

响应为 `200`，业务决定中的 `allowed` 为 `false`，`decisionCode` 为 `PERMISSION_DENIED`。

## 6. 切换到 editor profile

profile 切换不会修改 reader token，而是创建一个独立 session 和替代 token。切换到 profile `402`（`alice-editor-project-101`）：

```bash
EDITOR_RESPONSE="$(curl --fail-with-body -sS \
  -X POST "$IAM_EXAMPLE_BASE_URL/iam/authorization/profiles/402/switch" \
  -H "Authorization: Bearer $IAM_READER_TOKEN")"

export IAM_EDITOR_TOKEN="$(jq -r '.accessToken' <<<"$EDITOR_RESPONSE")"
export IAM_EDITOR_SESSION_ID="$(jq -r '.sessionId' <<<"$EDITOR_RESPONSE")"
jq '{sessionId, expiresAt, principal}' <<<"$EDITOR_RESPONSE"
```

editor 具有 `document:update` 与 project `101` WRITE scope，写入返回 `200`：

```bash
curl --fail-with-body -sS \
  -X POST "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $IAM_EDITOR_TOKEN" \
  --data '{"status":"PUBLISHED"}' | jq
```

原 reader token 没有被提升，重复写入仍返回 `403`：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $IAM_READER_TOKEN" \
  --data '{"status":"REVIEWED"}'
# 403
```

## 7. 只撤销 editor session

用 editor token 撤销其自己的 session：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST "$IAM_EXAMPLE_BASE_URL/iam/sessions/$IAM_EDITOR_SESSION_ID/revoke" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $IAM_EDITOR_TOKEN" \
  --data '{"reason":"QUICKSTART_COMPLETE"}'
# 204
```

editor token 已失效，读取返回 `401`：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H "Authorization: Bearer $IAM_EDITOR_TOKEN"
# 401
```

独立的 reader session 不受影响，原 token 仍返回 `200`：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  "$IAM_EXAMPLE_BASE_URL/api/documents/1001" \
  -H "Authorization: Bearer $IAM_READER_TOKEN"
# 200
```

这条完整路径由 [`IamConsumerIntegrationTest`](https://github.com/wbh123/iam/blob/main/iam-example/src/test/java/io/github/iamstarter/example/IamConsumerIntegrationTest.java) 在 MySQL 8.4 与 Redis 7 Testcontainers 上验证。

## 8. 停止与重置

先在运行应用的终端按 `Ctrl-C`，再停止容器：

```bash
docker compose \
  --env-file examples/quickstart/.env.example \
  -f examples/quickstart/docker-compose.yml \
  down
```

需要同时删除本地演示数据卷时，使用同一命令并追加 `--volumes`。

## 9. 迁移到自己的宿主应用

生产应用通常只依赖 starter：

```xml
<dependency>
    <groupId>io.github.iamstarter</groupId>
    <artifactId>iam-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

最小连接配置与 `iam-example` 的 [`application.yaml`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/resources/application.yaml) 一致：

```yaml
spring:
  datasource:
    url: ${IAM_EXAMPLE_JDBC_URL:jdbc:mysql://localhost:3306/iam_example}
    username: ${IAM_EXAMPLE_DB_USERNAME:iam}
    password: ${IAM_EXAMPLE_DB_PASSWORD:iam-secret}
  data:
    redis:
      host: ${IAM_EXAMPLE_REDIS_HOST:localhost}
      port: ${IAM_EXAMPLE_REDIS_PORT:6379}

iam:
  enabled: true
  token:
    ttl: 8h
```

自己的应用还必须提供：

1. `IdentityAuthenticator`：宿主校验凭据并投影 `IamPrincipal`，参考 [`ExampleIdentityAdapter`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleIdentityAdapter.java)；
2. `ResourceHierarchyProvider`：宿主判断资源是否位于 scope 内，参考 [`ExampleResourceHierarchyAdapter`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleResourceHierarchyAdapter.java)；
3. 对 `/api/**` 等宿主业务路由启用 Bearer filter，参考 [`ExampleSecurityConfiguration`](https://github.com/wbh123/iam/blob/main/iam-example/src/main/java/io/github/iamstarter/example/ExampleSecurityConfiguration.java)；
4. MVC 路由使用 `@RequirePermission` 时提供 `MvcResourceDescriptorResolver`，参考上面的 document resolver。

不要把 `QuickStartDemoSeeder`、`alice/demo-pass` 或演示数据库凭据复制到生产应用。完整的稳定候选类型、配置与 HTTP 状态见 [公开 API](/reference/public-api/)，迁移所有权见 [IAM 迁移指南](https://github.com/wbh123/iam/blob/main/docs/IAM_MIGRATION_GUIDE.md)。

## 10. 自动验证

运行 QuickStart HTTP 验收：

```bash
mvn -B -pl iam-example -am -Pintegration \
  -Dtest=IamConsumerIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

验证消费应用没有导入 IAM 持久化或内部实现：

```bash
bash scripts/verify-consumer-public-api.sh
```
