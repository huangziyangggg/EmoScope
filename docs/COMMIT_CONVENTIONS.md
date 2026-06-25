# 提交信息规范

从 2026-06-20 起，心镜采用统一的提交信息格式：

```text
<type>(<scope>): <简短中文说明>
```

`scope` 使用受影响的模块，例如 `history`、`ui`、`data`、`architecture` 或 `project`。

| type | 使用场景 |
| --- | --- |
| `feat` | 新增用户可见能力 |
| `fix` | 修复缺陷 |
| `refactor` | 不改变外部行为的结构调整 |
| `docs` | 文档与说明更新 |
| `test` | 测试新增或调整 |
| `chore` | 构建、配置、素材与维护工作 |

示例：

```text
refactor(history): 拆分导出与备份职责
fix(ui): 修复小屏底部导航裁切
docs(architecture): 补充重构计划
chore(project): 更新构建兼容配置
```

一条提交只描述一个清晰结果；避免使用版本号、笼统的“更新”或混合多个无关事项的说明。
