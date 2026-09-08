# Muer 0.1.0 发布阻塞清单

本清单只记录发布审查中影响事实一致性、首次接入安全性和产品闭环的事项。

| 优先级 | 事项 | 当前状态 |
| --- | --- | --- |
| P0 | 无 | 已清零 |
| P1 | Diagnostics 请求字段与运行时不一致 | 已修复：使用 `domain`，并由文档契约检查 |
| P1 | Principal Profile 标识可空 | 已修复：OpenAPI 要求正数 `activeProfileId` / `templateVersionId` |
| P1 | 空库无法通过正式 Management API 创建授权结构 | 待补齐最小 Template、Version、Profile 生命周期 API |
| P1 | Quick Start Seeder 整表删除风险 | 已修复为固定 Demo ID 定向清理并增加冲突保护 |
| P2 | Quick Start 长教程与标题不匹配 | 待拆分短 Quick Start 与从零教程 |
| P2 | 根目录重复用户文档存在漂移风险 | 待完成归并 |
| P2 | Observability 缺少 Actuator/Micrometer 操作步骤 | 待补齐依赖、暴露和验证步骤 |
| P2 | `release/0.1.0` 分支落后于正式分支 | 待同步并重新验证 |

发布审查门槛：所有 P0/P1 必须为“已修复”，并在 CI 与运行时验证中有证据。
