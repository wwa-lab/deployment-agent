# Atlas Engineering Delivery Hub

**参赛类别：** Framework

Atlas Engineering Delivery Hub 是一个端到端 SDLC 交付框架，用 Seven Mountains SDLC 和 Seven Gates Flow 组织规划、发现、构建、测试、部署和维护工作。

本仓库是内部开放协作竞赛的框架类参赛入口。当前代码基线是 WWA Agent Workspace Hub：一个基于 Spring Boot 和 Vue 的平台，提供共享治理能力，并承载 Build、Testing、Deployment 等阶段型 Agent 工作区。

![Atlas Engineering Delivery Hub lifecycle](docs/assets/atlas-framework-lifecycle.svg)

## 框架定位

Atlas Engineering Delivery Hub 不是单一发布工具，而是一个可复用的工程交付框架，帮助团队获得：

- 覆盖完整 SDLC 的生命周期可视化；
- 通过阶段门和 Human-in-the-Loop 决策实现流程控制；
- 通过证据、评审和验证检查实现质量保障；
- 通过审计、权限范围、任务历史和 SDD 文档实现可追溯交付；
- 可接入人员、AI Agent、自动化和外部系统的持续交付运行能力。

当前实现通过 WWA Agent Workspace Hub 及其 Agent Module 模式承载。未来新的阶段能力可以接入同一生命周期和门控模型，而不需要替换父框架。

## Seven Mountains SDLC

框架把 SDLC 建模为七座山，每一座山都有明确职责，并在进入下游前通过对应阶段门。

| Mountain | 目标 | 当前范围 |
|---|---|---|
| Planning | 明确项目目标、审批准备、范围和边界。 | 已在 SDLC 覆盖模型中表达。 |
| Estimation | 建立进度、成本、资源和风险基线。 | 已在 SDLC 覆盖模型中表达。 |
| Discovery | 把业务意图转化为需求和可执行设计。 | 目标阶段；Atlas Phoenix Lens 可作为其中一个示例能力接入。 |
| Build | 产出代码变更、本地验证和构建产物。 | 通过 Build Agent 实现。 |
| Testing | 产出行为、质量、缺陷和验收验证证据。 | 通过 Testing Agent 建设中。 |
| Deployment | 协调 SIT、UAT、PROD 发布和上线验收。 | 通过 Deployment Agent 实现。 |
| Maintenance | 将生产反馈、事件和改进重新路由回 SDLC。 | 目标阶段。 |

![Seven Mountains SDLC](docs/assets/seven-mountains-sdlc.svg)

## Seven Gates Flow 与 I-E-O-V

每个 Mountain 都遵循同一套门控模型：

| Gate 元素 | 含义 |
|---|---|
| Input | 必要输入、范围、负责人、约束和前置条件。 |
| Execute | 由人员、Agent、自动化或外部工具执行的受控工作。 |
| Output | 可持久化的产物、决策、运行记录和结果。 |
| Validate | 评审检查、审批、测试证据和审计记录。 |

当前实现通过任务状态、执行历史、决策门、审计记录、范围化访问控制和 SDD 文档追踪来表达这些门控。

![Seven Gates I-E-O-V flow](docs/assets/seven-gates-ieov.svg)

## 框架能力

- **生命周期地图：** 通过 Agent Contribute Dashboard 展示七阶段 SDLC 覆盖和负责人。
- **平台核心：** 共享认证、访问治理、审计、配置管理、上传、任务流转和 Release Flow 服务。
- **Agent 模块：** 面向 Build、Testing、Deployment 以及未来能力的阶段工作区。
- **Human-in-the-Loop 控制：** 明确的执行、结果记录、评审、重跑、跳过和批准决策。
- **可追溯性：** SDD 文档、审计日志、任务历史、Release Flow 状态、负责人和贡献元数据。
- **AI 友好：** 结构化文档、稳定阶段契约、显式门控和样例模板，便于 AI Agent 与工程团队协作。

## 当前范围

当前已具备：

- WWA Agent Workspace Hub 平台壳；
- DEV 阶段 Build Agent；
- SIT / UAT / PROD 发布编排 Deployment Agent；
- UAT 测试工作流 Testing Agent 基线；
- Seven Mountains SDLC 可视化的 Agent Contribute Dashboard；
- 访问管理、审计、配置、模板下载、上传和任务生命周期服务。

尚未作为运行时能力实现：

- Discovery 阶段生产能力；
- Maintenance 阶段生产能力；
- 完整外部通知分发器；
- 面向开放协作包的真实客户截图或内部敏感材料。

详细当前实现说明已保存在 [WWA Agent Workspace Hub Current Baseline](docs/wwa-agent-workspace-hub-current-baseline.md)。

## 架构概览

当前架构包含四层：

1. **生命周期模型：** Seven Mountains SDLC 和 Seven Gates / I-E-O-V。
2. **治理模型：** Human-in-the-Loop 决策、访问范围、审计、验证证据和 SDD 追踪。
3. **Platform Core：** `/api/platform/*` 与 WWA 共享控制面下的 Spring Boot 服务和 Vue 平台页面。
4. **Agent Modules：** Build Agent、Testing Agent、Deployment Agent 以及未来阶段能力。

更多细节请看 [System Architecture](docs/04-architecture/architecture.md) 和 [Atlas Engineering Delivery Hub Packaging Architecture](docs/04-architecture/atlas-engineering-delivery-hub-architecture.md)。

## 演示路径

一个合成演示场景：

1. 团队在 Planning 和 Estimation 中记录目标、负责人、范围和风险边界。
2. Discovery 能力，例如 Atlas Phoenix Lens，将原始业务输入转化为需求与设计证据。
3. Build Agent 将批准后的设计转化为可追踪的 DEV 任务与构建产物。
4. Testing Agent 跟踪验证证据和缺陷反馈。
5. Deployment Agent 通过任务级评审决策协调 SIT、UAT、PROD 发布。
6. Maintenance 将生产反馈重新带回生命周期。

本地运行当前应用基线：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173/wwa/agent-contribute-dashboard` 查看 Seven Mountains 覆盖看板。

## 与子能力的关系

Atlas Phoenix Lens 是 Discovery 阶段子能力示例。它可以支持需求和发现智能，但不是本仓库的父级框架。Atlas Engineering Delivery Hub 才是定义生命周期、门控、平台治理、采用路径和贡献模型的上层交付框架。

## 路线图

- 用已审批的协作页面替换占位内部指南链接。
- 通过独立 SDD 切片扩展 Discovery 和 Maintenance 运行时能力。
- 增加团队入门、门控证据和 Agent 模块贡献模板。
- 增强文档、图表、SDD 追踪和样例包的验证自动化。
- 仅在明确评审和批准后加入已脱敏截图或宣传图。

## 文档入口

- [框架文档索引](docs/atlas-engineering-delivery-hub-index.md)
- [英文开放协作提交材料](docs/open-collaboration-submission.md)
- [中文开放协作提交材料](docs/open-collaboration-submission.zh-CN.md)
- [框架 Pitch](docs/atlas-engineering-delivery-hub-pitch.md)
- [贡献指南](CONTRIBUTING.md)
- [合成采用样例](docs/samples/atlas-framework-adoption-sample.md)
- [SDD 追踪](docs/00-context/atlas-engineering-delivery-hub-traceability.md)
- [当前实现基线](docs/wwa-agent-workspace-hub-current-baseline.md)

