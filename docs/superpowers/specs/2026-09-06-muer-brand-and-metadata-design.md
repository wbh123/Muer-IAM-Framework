# Muer 品牌与工程元数据重构设计

## 目标

将当前 IAM 框架正式更名为“木耳 / Muer”，在不改变认证、授权、客户端类型约束、Profile 切换、会话撤销隔离或诊断安全边界的前提下，完成源代码、构建坐标、配置、文档和 CI 的一次性命名空间切换。

## 现状与边界

- 当前代码仓库为 `wbh123/iam`，GitHub 组织 `muer` 与 `muer/muer.github.io` 尚未创建。
- 文档站使用 Astro + Starlight，当前部署地址和仓库链接均指向旧仓库。
- 版本为 `0.1.0-SNAPSHOT`，本次采用一次性 breaking cutover：不保留旧 Java 包和旧 Maven 坐标兼容层。
- 物理数据库表、Flyway 已发布迁移、Redis 键和示例身份 `operator-a`、`operator-b` 不因品牌调整迁移。
- 当前仓库不能实际部署为 `https://muer.github.io`；部署配置将就绪，但仅在组织仓库迁移后把实际站点 URL 和仓库链接切换到 `muer/muer`。

## 命名规则

| 范围 | 新值 | 处理方式 |
| --- | --- | --- |
| Java 根包 | `io.github.muer` | 所有生产、测试、SPI、MyBatis XML 和脚本一次性迁移 |
| Maven groupId | `io.github.muer` | 根 POM、子模块 POM、示例和文档代码块同步更新 |
| Maven artifactId | `muer-*` | 现有模块一对一改名，不拆分模块 |
| Spring 配置 | `muer.*` | 绑定前缀与所有示例、测试同步迁移；新增迁移说明 |
| 公共入口类 | `Muer*` | `IamProperties`、`IamAutoConfiguration` 等少量框架入口更名；领域类保持领域名 |
| 文档品牌 | 木耳 Muer / Muer Identity | IAM 作为领域术语保留，不做机械替换 |

## 代码与安全设计

1. 使用结构化文件移动和受限文本替换，将 `src/main/java` 与 `src/test/java` 的目录、`package`、`import` 以及资源中的全限定类名迁移到 `io.github.muer`。
2. 自动配置入口重命名为 `MuerAutoConfiguration`，属性类重命名为 `MuerProperties`；`AutoConfiguration.imports` 必须引用新全限定名。自动配置条件、`SecurityFilterChain`、拦截器和鉴权执行顺序不改变。
3. 将配置绑定前缀改为 `muer`，同步示例 YAML、环境变量说明和自动配置测试。由于本次是 breaking cutover，旧 `iam.*` 不再被运行时读取；`MIGRATION.md` 明确说明替换规则、受影响的二进制 API 与回滚方式。
4. MyBatis mapper XML 的 namespace、type、javaType 跟随新 Java FQN；SQL、迁移脚本路径、表名和 Redis key 不变。
5. 所有现有认证、授权、会话、Profile、诊断和示例集成测试在重命名后必须继续通过。新增静态命名空间检查，禁止已迁移的生产和测试代码重新引入 `io.github.iamstarter` 或 `iam.*` 配置键。

## 文档与品牌设计

1. 保留 Astro/Starlight；站点标题、描述、首页、导航、页脚与 README 切换为 Muer 品牌，首页定位为开源项目入口，而非企业营销页。
2. 使用 `iam-docs/public/brand/` 提供轻量、可维护的 SVG 文字/图形标识和 favicon；遵循 Charcoal、Muer Brown、Stone Gray、Moss Green、Mist White 色板。没有正式位图时不生成低质量临时 OG 图片。
3. 现有文档内容按 Starlight 路由组织，不建立空页面；通过导航将既有内容映射为 Getting Started、Guide、Concepts、Reference、Examples、Releases。
4. 当前仓库链接保留 `https://github.com/wbh123/iam`，并在迁移说明中记录迁移到 `muer/muer` 后需要更新的链接。文档构建提供可配置的 `SITE_URL`/`BASE_PATH`，使当前项目页与未来组织主页都能正确生成 canonical、sitemap 和链接。

## CI 与发布设计

1. 保留既有 Java、管理台、文档校验工作流并改为 Muer 名称及新模块坐标。
2. Pages 工作流在 pull request 仅执行安装、检查和构建；main 构建、上传官方 Pages artifact 并部署。部署使用 GitHub 官方 Pages actions，不预设不存在的组织仓库。
3. POM 使用 Muer 名称、描述和当前可验证的 SCM 地址；组织仓库创建后仅需变更集中定义的仓库 URL。许可证、发布仓库和开发者信息不凭空补造。
4. `MIGRATION.md` 和 Release Notes 记录本次 breaking changes、配置映射、坐标映射、物理数据兼容承诺，以及组织/官网迁移后的操作清单。

## 验收标准

- Maven reactor 在新 Maven 坐标下编译，自动配置和 consumer smoke tests 通过。
- Spring Boot 可从 starter 发现 `MuerAutoConfiguration`；`muer.enabled`、客户端类型和 session 配置生效。
- 自动配置资源、MyBatis XML、SPI、脚本、文档示例与测试中没有遗留旧 Java 根包。
- 文档站 `npm run check` 与 `npm run build` 通过，生成的站点包含 Muer 首页、正确的可配置 URL 元数据与品牌资源。
- CI 对 PR 只校验 Pages 构建，对 main 才部署；任何因 GitHub 组织尚未创建而无法完成的线上地址操作都被清楚标注，而不是伪造完成。
