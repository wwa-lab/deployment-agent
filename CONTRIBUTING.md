# 参与 Atlas Engineering Delivery Hub

[中文首页](README.md) · [English overview](README.en.md) · [文档索引](docs/atlas-engineering-delivery-hub-index.md)

贡献围绕 Agentic SDLC 平台实践：先定义原子任务与输入输出，再适配自动执行，逐步验证智能编排与辅助决策。当前以 IBM iSeries 为实践场景，Deployment 为重点展示模块；任务契约、执行器适配和治理机制应保持可复用。新增贡献不自动成为独立参赛方案。

## 从哪里开始

- 完善[案例索引与模板](docs/samples/README.md)，提交获授权的脱敏案例或明确标记的合成样例。
- 为导入、状态决策、执行适配与权限增加有意义的验证。
- 改进 README、路演与图片的可读性，并按证据限制能力声明。
- 贡献 Jenkins/AWX 的配置、失败、状态同步测试；接入新执行器须说明真实接口与实际验证环境。

## 文档与证据

README.md 为简体中文默认入口，README.en.md 提供完整英文；README.zh-CN.md 保留兼容链接。详细价值叙事在路演讲稿，证据与收益口径在案例索引维护，报名材料引用它们。

不要改写历史样例、截图、运行输出或来源以统一品牌。新运行、新修正放入新的版本目录，记录来源 commit、输入/输出 SHA-256、验证方式、人工介入和结果。合成案例不能称为真实实践；测试通过不能推导生产可用或业务收益。

对外文案使用 Agent Skills 等工具中立表述。带工具品牌的现有技术目录、桥接文件和真实 CLI 命令有实际用途；迁移前必须核查调用、引用与受影响入口，不能机械替换为假想的通用命令。当前文档调整不进行目录迁移，也不宣称新增执行器兼容。

项目规则及 SDD 继续使用英文。开始非平凡实现前阅读 [项目规则](PROJECT_RULES.md)、[开发标准](DEVELOPMENT_STANDARDS.md)和[SDD Profile](docs/00-context/sdd-profile.md)，更新相关规格与任务。

## 安全与发布职责

样例使用合成值或获授权脱敏数据；不得加入个人姓名、真实 Staff ID、客户数据、凭据或未经授权内部信息。报名个人字段留空。原始运行输出留在获批准位置，先检查公开范围再提供衍生摘要，保留原始校验值。

AUTO 执行器必须使用现有配置与凭据管理机制，不能硬编码端点和密钥。适配变更须给出接口、模拟测试、实际验证范围和未验证项。默认关闭的轮询不是已完成的生产同步验证。

人工批准、拒绝、重跑和跳过必须保留现有状态及权限约束。跳过不是成功证明。当前所有者/管理员决策不等于强制双人审批。基础设施回滚由团队批准的恢复流程负责，不能仅凭检查表宣称一键回滚。

## 如何验证

| 改动 | 最低验证 |
|---|---|
| 文档 | `git diff --check`、`node scripts/check-markdown-links.mjs`，人工核对声明与源码 |
| SVG/PNG/HTML | 查看实际渲染；检查文字裁切、分支/箭头、图片链接、离线加载、键盘与备注 |
| 后端 | `mvn test` |
| API | 对应控制器/契约测试 |
| 前端应用 | frontend 目录执行 `npm run build`，UI 改动提供安全的前后截图 |
| 新执行器 | 模拟调用与状态映射测试；真实环境验收单独记录，不混写 |

演示直接用浏览器打开，不需要 Python。若运行文档一致性扫描，在仓库根目录使用：

```bash
python3 .agents/skills/review-docs-against-code/scripts/doc_consistency_scan.py README.md README.en.md
```

Windows 11 对应命令：

```powershell
py -3 .agents/skills/review-docs-against-code/scripts/doc_consistency_scan.py README.md README.en.md
```

## 提交前检查

确认双语主张一致、链接和图片有效、案例来源可追溯、无个人或机密数据；在变更说明里写出测试范围、结果与未验证事项。保护现有工作区改动，不修改凭据、锁文件或 CI secrets。提交采用 conventional commit；只有明确要求时才执行 commit、push 或 merge。
