# Atlas Engineering Delivery Hub - Deployment

**参赛类别：** Tool
**生命周期阶段：** M6 Deployment
**English README:** [README.md](README.md)

Atlas Engineering Delivery Hub - Deployment 是 Atlas Engineering Delivery Hub / Seven Mountains SDLC 中 **M6 Deployment** 阶段的 Tool。它帮助团队把已经通过构建和测试验证的交付输出，转换成受控、可追踪、可重复的发布运行流程。

本仓库承载的是当前 WWA Agent Workspace Hub 中的发布编排实现基线。它属于 Atlas Engineering Delivery Hub 叙事中的一个阶段能力，不代表整个 Atlas Engineering Delivery Hub 框架本身。

![M6 Deployment lifecycle positioning](docs/assets/atlas-deployment-lifecycle-positioning.svg)

## 生命周期定位

Seven Mountains SDLC：

```text
M1 Planning -> M2 Estimation -> M3 Discovery -> M4 Build -> M5 Testing -> M6 Deployment -> M7 Maintenance
```

本仓库聚焦 **M6 Deployment**。它接收上游 M4 Build 与 M5 Testing 形成的构建产物、测试证据和发布上下文，并把这些输入组织成 SIT / UAT / PROD 发布流程、人工评审、审计记录、权限边界和可回溯的任务历史。

| 阶段 | 在 Atlas 叙事中的角色 | 与本仓库的关系 |
|---|---|---|
| M3 Discovery | 将业务意图转化为需求和设计证据；Atlas Phoenix Lens / Legacy Spec Factory 是上游示例能力。 | 上游能力，本仓库不实现。 |
| M4 Build | 产出构建任务、构建产物和交付证据。 | 上游证据来源；当前平台基线中也包含 Build Agent 工作区。 |
| M5 Testing | 产出验证证据、缺陷反馈和验收结论。 | 进入发布前的直接上游门。 |
| M6 Deployment | 对发布任务进行编排、执行、评审、追踪和审计。 | 本参赛项目的主定位。 |
| M7 Maintenance | 将生产反馈、事件和改进重新带回生命周期。 | 下游反馈目标；维护自动化仍为计划/TBD。 |

## 当前交付范围

当前已经具备：

- 基于 Spring Boot 与 Vue 3 的受控发布工作区。
- `/wwa/deployment-agent` 下的 Deployment Agent 页面。
- `/api/deployment-agent/*` 下的发布相关 API。
- 基于固定 `AMH_HCC_task` 工作表的 Excel 发布请求导入。
- 面向 `SIT`、`UAT`、`PROD` 的阶段化 Release Flow 跟踪。
- Request 与 Task 生命周期管理，包括手动运行、自动提交、结果记录、人工决策、重跑、跳过、失败标记、归档、恢复和清除。
- 进入下一步前的 Human-in-the-Loop 决策门。
- 任务执行历史，包括重跑尝试、外部任务链接和日志链接。
- 面向 Jenkins 与 Ansible/AWX 的 AUTO 提交适配器，端点和凭证由配置驱动。
- 可选的外部执行轮询能力；当前配置默认关闭。
- 基于 Access Grant 的本地访问治理。
- 带 Release Flow、Request、Task、Application、SNOW Group、Agent 和 Correlation ID 上下文的审计日志。
- 共享平台服务：认证、访问管理、配置管理、审计、模板下载和复用型工作流 UI。

当前不声称已经完成的能力：

- 不提供全自动发布审批；当前基线坚持人工决策。
- 真正的企业 Team Book 生产集成仍是未来工作；本地和测试流程使用抽象 provider 与 stub 用户。
- 模板创建和模板存储仍有部分前端本地草稿形态；从模板创建 rundown 已经有后端能力。
- AUTO 执行回调式完成回写不是当前主模型。
- Maintenance 阶段的事件路由、生产反馈闭环和长期运维自动化仍为计划/TBD。
- 本开放协作包不包含内部截图、客户数据、kubeconfig、真实凭证或真实生产环境名称。

## 核心能力

| 能力 | 说明 | 仓库中的证据 |
|---|---|---|
| 发布导入 | 接收 Excel、显式 Stage、发布标识和可选运行时范围。 | Upload Controller、Import Service、模板下载、解析测试。 |
| Release Flow 跟踪 | 将发布工作组织为 Release Flow -> Request -> Task，并覆盖 SIT / UAT / PROD。 | Release Flow 领域服务、Controller、前端 summary/detail 页面。 |
| 人工评审门 | 执行后不会自动静默推进，需要 Approve / Reject / Rerun / Skip。 | Decision Engine、Task State Machine、Progression Service、决策弹窗。 |
| 手动执行 | Owner 或 DevOps Admin 可以启动手动任务并记录结果。 | Task Service、Record Result Service、Execution History。 |
| AUTO 执行 | 将 AUTO 任务提交到已配置的 Jenkins 或 Ansible/AWX。 | Auto Execution Service、Execution Target Resolver、执行适配器。 |
| 发布安全控制 | 支持归档/恢复/清除、失败标记、重跑尝试、状态重算和审计。 | Release Flow Service、Audit Logger、Request/Task 状态测试。 |
| 范围化治理 | 通过 Access Grant、角色、权限和 `Application + SNOW Group` 范围控制访问。 | Access Grant、Auth Service、安全过滤器、Access Management UI。 |
| 可追踪性 | 保存审计、执行历史、导入元数据、SDD 文档和样例包。 | 文档、迁移、测试、审计表和执行历史表。 |

## 输入与输出

主要输入：

- 来自 M4 Build 和 M5 Testing 的发布意图、构建产物和验证证据。
- 基于下载模板准备的 Excel 上传文件。
- 上传时显式选择的阶段：`SIT`、`UAT` 或 `PROD`。
- 可选 Workflow Identifier / Release ID，用于关联阶段尝试。
- 可选 `Application`、`SNOW Group` 和 Agent 上下文。
- Task 的执行类型：`MANUAL` 或 `AUTO`。
- Task Owner 或 DevOps Admin 提交的人工决策与结果说明。
- 任务输入中保存的 Jenkins / Ansible 目标元数据。

主要输出：

- 带阶段状态和当前阶段的 Release Flow 汇总。
- 带 rundown owner、运行范围和归档状态的阶段 Request。
- 有序 Task 列表，包括状态、输入参数、期望输出、结果摘要和执行历史。
- AUTO 提交后的外部 job URL 与 log URL。
- 上传、编辑、执行、决策、访问变更和生命周期操作的审计记录。
- 面向未来通知分发的 outbox 事件记录。
- 面向评审和贡献者的 SDD 与开放协作材料。

## 发布工作流

![Deployment tool internal workflow](docs/assets/atlas-deployment-tool-workflow.svg)

1. 发布操作者上传任务工作簿，或从模板创建 rundown。
2. 用户必须显式选择 `SIT`、`UAT` 或 `PROD`；Stage 不从表格中盲目信任。
3. 后端校验并导入 Release Flow、Request 和 Task。
4. 第一个符合条件的任务进入可运行状态。
5. Task Owner 或 DevOps Admin 启动手动任务，或提交 AUTO 任务。
6. 手动任务由用户记录结果；AUTO 任务保存外部执行元数据，并可在开启监控后轮询。
7. 人工评审决定 Approve、Reject、Rerun 或 Skip。
8. Progression 逻辑推进下一任务，或推进/完成 Release Flow。
9. 审计和执行历史保留谁、何时、在什么发布上下文中做了什么。
10. 失败、拒绝、归档或需要回滚的工作通过状态和历史保留，不会被静默覆盖。

## 上下游关系

![M4/M5 to M6 to M7 relationship](docs/assets/atlas-deployment-upstream-downstream.svg)

Deployment Tool 位于构建和测试门之后：

- **Input：** 构建产物引用、任务清单、测试证据、发布范围、Owner 和审批上下文。
- **Execute：** 在 SIT / UAT / PROD 中执行阶段化 release rundown，支持手动与 AUTO 任务。
- **Output：** 发布记录、任务结果、外部执行链接、审批、审计历史和失败/回滚状态。
- **Validate：** 人工评审、可追踪决策、状态重算、审计检查，以及进入 M7 Maintenance 的生产反馈。

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

## 示例发布故事

一个脱敏演示路径：

1. M4 Build 产出候选包和构建证据。
2. M5 Testing 记录验证证据和验收说明。
3. 发布操作者打开 Deployment Agent，并上传 `SIT` 阶段的脱敏任务工作簿。
4. 工具创建或更新 Release Flow，并释放第一个发布任务。
5. Task Owner 执行手动步骤，或把 AUTO 任务提交到已配置的 Jenkins/Ansible 目标。
6. Reviewer 检查结果与期望输出后逐项批准。
7. 后续 `UAT` 与 `PROD` 上传复用同一个 workflow identifier，让阶段尝试保持关联。
8. 如果任务失败，团队记录失败、重跑或拒绝，并保留审计和执行历史。
9. PROD 批准后，发布记录成为进入 Maintenance 阶段的运维证据。

脱敏样例包见 [docs/samples/atlas-deployment-tool-mini-output](docs/samples/atlas-deployment-tool-mini-output/README.md)。

## 目录概览

| 路径 | 作用 |
|---|---|
| `src/main/java/com/wwa/agenthub/agents/deployment/` | Deployment Agent 阶段定义与 REST Controller。 |
| `src/main/java/com/wwa/agenthub/domain/` | Release Flow、Task、Decision、Execution、Audit、Auth、Configuration、Import 等共享领域服务。 |
| `src/main/java/com/wwa/agenthub/platform/` | 所有 Agent 共享的平台契约和安全边界。 |
| `src/main/resources/db/migration/` | Oracle 数据库迁移历史。 |
| `frontend/src/agents/deployment/` | Deployment 工作区入口、API、summary 与 detail 页面。 |
| `frontend/src/platform/` | 前端共享的 release-flow workspace factory 和平台组件。 |
| `docs/` | SDD、架构/设计、参赛材料、图表和样例。 |
| `scripts/check-markdown-links.mjs` | Markdown 相对链接检查脚本。 |

## 关键文档

- [Deployment Tool 文档索引](docs/atlas-engineering-delivery-hub-deployment-index.md)
- [英文开放协作提交材料](docs/open-collaboration-submission.md)
- [中文开放协作提交材料](docs/open-collaboration-submission.zh-CN.md)
- [Deployment Pitch](docs/atlas-engineering-delivery-hub-deployment-pitch.md)
- [贡献指南](CONTRIBUTING.md)
- [当前实现基线](docs/wwa-agent-workspace-hub-current-baseline.md)
- [Deployment Agent 需求基线](docs/01-requirements/requirement.md)
- [Deployment Agent 规格基线](docs/03-spec/spec.md)
- [平台与 Deployment 架构](docs/04-architecture/architecture.md)
- [详细设计基线](docs/05-design/design.md)
- [M6 包装追踪链](docs/00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

## 审批、追踪、回滚与人工评审

- **审批：** 当前任务推进必须经过人工决策。MVP 决策门明确是手动模式。
- **追踪：** Release Flow、Request、Task、执行历史、审计日志、Correlation ID 和 SDD 文档构成追踪链。
- **回滚/恢复姿态：** 工具不声称提供一键基础设施回滚。它提供失败标记、重跑、拒绝、归档/恢复、清除和历史保留，帮助团队执行已有的回滚或修复流程。
- **验证：** 上传校验、状态机、角色校验、边界保护和测试共同保护工作流。发布决策仍然需要人工评审。
- **凭证安全：** 凭证应进入配置/密钥管理，不应进入文档、样例、截图或提交的工作簿。

## 路线图

- 明确 Build 与 Testing 阶段向 Deployment 传递证据的契约。
- 完成后端持久化的模板管理能力。
- 在环境验证后扩展 AUTO 执行监控和回调回写。
- 基于现有 outbox seam 增加通知分发。
- 增加 Maintenance 阶段的事件、回滚和发布后复盘模板。
- 仅在明确评审通过后加入脱敏截图。
- 持续增强 SDD 和文档验证自动化。

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
