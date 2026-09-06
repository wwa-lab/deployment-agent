# 案例与证据索引

[中文首页](../../README.md) · [English overview](../../README.en.md) · [案例模板](case-template.md)

本索引只整理当前仓库。**项目方已确认当前基于 IBM iSeries 实践；本仓库尚未收录可复核的获授权脱敏实际运行包或实测收益。** 历史文件保持原始字节；其来源与 SHA-256 见[历史清单](evidence/2026-09-07-v1/historical-manifest.json)。校验值只证明文件一致性，不证明内容真实、授权有效或流程运行成功。

## 案例分层

| 案例 | 类型 / 来源版本 | 输入与产物 | 验证与人工介入 | 可得结论 / 限制 |
|---|---|---|---|---|
| C01 发布交接 mini output | 合成说明；源 commit 见哈希清单 | [release input](atlas-deployment-tool-mini-output/sample-release-input.json)、[task output](atlas-deployment-tool-mini-output/sample-task-output.json)、[audit trail](atlas-deployment-tool-mini-output/sample-audit-trail.json)、[rollback checklist](atlas-deployment-tool-mini-output/sample-rollback-checklist.md) | 本次检查 JSON 可解析和字节未变；人工编排示例，示例中的 owner/reviewer 不是实际参与记录 | 展示对象与交接形状；不是运行日志、可上传工作簿或 API 契约 |
| C02 框架采用示例 | 合成场景；[原文](atlas-framework-adoption-sample.md) | 七阶段角色与证据表、I-E-O-V 模板及采用清单 | 本次检查来源和字节；人工撰写模板，未进行真实团队试点 | 展示一种组织方式；提及其他项目不证明实际接入或案例授权 |
| C03 本次自动化复验 | 合成测试夹具 / 模拟外部调用；版本 2026-09-07-v2 | 当前 Java 测试与配置 → [测试摘要](evidence/2026-09-07-v2/test-results.json)：84 项通过 | 工具启动测试，测试以程序调用模拟用户决策；无需真实业务人员审批，不能替代人工 UAT | 只证明指定断言在此次本地环境的结果；不覆盖真实 Jenkins/AWX、Oracle 部署或业务收益 |
| C04 IBM iSeries 获授权运行证据 | 实践背景已由项目方确认，案例运行包待补 | 需按模板另建版本 | 需授权范围、脱敏审查、原始输出及人工记录 | 仓库暂缺可复核运行结果，不以 C01/C02/C03 替代 |

源基线：`abf3850dee78b13c597f7da2791dd06d201c1a66`，分支 `2026-codecup`。历史样例内部时间是合成内容，不用作执行日期。新测试的时间和源码改动状态写入摘要。

README 科技风格 v3 的[图片验证摘要](evidence/2026-09-07-readme-v3/visual-results.json)记录两张 SVG/PNG、中英文首页在 3 种宽度下的加载检查及历史素材校验。平台叙事 v2 的[视觉验证摘要](evidence/2026-09-07-platform-v2/visual-results.json)记录 18 页演示、9 种尺寸、图片加载与素材哈希。上一版的[16 页验证摘要](evidence/2026-09-07-v2/visual-results.json)及其素材保持原样；这些属于展示材料检查，不增加业务案例数量。

## 项目方提供的实践背景

2026-09-07 的定位澄清确认：平台是 Agentic SDLC 的具体实践，不限定单一交付语言；当前基于 IBM iSeries；先介绍平台，再重点讲 Deployment Agent 的原子化、自动化、智能化。附图提及 BAU → SOP → 原子任务、Jenkins Pipeline、Ansible 脚本和 IBM iSeries Health Check UTL。

该信息用于确认定位和场景，不另计一个成功案例。附图原文件 SHA-256 为 `83a22a885a1fe48e4a487c536cfcc8d479108e9ea46a2bb5187ed55e55bc5e98`；未将原始截图复制到公开材料，也不把图中效率描述视为实测。UTL 的具体调用接口、运行输出与验收仍需独立记录。

## 历史样例解读须知

C01 中 “submitted successfully” 与 `Awaiting_Review` 同时出现，是代表性手写样例的简化。真实 `AutoExecutionService` 提交成功后任务仍为 Executing；正确配置并启用轮询、收到成功结果后，才由 monitor 转为 Awaiting_Review。该样例的 `approve_task` 等字段也不是当前审计枚举的可执行契约。

不修饰或覆盖原 JSON；引用时必须带上上述限制。新运行或修正需要新版本位置，并写明与旧版差异。

C02 中上游项目及七阶段关系是合成采用设想。本次没有导入或验证其他仓库案例。历史图、历史实现说明同样保留，不作为当前集成证据。

## 能力到源码、测试的映射

以下链接提供核查入口，不能仅凭文件存在声称测试通过。此次执行范围见 C03。

| 能力 | 源码 | 现有测试 / 当前边界 |
|---|---|---|
| Excel → 发布/请求/任务 | [ImportService](../../src/main/java/com/wwa/agenthub/domain/fileimport/ImportService.java) | [ExcelImportWorkflowTest](../../src/test/java/com/wwa/agenthub/workflow/ExcelImportWorkflowTest.java)：真实内存 XLSX + H2；复上传断言只比较原请求任务数，不能推导全局不新增请求 |
| 人工结果记录 | [RecordResultService](../../src/main/java/com/wwa/agenthub/domain/task/RecordResultService.java) | [ManualTaskWorkflowTest](../../src/test/java/com/wwa/agenthub/workflow/ManualTaskWorkflowTest.java)：程序调用，不验证人工所填结果真实性 |
| 状态与权限决策 | [DecisionEngine](../../src/main/java/com/wwa/agenthub/domain/decision/DecisionEngine.java)、[TaskStateMachine](../../src/main/java/com/wwa/agenthub/domain/task/TaskStateMachine.java) | [DecisionEngineTest](../../src/test/java/com/wwa/agenthub/domain/decision/DecisionEngineTest.java)：批准/拒绝/重跑/跳过、状态和非所有者限制；无强制双人审批 |
| 下一任务就绪与阶段推进 | [ReleaseFlowProgressionService](../../src/main/java/com/wwa/agenthub/domain/decision/ReleaseFlowProgressionService.java) | [对应测试](../../src/test/java/com/wwa/agenthub/domain/decision/ReleaseFlowProgressionServiceTest.java)：关键任务待评审阻止下一任务就绪；非关键评审不具有同等阻断 |
| AUTO 提交与目标解析 | [AutoExecutionService](../../src/main/java/com/wwa/agenthub/domain/execution/AutoExecutionService.java)、[AutoExecutionAdapter](../../src/main/java/com/wwa/agenthub/domain/execution/AutoExecutionAdapter.java) | [AutoExecutionServiceTest](../../src/test/java/com/wwa/agenthub/domain/execution/AutoExecutionServiceTest.java)、[ExecutionTargetResolverTest](../../src/test/java/com/wwa/agenthub/domain/execution/ExecutionTargetResolverTest.java)：模拟 HTTP，非真实发布 |
| Ansible 参数提交 | [AnsibleExecutionAdapter](../../src/main/java/com/wwa/agenthub/domain/execution/AnsibleExecutionAdapter.java) | [AnsibleExecutionAdapterTest](../../src/test/java/com/wwa/agenthub/domain/execution/AnsibleExecutionAdapterTest.java)：模拟 HTTP，检查结构化参数 |
| 外部状态同步 | [ExternalExecutionMonitorService](../../src/main/java/com/wwa/agenthub/domain/execution/ExternalExecutionMonitorService.java) | [对应测试](../../src/test/java/com/wwa/agenthub/domain/execution/ExternalExecutionMonitorServiceTest.java)：直接测试处理方法；不证明真实调度、网络或生产事务行为 |
| 执行历史 | [TaskExecutionHistoryService](../../src/main/java/com/wwa/agenthub/domain/task/TaskExecutionHistoryService.java) | [对应测试](../../src/test/java/com/wwa/agenthub/domain/task/TaskExecutionHistoryServiceTest.java)；不是不可篡改归档认证 |
| 工作区隔离 | [AgentBoundaryGuard](../../src/main/java/com/wwa/agenthub/platform/web/security/AgentBoundaryGuard.java) | [对应测试](../../src/test/java/com/wwa/agenthub/platform/web/security/AgentBoundaryGuardTest.java)；不替代全面安全审计 |

AI Assist 在界面中是预览，仓库 Agent Skills 为开发文档工作流。iSeries 一键发布、自动回滚和全生命周期自动交付没有在以上案例中验证。

## 如何测量收益

先在获授权非生产环境选同等复杂度、同阶段的任务组，记录现有处理方式与 Hub 方式；采用多次重复而不是一次 POC。测试与采集计划尚未执行，不能写为实测收益。

| 指标 | 操作定义 | 应保留的数据 |
|---|---|---|
| 证据查找时间 | 从提出同一证据问题到定位正确结果与对应尝试；由人工校验答案正确后停止计时 | 样本数、问题、开始/结束时间、中位数与较慢分位；错误答案另计 |
| 交接完整率 | 同时具备预先规定的输入、结果、决策和尝试关联的任务数 / 纳入评价的全部任务数 | 冻结检查清单；缺项原因；失败与跳过单列，不算成功交付 |
| 异常恢复人工投入 | 从发现异常到完成恢复交接，各参与者实际操作分钟数之和 | 人工操作与等待分别记录；重跑、回滚及升级处理不能剔除 |
| 重复运行可靠性 | 满足预先定义验收条件的完整运行次数 / 所有尝试次数 | 每次版本、环境、输入哈希、结果和失败原因，不只报告成功样本 |

净人工节省 = 可比基线人工分钟 − 使用 Hub 的人工分钟 − 额外配置维护与复核分钟。各活动计时互斥，避免“查找减少”和“交接减少”重复计入；时间节省不直接折算财务收益。发布成功率、业务准确率或覆盖率需要各自明确分母，不能用单元测试数量替代。

### 三步走的补充评价口径

- **原子化：** 具备预先规定输入、操作、预期输出和验证要求的任务数 / 全部纳入梳理的任务数；分母在拆分前后分别记录，避免靠无限细分提升指标。
- **自动化：** AUTO 任务数 / 同口径可执行任务数；同时报告实际执行成功、失败、跳过和人工兜底。任务标记 AUTO 不等于成功自动交付。
- **智能化（上线后再测）：** 在独立标注的评价集上，统计建议可采纳率、错误建议与高风险建议数量，并记录人工复核成本。当前预览界面不产生智能化效果数据。

## 新证据如何入库

按[案例模板](case-template.md)建立新案例/新运行版本。原始输出在获授权位置保留；公开衍生摘要记录原文哈希、脱敏方法和限制，不覆写原始文件。

首次运行 v1 的 Maven 输出为 84 项通过，但报告目录参数没有生效，采集器将摘要标为不完整。[v1 摘要](evidence/2026-09-07-v1/test-results.json)与原始输出保留；修正采集后重新运行，v2 收齐 10 个测试类的 XML，84 项通过，无失败、错误或跳过。v1 的零计数表示摘要未采集到测试，不是没有执行测试。

本次本地原始 stdout、stderr 位于 `target/atlas-evidence/2026-09-07-v1/` 与 `target/atlas-evidence/2026-09-07-v2/`；v2 另保留 Surefire XML，不随公开材料分发。摘要保留各原始文件的哈希。该目录受构建输出清理影响，执行 clean 前应将它归档到获批准的持久位置。仓库里的摘要不是完整原始运行包。

复验脚本：[capture-atlas-evidence.mjs](../../scripts/capture-atlas-evidence.mjs)。它拒绝覆盖已有版本；使用一个新的版本标识运行：

```bash
node scripts/capture-atlas-evidence.mjs new-run-id
```

新摘要发布前仍需人工审查；测试命令不能证明客户案例授权。不得把本地日志直接复制成对外截图。
