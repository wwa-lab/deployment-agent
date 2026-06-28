# Atlas Engineering Delivery Hub

**参赛类别：** Framework
**主叙事：** Team delivery framework
**独立 function：** [Atlas Engineering Delivery Hub - Deployment](docs/atlas-engineering-delivery-hub-deployment-index.md)
**English README:** [README.md](README.md)

Atlas Engineering Delivery Hub 是一个面向团队交付的框架，用来把软件交付过程变得可见、可治理、可追踪、可复用。它围绕 Seven Mountains SDLC，并用统一的 I-E-O-V gate 模型描述每个阶段：Input、Execute、Output、Validate。

这个仓库承载的是当前 Atlas Engineering Delivery Hub 的实现基线，用真实软件展示这个 framework。部分历史技术标识仍然保留 `WWA` 或 `deployment-agent` 以保证兼容，但可见产品品牌统一为 Atlas Engineering Delivery Hub。Deployment Agent 是 Hub 里的一个已实现 Agent，不是整个 Hub。针对这次公司开源/开放协作大赛，本仓库支持两个相关但层级不同的项目：

| 项目 | 类别 | 展示重点 | 入口 |
|---|---|---|---|
| Atlas Engineering Delivery Hub | Framework | 一套可复用的团队 SDLC 工作框架，覆盖阶段模型、共享工作台、人工治理、证据、审计和贡献方式。 | 本 README、[Framework 文档索引](docs/atlas-engineering-delivery-hub-index.md)、[Framework 提交材料](docs/open-collaboration-submission.md) |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | Deployment Agent 作为 M6 Deployment 能力：受控的 SIT / UAT / PROD 发布运行能力，以及 IBM iSeries one-click release UTL 的设计方向。 | [Deployment 文档索引](docs/atlas-engineering-delivery-hub-deployment-index.md)、[Deployment 提交材料](docs/open-collaboration-submission-deployment.md) |

![Atlas Engineering Delivery Hub framework lifecycle](docs/assets/atlas-framework-lifecycle.svg)

## Framework 定位

Hub 的定位是一套 team framework，而不是单个自动化脚本，也不是一个 deployment 页面。它给团队一套共同语言：每个交付阶段需要什么输入、如何受控执行、产出什么证据、怎样验证结果，以及如何把决策保留下来供复盘和审计。

当前实现通过 Spring Boot 后端、Vue 3 前端、共享平台壳、Agent 工作区、访问治理、审计日志、配置管理、Excel 导入和人工评审门来展示这个框架。有些生命周期 function 已经实现较深，有些是框架方向；当某个 function 的工作价值足够完整时，它可以作为独立项目参赛或共建。

## Seven Mountains SDLC

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

| 阶段 | 在团队框架中的目的 | 当前仓库信号 |
|---|---|---|
| M1 Planning | 对齐目标、范围、参与者和审批准备度。 | Agent Contribute Dashboard 与 framework 文档。 |
| M2 Estimation | 记录工作量、排期、风险和资源预期。 | Agent Contribute Dashboard 与 framework 文档。 |
| M3 Discovery | 把业务意图转为需求、规格和设计证据。 | 目标阶段；Atlas Phoenix Lens / Legacy Spec Factory 可作为上游示例能力接入。 |
| M4 Build | 将已批准设计转化为代码、任务、验证记录和构建产物。 | Build Agent 工作区与共享任务流基线。 |
| M5 Testing | 在发布前形成验证证据。 | Testing Agent 工作区方向与共享任务流基线。 |
| M6 Deployment | 协调发布执行、审批、回滚姿态和审计。 | 本仓库已实现的 Deployment function。 |
| M7 Maintenance | 将事件、生产反馈和改进重新带回交付生命周期。 | 框架目标阶段和路线图方向。 |

## Seven Gates：I-E-O-V

每个生命周期阶段都遵循同一种操作结构：

| Gate 元素 | 含义 |
|---|---|
| Input | 阶段开始前需要的交付物、Owner、范围、约束和前置条件。 |
| Execute | 由人、Agent、自动化或外部工具完成的受控工作。 |
| Output | 可留存的产物、决策、运行记录和可追踪结果。 |
| Validate | 评审检查、测试证据、审批、审计记录和验收结论。 |

这样团队可以在 Planning、Build、Testing、Deployment、Maintenance 等阶段复用同一种治理语言，同时允许每个 function 拥有自己的 workflow 和工具。

![Seven Gates I-E-O-V](docs/assets/seven-gates-ieov.svg)

## Framework 能力

当前已实现或已文档化的能力包括：

- 支持多个交付 function 的共享工作台。
- 面向 Build Agent、Testing Agent、Deployment Agent 工作流的 Agent/function 工作区。
- Human-in-the-Loop 任务推进和评审决策。
- Release Flow -> Request -> Task 追踪模型。
- 基于本地 Access Grant 的范围化访问治理。
- 带 user、action、scope、agent、correlation context 的审计日志。
- 面向执行目标和团队配置的 Configuration Management。
- 用模板和 Excel 导入建立可重复工作流。
- SDD 文档链，让需求、故事、规格、架构、设计和任务对人和 AI 协作者都清晰。

## Deployment Function

Deployment Agent 是这个仓库中目前最具体的 Agent。它位于 M6 Deployment，用来把已经通过 Build/Testing 验证的输出，转成受控的 SIT / UAT / PROD 发布工作。

Deployment Agent 包括：

- 基于 Excel 的 deployment request 导入。
- 面向 `SIT`、`UAT`、`PROD` 的阶段化 Release Flow 跟踪。
- 手动任务执行和 AUTO 提交路径。
- Jenkins 与 Ansible/AWX 执行适配器。
- 人工评审决策：approve、reject、rerun、skip。
- 执行历史、外部 job/log 链接和审计记录。
- 访问治理和发布安全控制。

作为第二个参赛项目，这个 Agent 可以独立呈现为 **Atlas Engineering Delivery Hub - Deployment**。它的差异化重点是 IBM iSeries one-click release UTL 的方向：Hub 提供受控发布框架，Deployment Agent 则沉淀任务模型、证据、人工评审门和适配器设计，让 iSeries 发布活动能够被包装成可重复的一键式运行流程。

![Deployment tool workflow](docs/assets/atlas-deployment-tool-workflow.svg)

## 当前范围和边界

这个 package 声称具备：

- 共享交付工作流的可运行实现基线。
- 可以承载多个 SDLC function 的 framework 叙事。
- 带完整 SDD 追踪链和样例输出的 Deployment function。
- Framework 和 Deployment function 的中英文评审入口。

兼容性说明：

- 可见产品品牌：Atlas Engineering Delivery Hub / Atlas Hub。
- Agent 名称：Deployment Agent。
- 目前保留的兼容技术标识：`/wwa/*`、`/wwa/deployment-agent`、`/api/deployment-agent`、Maven `artifactId=agenthub`、Java package `com.wwa.agenthub`。

这个 package 不声称具备：

- 完全自动化的交付审批。
- Seven Mountains 每个阶段的完整生产落地。
- 开放包里的真实企业 Team Book 生产集成。
- 一键基础设施回滚。
- 真实凭证、客户数据、kubeconfig、内部截图或真实生产环境名称。

## 快速开始

后端：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

前端：

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173/wwa/deployment-agent
```

本地/测试 stub 用户见 [当前实现基线](docs/wwa-agent-workspace-hub-current-baseline.md)。在 local 模式下，stub provider 接受任意非空密码。

## 文档地图

Framework 入口：

- [Framework 文档索引](docs/atlas-engineering-delivery-hub-index.md)
- [Framework 开放协作提交材料](docs/open-collaboration-submission.md)
- [中文 Framework 提交材料](docs/open-collaboration-submission.zh-CN.md)
- [Framework Pitch](docs/atlas-engineering-delivery-hub-pitch.md)
- [Framework adoption sample](docs/samples/atlas-framework-adoption-sample.md)
- [Framework SDD 追踪链](docs/00-context/atlas-engineering-delivery-hub-traceability.md)

Deployment function 入口：

- [Deployment 文档索引](docs/atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment 开放协作提交材料](docs/open-collaboration-submission-deployment.md)
- [中文 Deployment 提交材料](docs/open-collaboration-submission-deployment.zh-CN.md)
- [Deployment Pitch](docs/atlas-engineering-delivery-hub-deployment-pitch.md)
- [Deployment 样例包](docs/samples/atlas-deployment-tool-mini-output/README.md)
- [Deployment SDD 追踪链](docs/00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

运行时与贡献参考：

- [贡献指南](CONTRIBUTING.md)
- [当前实现基线](docs/wwa-agent-workspace-hub-current-baseline.md)
- [平台与 Deployment 架构](docs/04-architecture/architecture.md)
- [详细设计基线](docs/05-design/design.md)
- [SDD Profile](docs/00-context/sdd-profile.md)

## 路线图

- 为 Discovery、Build、Testing、Deployment、Maintenance 明确 function-level 项目包装方式。
- 在不暴露真实环境细节的前提下，扩展 IBM iSeries one-click release UTL 设计。
- 明确 Build、Testing 与 Deployment 之间的上游证据交接契约。
- 强化 stage gate、task manifest、evidence capture 和 review decision 的可复用模板。
- 持续增强 SDD、Markdown、Mermaid 和文档验证自动化。

## 验证

推荐的文档/包装检查：

```bash
git diff --check
node scripts/check-markdown-links.mjs
```

涉及代码变更时的运行时检查：

```bash
mvn test
cd frontend && npm run build
```
