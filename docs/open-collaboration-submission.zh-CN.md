# 开放协作提交材料：Atlas Engineering Delivery Hub

## 摘要

Atlas Engineering Delivery Hub 是一个 Framework 类参赛项目，面向团队交付治理。它用 Seven Mountains SDLC 组织工程工作，并为每个阶段提供统一的 I-E-O-V 运行模型：Input、Execute、Output、Validate。

当前仓库通过 WWA Agent Workspace Hub 的实现基线展示这个 framework：Spring Boot 后端、Vue 3 前端、共享工作台、Agent/function 工作区、范围化访问治理、配置管理、审计日志、SDD 追踪链，以及 Human-in-the-Loop 工作流控制。

## 参赛类别

**Framework**

本项目符合 Framework 类别，因为它不是一个单一工具或 deployment 脚本，而是一套可复用的方法，用来组织团队在多个 SDLC function 中的阶段、证据、执行、验证和贡献方式。

## 两个参赛项目

| 项目 | 类别 | 关系 |
|---|---|---|
| Atlas Engineering Delivery Hub | Framework | 上层 team framework，也是这个仓库的主定位。 |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | Hub 中的一个 function，作为 M6 Deployment 和 IBM iSeries one-click release UTL 设计方向的独立项目。 |

Deployment 是 Hub 的一个 function，不是整个 Hub。

## 解决的问题

交付工作常常散落在 planning notes、表格、build jobs、test reports、release runbooks、审批和生产反馈中。团队容易失去以下清晰度：

- 开始前需要哪些 input evidence；
- 谁负责每个阶段和每个决策；
- 哪些工作由人、Agent 或外部工具执行；
- 哪些 output records 可以长期留存；
- 验证和审批是如何发生的；
- 后续团队如何追踪决策链路。

Atlas Engineering Delivery Hub 为这些交接提供统一的 team framework。

## Framework 模型

Seven Mountains SDLC：

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

每个阶段遵循 Seven Gates / I-E-O-V：

| 元素 | 含义 |
|---|---|
| Input | 必需交付物、范围、Owner、约束和前置条件。 |
| Execute | 由人、Agent、自动化或外部工具完成的受控工作。 |
| Output | 可留存的产物、决策、运行记录和可追踪结果。 |
| Validate | 评审检查、证据门、审批和审计记录。 |

## 当前仓库提供的能力

- 可运行的共享 delivery workspace shell。
- 面向 Build、Testing、Deployment 类工作流的 Agent/function 工作区模式。
- Release Flow -> Request -> Task 追踪模型。
- 任务决策的人工评审门。
- 基于本地 Access Grant 的范围化访问治理。
- 带 user、action、scope、agent 和 correlation context 的审计日志。
- 面向可复用执行目标的配置管理。
- 基于模板和 Excel 的工作流 onboarding。
- 连接 requirements、user stories、specs、architecture、design 和 tasks 的 SDD 文档。
- 面向开放协作的 framework 文档、图表、贡献指南和合成样例。

## Deployment 作为独立 Function

Deployment 是当前仓库里最具体的 function。它覆盖受控的 SIT / UAT / PROD 发布运行，包括手动和 AUTO 任务执行、Jenkins/Ansible 适配器、评审决策、执行历史和审计。

作为第二个参赛项目，Deployment 可以独立呈现，因为它有完整的 function-level 故事，并承载 IBM iSeries one-click release UTL 的设计方向。Hub 提供 team framework，Deployment 展示一个 function 如何落成可复用的运行工具。

详见 [Deployment 提交材料](open-collaboration-submission-deployment.zh-CN.md)。

## 跨团队复用价值

团队可以复用：

- 生命周期和 gate 语言；
- SDD 文档链；
- 工作流壳和任务推进模式；
- 证据与审计模型；
- 贡献和验证规则；
- Deployment 展示的 function 包装方式。

这个 framework 故意保持可适配：不同团队可以接入不同的 Discovery、Build、Testing、Deployment 或 Maintenance function，而不需要重写上层 operating model。

## 与开放协作主题的关系

Hub 适合共建：

- 每个 function 可以独立演进，同时保持同一套 stage/gate 模型。
- 文档和 SDD artifact 让范围对人和 AI Agent 都清晰。
- 合成样例可以安全共享，不暴露客户或生产数据。
- 贡献规则保护凭证、审批、审计和回滚姿态。
- Deployment function 提供一个具体的参考实现。

## 已交付材料

- [英文 README](../README.md)
- [中文 README](../README.zh-CN.md)
- [Framework 文档索引](atlas-engineering-delivery-hub-index.md)
- [Framework Pitch](atlas-engineering-delivery-hub-pitch.md)
- [贡献指南](../CONTRIBUTING.md)
- [Framework 生命周期图](assets/atlas-framework-lifecycle.svg)
- [Seven Mountains SDLC 图](assets/seven-mountains-sdlc.svg)
- [Seven Gates I-E-O-V 图](assets/seven-gates-ieov.svg)
- [合成 framework adoption sample](samples/atlas-framework-adoption-sample.md)
- [Framework SDD 追踪链](00-context/atlas-engineering-delivery-hub-traceability.md)
- [Deployment function package](atlas-engineering-delivery-hub-deployment-index.md)

## 演示故事

1. 从 Seven Mountains SDLC 地图开始。
2. 说明 I-E-O-V gate 是团队共同的运行契约。
3. 展示 framework 文档和 SDD 追踪链。
4. 打开可运行的 WWA Agent Workspace Hub。
5. 用 Deployment 作为具体 function 示例。
6. 展示 release rundown、task flow、decision gate 和 audit trail。
7. 说明其他 function 如何复用同一套 framework 结构。

## 共建机会

- 为 Discovery、Build、Testing 或 Maintenance 增加 function-level package。
- 改进 framework adoption samples。
- 增加脱敏的 stage gate 模板和证据样例。
- 强化 Markdown、Mermaid 和 SDD 验证脚本。
- 在 Deployment function 下扩展 IBM iSeries one-click release UTL 设计。
- 优化 operator、reviewer 和 contributor 的前端体验。

## 安全边界

- 凭证不得进入文档、样例、截图或提交的工作簿。
- 不提交真实环境名称和客户数据。
- 审批与回滚能力描述必须与实际实现一致。
- 非平凡或面向用户的变更必须更新相关 SDD artifact。

## 链接

- [README](../README.md)
- [中文 README](../README.zh-CN.md)
- [Framework 文档索引](atlas-engineering-delivery-hub-index.md)
- [Framework Pitch](atlas-engineering-delivery-hub-pitch.md)
- [Deployment 文档索引](atlas-engineering-delivery-hub-deployment-index.md)
- [贡献指南](../CONTRIBUTING.md)
- [当前实现基线](wwa-agent-workspace-hub-current-baseline.md)
