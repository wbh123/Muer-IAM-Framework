# Muer Namespace Migration Validation Report

> 分支：`codex/muer-brand-metadata` · 范围：品牌/Maven/包名/文档迁移收口 + CI 回归修复 + Apache License 2.0
> 说明：仅修复 CI 回归、规范化身份与许可证，未新增 Permission Registry / Actuator / 新 Management API / Admin Console 功能。

## 1. Git

```text
Branch: codex/muer-brand-metadata
HEAD:   9edab6e2773c3310ec46200b2ba76174218e37d1
Main:   a62017f
Ahead:  10
Behind: 0
```

## 2. Canonical Identity

```text
Brand:           Muer
Chinese Brand:   木耳
Group ID:        io.github.muer
Root Artifact:   muer-parent
Starter Artifact: muer-spring-boot-starter
Package Root:    io.github.muer
Website:         https://muer.github.io
```

`metadata/project-metadata.yaml` 是身份 Source of Truth；`verify-project-identity.ps1` 校验 root POM groupId/artifactId(name `Muer IAM Framework`)/version、各 module 父坐标与 artifactId、以及 consumable starter 存在性。

## 3. Namespace Migration

| 项目                         | 结果 |
| ---------------------------- | ---- |
| Java package                | ✅ 全部 `io.github.muer`（运行时扫描 0 旧命名空间源码引用） |
| Maven group                 | ✅ `io.github.muer` |
| Maven modules               | ✅ 全部 `muer-*`（reactor 12 模块），遗留旧模块空壳目录已清除 |
| Starter artifact            | ✅ `muer-spring-boot-starter`（依赖链指向 muer-autoconfigure） |
| Spring properties           | ✅ 运行时前缀 `muer:`（`MuerProperties`）；demo seeder opt-in 对齐 `muer.example.*` |
| Docs                        | ✅ 活跃文档源码路径 `io/github/muer`、`MuerProperties`；迁移/历史文档（superpowers/MIGRATION.md）保留旧名标 Legacy |
| Admin Console OpenAPI path  | ✅ `iam-admin-web` `api:generate` 读取 `muer-management-web/src/main/resources/openapi/iam.yaml` |

## 4. Compatibility Kept

```text
/iam/**         保持（IAM 领域术语）
iam_*           保持（数据库表）
iam.admin.*     保持（权限码）
IAM 类型名      保持（IamPrincipal / IamAuthorizationInterceptor / IamBearerTokenFilter 等为领域名，非品牌）
redisPrefix 默认 "iam"、history-table "iam_flyway_schema_history"  保持（DB/Redis key 名）
历史组织/包名扫描 保持（迁移前组织名、旧包根与学校品牌标识继续作为 forbidden tokens）
```

## 5. License

```text
Apache License 2.0:  ✅ 已选定
LICENSE:             ✅ 仓库根官方 Apache-2.0 全文（未改写正文、无自定义限制）
POM metadata:        ✅ root pom <licenses> Apache-2.0；developer name 归一为 "Muer maintainer"
README:              ✅ 底部 "## License / Apache License 2.0"
Docs:                ✅ iam-docs resources/contributing 增加许可证说明
```

## 6. Legacy Scan

```text
legacy Java namespace:     运行时/活跃文档 0 处（仅明确标注的迁移/历史文档保留）
old Maven modules:         0 处（CI/scripts 无旧 reactor module 选择器残留）
old starter artifact:      0 处（运行时/CI 无旧 Starter artifact 引用）
legacy organization ids:   保留严格校验并通过（CI "Forbidden-identifier scan passed."）
遗留旧模块目录外壳:          已删除（12 个，均 0 跟踪文件，仅空 scaffold + target 构建产物）
```

## 7. Local Verification

| Check                       | Result |
| --------------------------- | ------ |
| Identity (ps1 断言模拟)     | ✅ PASS（root/module/starter/java package 一致） |
| Maven reactor `mvn test`    | ✅ BUILD SUCCESS（13 模块全绿） |
| AutoConfiguration           | ✅ `MuerAutoConfigurationTest` PASS |
| Starter Smoke               | ✅ `IamStarterAutoConfigurationSmokeTest` PASS |
| Management API              | ✅ `mvn -pl muer-management-web -am test` PASS（46 tests） |
| Independent Consumer        | ✅ `IamConsumerIntegrationTest` PASS |
| Testcontainers              | ✅ `IamStarterConsumptionTest` + `IamSecurityIntegrationTest` PASS |
| Admin Demo Seed             | ✅ `AdminConsoleDemoSeedIntegrationTest` PASS（本地 Testcontainers） |
| Public API Boundary         | ✅ `verify-consumer-public-api.sh` / `test-consumer-public-api.sh` PASS |
| Docs（Astro）               | ✅ `npm run check` 0 err/warn；`npm run build` 58 pages |
| Docs workflow guard         | ✅ `test-docs-workflow.sh` PASS |
| Muer identity guard         | ✅ `test-muer-identity.sh` PASS（改纯 grep，不再依赖 rg） |

## 8. Remote CI

> 注：`codex/**` 仅触发 `verify.yml`；`admin-web.yml`（Verify IAM Admin Console）与 `docs.yml`（Verify IAM Documentation）触发条件为 `main/release/feature`，故本轮分支 push 只跑 Verify IAM Starter，文档/前端 CI 在合并 main 时校验。

```text
Verify IAM Starter
Run ID:  34018573029
Result:  success（verify / Verify IAM Management API / Independent Consumer acceptance / Docker+Testcontainers consumer showcase 全 success）
```

## 9. Findings

```text
P0: 无（无阻断性正确性/安全问题；全模块本地+远端测试绿）
P1: 无（LICENSE P1 已 Resolved）
P2: 活跃参考文档的配置示例前缀仍展示旧品牌前缀，运行时前缀已是 muer:；
    iam-docs/src 与 docs/ 中少量此类文档示例待最终 README/Docs 产品化阶段统一（不影响编译/测试/扫描）。
    另：admin-web/docs CI 合并 main 前无法在本分支远端复验（触发条件限制，非回归）。
P3: 遗留 ~10 个历史 docs/superpowers 设计文档保留旧命名空间作档案，符合 Legacy 标注策略。
```

## 10. Recommendation

```text
READY FOR MUER MIGRATION REVIEW
```

本轮未自动合并 `main`。待人工确认后，再进入 Permission Registration / Runtime Observability / 最终 README-Docs 产品化重构阶段。
