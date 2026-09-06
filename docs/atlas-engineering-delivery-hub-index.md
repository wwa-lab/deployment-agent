# Atlas Engineering Delivery Hub 文档索引

[简体中文首页](../README.md) · [English](../README.en.md)

当前定位：Agentic SDLC 的具体实践平台；IBM iSeries 为当前实践场景，方法不限定单一交付语言。路演先讲平台，再以 Deployment Agent 展开“原子化 → 自动化 → 智能化”。详细价值叙事统一维护在[路演讲稿](atlas-engineering-delivery-hub-pitch.md)。

| 阅读目的 | 当前入口 |
|---|---|
| 问题、机制、边界 | [README](../README.md)、[完整讲稿](atlas-engineering-delivery-hub-pitch.md) |
| 报名 | [中文材料](open-collaboration-submission.zh-CN.md)、[English](open-collaboration-submission.md) |
| 路演 | [离线 HTML](atlas-engineering-delivery-hub-presentation-v2.html)；操作说明见讲稿 |
| 价值图 | [SVG 源文件](assets/atlas-delivery-value-v2.svg)、[PNG](assets/atlas-delivery-value-v2.png) |
| 能力协作图 | [SVG 源文件](assets/atlas-delivery-workflow-v2.svg)、[PNG](assets/atlas-delivery-workflow-v2.png) |
| 案例与收益测量 | [案例索引](samples/README.md)、[案例模板](samples/case-template.md)、[历史文件哈希](samples/evidence/2026-09-07-v1/historical-manifest.json) |
| 验证边界 | [本次验证记录](00-context/atlas-delivery-showcase-verification-2026-09-07.md) |
| 参与贡献 | [CONTRIBUTING](../CONTRIBUTING.md) |
| Deployment 旧入口 | [模块索引](atlas-engineering-delivery-hub-deployment-index.md) |
| SDD 依据 | [包装追踪链](00-context/atlas-engineering-delivery-hub-traceability.md)、[Deployment 追踪链](00-context/atlas-engineering-delivery-hub-deployment-traceability.md) |

本次修订说明与验证见[平台定位修订记录](00-context/atlas-agentic-sdlc-positioning-review-2026-09-07.md)。旧验证记录与素材哈希仍对应旧版本。

## 实现参考

[架构](04-architecture/architecture.md) · [设计](05-design/design.md) · [项目规则](../PROJECT_RULES.md) · [SDD Profile](00-context/sdd-profile.md) · [UAT 参考](UAT_RUNBOOK.md)

[原实现基线](wwa-agent-workspace-hub-current-baseline.md)保留作历史参考，其中端口和部分成熟度描述可能已过时，以当前源码、配置、README 和验证记录为准。

## 历史资料

以下保持原样，不能当成当前集成证明、实测收益或当前流程图：

- [框架采用合成样例](samples/atlas-framework-adoption-sample.md)
- [发布 mini output 合成样例](samples/atlas-deployment-tool-mini-output/README.md)
- [旧生命周期图](assets/atlas-framework-lifecycle.svg)、[旧 I-E-O-V 图](assets/seven-gates-ieov.svg)
- [旧发布工作流图](assets/atlas-deployment-tool-workflow.svg)、[旧上下游图](assets/atlas-deployment-upstream-downstream.svg)

旧图可能省略 AUTO 轮询条件或将未来阶段连成流程；当前演示使用上表中的 v2 图。上一版[价值图 v1](assets/atlas-delivery-value-v1.svg)、[执行状态详图 v1](assets/atlas-delivery-workflow-v1.svg)及[16 页旧演示](atlas-engineering-delivery-hub-presentation.html)保留，旧演示的发布中心定位已由本次平台定位修正。
