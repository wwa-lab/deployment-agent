# Raw Requirements / 原始需求文档

## Requirement Sources / 需求来源
Where are the requirements coming from? / 这些需求来自哪里？

- User interviews / 用户访谈：
  - Requirements come from ongoing discussions with the project sponsor and core users around the WWA operating model and the first agent workspace, Deployment Agent.
  - 需求来自与项目发起方及核心用户围绕 WWA 工作模式和首个 Agent 工作台 Deployment Agent 的持续讨论。

- Business goals / 业务目标：
  - Establish WWA (Work With Agent) as a reusable platform layer, with Deployment Agent as the first implemented workspace.
  - 将 WWA（Work With Agent）建设为可复用的平台层，并以 Deployment Agent 作为首个落地工作台。
  - Improve deployment execution visibility, control, and traceability through a structured human-in-the-loop workflow.
  - 通过结构化的人在回路（human-in-the-loop）流程，提升部署执行的可视性、可控性和可追溯性。
  - Enable later reuse of shared capabilities such as Template Management, Configuration Management, and Audit Log.
  - 为后续复用共享能力打基础，包括模板管理、配置管理和审计日志。

- Existing workflow issues / 现有流程问题：
  - Deployment requests are currently initiated through Excel and external orchestration tools, but the workflow lacks a unified execution workspace.
  - 当前部署请求通过 Excel 和外部编排工具发起，但缺少统一的执行工作台。
  - Users cannot easily track release progress across SIT / UAT / PROD in one consolidated view.
  - 用户无法在一个统一视图中便捷跟踪 SIT / UAT / PROD 的发布进展。
  - Task results may be produced by systems such as Jenkins or Ansible, but review and go/no-go decisions are not standardized in one interface.
  - 任务结果可能由 Jenkins、Ansible 等系统产生，但结果审阅与继续/中止决策尚未在统一界面中标准化。
  - Some key runtime configuration values are integration-specific and should not be hardcoded.
  - 某些关键运行配置与集成端点相关，不应写死在代码中。

- Competitive references / 竞品与参考：
  - The requirement references a platform-style agent model where a shared shell supports multiple future agent workspaces, each with its own task logic but shared governance capabilities.
  - 需求参考了一种平台型 Agent 模式：共享平台外壳支持多个未来 Agent 工作台，各自拥有独立任务逻辑，同时复用统一治理能力。

- Personal judgment / 主观判断：
  - This MVP should prioritize operational clarity and controlled progression over autonomous decision-making.
  - 本次 MVP 应优先关注流程清晰和受控推进，而不是自动化决策。
  - The first version should focus on making deployment flow visible, reviewable, and auditable before expanding into more advanced automation.
  - 第一版应先让部署流程可见、可审、可追踪，再逐步扩展到更高级自动化。

## Problem Statements / 问题陈述
What problems need to be solved? / 需要解决哪些问题？

- There is no unified workspace to onboard deployment requests, track release flows, inspect task results, and make controlled decisions before moving to the next step.
- 当前缺少一个统一工作台，用于接收部署请求、跟踪发布流程、查看任务结果，并在进入下一步前进行受控决策。

- Deployment progress across SIT / UAT / PROD is difficult to monitor consistently at the release-flow level rather than only at the individual request level.
- 部署进度难以在 Release Flow 层面统一监控，现状更容易停留在单个请求层面，而非整条发布链路层面。

- Execution outputs may exist, but there is no standardized human review mechanism that prevents premature progression after task execution.
- 虽然执行结果可能已经产生，但缺少标准化的人工审核机制，无法避免任务执行后未经确认就过早推进流程。

- Operational traceability is insufficient: important actions such as upload, edit, approval, rejection, rerun, and skip need to be logged clearly.
- 操作可追溯性不足：上传、编辑、批准、拒绝、重跑、跳过等关键动作需要被清晰记录。

- Shared capabilities needed by future agents are not yet abstracted at the platform level.
- 未来多个 Agent 共同需要的共享能力尚未在平台层抽象出来。

## User Needs / 用户需求
What do users actually need? / 用户真正需要什么？

- Developers need to upload deployment requests and trigger a controlled release flow using the existing Excel template.
- Developer 需要基于现有 Excel 模板上传部署请求，并触发一个受控的 Release Flow。

- TLs need to review execution and verification results, then make explicit human decisions before the release continues.
- TL 需要查看执行与验证结果，并在流程继续前进行明确的人工决策。

- DevOps Admins need to maintain key integration and runtime configurations from UI rather than code changes.
- DevOps Admin 需要在 UI 中维护关键集成配置和运行时配置，而不是通过改代码处理。

- Audit teams or management need to inspect key actions and process traces through auditable records.
- 审计部门或管理层需要通过可审计记录查看关键动作和流程轨迹。

- Users need a single Deployment Agent workspace inside WWA to manage deployment activities in a structured way.
- 用户需要在 WWA 内拥有一个统一的 Deployment Agent 工作台，以结构化方式管理部署活动。

- Users need a summary view that shows each release flow and its stage progress across SIT / UAT / PROD with simple status values such as Done, Running, and Pending. :contentReference[oaicite:4]{index=4}
- 用户需要一个汇总视图，展示每条 Release Flow 在 SIT / UAT / PROD 各阶段的状态，并以 Done、Running、Pending 等简洁状态表达。:contentReference[oaicite:5]{index=5}

- Users need task-level visibility, including task name, result summary, timing, status, and available actions. :contentReference[oaicite:6]{index=6}
- 用户需要任务级可视化能力，包括任务名称、结果摘要、时间、状态和可操作动作。:contentReference[oaicite:7]{index=7}

- The MVP must ensure the core workflow can run through successfully from request to process to verification to decision.
- MVP 必须保证核心主链路能够正常跑通：request → process → verification → decision。

## Initial Functional Ideas / 初步功能设想
What capabilities are currently being considered? / 当前考虑中的能力有哪些？

- Add WWA as a level-1 menu and Deployment Agent as a level-2 workspace entry.
- 增加 WWA 一级菜单，并将 Deployment Agent 作为二级工作台入口。

- Reserve shared menu entries under WWA for:
  - Template Management
  - Configuration Management
  - Audit Log
- 在 WWA 下预留共享菜单入口：
  - 模板管理
  - 配置管理
  - 审计日志

- Build the Deployment Agent main page with the following sections:
  - page introduction area
  - filter area
  - Deployment Flow Summary
  - Selected Release Flow Details
  - Task Details
  - Upload Excel entry and dialog
- 构建 Deployment Agent 主页面，包含以下区域：
  - 页面介绍区
  - 筛选区
  - Deployment Flow Summary
  - Selected Release Flow Details
  - Task Details
  - Upload Excel 入口及弹窗

- Support Excel-based request onboarding, including:
  - Upload Excel
  - Download Template
  - View Sample
  - upload success message
  - View Import Log entry
- 支持基于 Excel 的请求接入，包括：
  - 上传 Excel
  - 下载模板
  - 查看示例
  - 上传成功提示
  - 查看导入日志入口

- Represent the main business object as a Release Flow that groups multiple stage requests under the same journey.
- 以 Release Flow 作为核心业务对象，用于把同一发布链路下的多个阶段请求归组管理。

- Show task-level actions:
  - Edit
  - View Result
  - Decision dropdown with Approve / Reject / Rerun / Skip
- 展示任务级操作：
  - Edit
  - View Result
  - Decision 下拉操作：Approve / Reject / Rerun / Skip

- Define the decision effects in MVP:
  - Approve: continue the release flow normally
  - Reject: stop the current release flow
  - Rerun: rerun the current step
  - Skip: skip the current step and continue
- 在 MVP 中明确决策动作效果：
  - Approve：Release 流程正常继续
  - Reject：停止当前 Release Flow
  - Rerun：重新执行当前 step
  - Skip：跳过当前 step 并继续

- Maintain key integration configuration values in UI, such as Jenkins URL and Ansible URL. :contentReference[oaicite:8]{index=8}
- 在 UI 中维护关键集成配置，例如 Jenkins URL 和 Ansible URL。:contentReference[oaicite:9]{index=9}

- Record a basic audit log for key actions such as upload_excel, create_request, edit_task_input, view_result, approve_task, reject_task, rerun_task, and skip_task. :contentReference[oaicite:10]{index=10}
- 为关键动作记录基础审计日志，例如 upload_excel、create_request、edit_task_input、view_result、approve_task、reject_task、rerun_task、skip_task。:contentReference[oaicite:11]{index=11}

## Initial Non-Functional Expectations / 初步非功能期望
What are the expectations around performance, maintainability, reliability, security, etc.? / 对性能、可维护性、可靠性、安全性等有哪些预期？

- Maintainability / 可维护性：
  - WWA should be designed as a reusable platform layer, not as a one-off page. :contentReference[oaicite:12]{index=12}
  - WWA 应被设计为可复用的平台层，而不是一次性页面。:contentReference[oaicite:13]{index=13}
  - Shared capabilities should be separated from Deployment-Agent-specific logic.
  - 共享能力应与 Deployment Agent 专属逻辑分离。

- Reliability / 可靠性：
  - The system must not auto-progress after execution; human review is required before moving forward. :contentReference[oaicite:14]{index=14}
  - 系统在执行完成后不得自动推进；进入下一步前必须经过人工审核。:contentReference[oaicite:15]{index=15}
  - The MVP should primarily guarantee that the core workflow can complete end-to-end from request to process to verification to decision.
  - MVP 的首要目标是保证主链路从 request 到 process 到 verification 再到 decision 能够端到端跑通。

- Explainability / 可解释性：
  - Users should be able to inspect result summaries and explicitly decide the next action.
  - 用户应能查看结果摘要，并显式决定下一步动作。
  - The workflow should make the current review state and responsible owner visible.
  - 流程应清晰展示当前审核状态和责任人。

- Security and control / 安全与控制：
  - Deployment Agent MVP is not intended to be fully autonomous. :contentReference[oaicite:16]{index=16}
  - Deployment Agent 的 MVP 并非完全自主 Agent。:contentReference[oaicite:17]{index=17}
  - Human-in-the-loop decision control is a core control mechanism, not an optional enhancement.
  - 人在回路的决策控制是核心控制机制，而不是可选增强项。

- Traceability / 可追溯性：
  - Key operator actions should be logged with who, what, input, output, and timestamp. :contentReference[oaicite:18]{index=18}
  - 关键操作应记录执行人、动作、输入、输出和时间戳。:contentReference[oaicite:19]{index=19}

## Open Questions / 开放问题
What is still unclear? / 目前还有哪些不明确之处？

- What exact fields are included in the Day-1 fixed Excel template, and which are mandatory for onboarding?
- Day 1 固定 Excel 模板中具体包含哪些字段，哪些字段是接入时必填？

- Is TL review always performed at the task level, or can it also happen at the request or stage level?
- TL 的 review 是否始终发生在 task 层，还是也可能发生在 request 或 stage 层？

- After Reject stops the current release, what exact status should be displayed for the Release Flow and current Request?
- Reject 停止当前 Release 后，Release Flow 和当前 Request 应显示什么精确状态？

- After Rerun, should the system preserve the previous execution record and append a new run history?
- 执行 Rerun 后，系统是否需要保留之前的执行记录，并追加新的运行历史？

- After Skip, should the next step become immediately available, or should additional confirmation still be required?
- 执行 Skip 后，下一个 step 是否应立即可用，还是仍需额外确认？

- What exact content should be shown in View Result: summary only, raw logs, parsed output, or all of them?
- `View Result` 应展示什么内容：仅摘要、原始日志、解析结果，还是全部？

- How should audit logs be surfaced before a full query page is implemented?
- 在完整审计查询页尚未实现前，审计日志应如何被呈现？

## Assumptions / 当前假设
What assumptions are currently being made? / 当前正在基于哪些假设推进？

- WWA is a platform layer that will host multiple future agent workspaces, and Deployment Agent is the first one.
- WWA 是一个未来会承载多个 Agent 工作台的平台层，Deployment Agent 是第一个落地工作台。

- The MVP is for FinBlock and is not intended to launch multiple agents simultaneously. :contentReference[oaicite:20]{index=20}
- 本次 MVP 面向 FinBlock，且不考虑多个 Agent 同时上线。:contentReference[oaicite:21]{index=21}

- The MVP is a controlled execution workspace, not a fully autonomous deployment agent. :contentReference[oaicite:22]{index=22}
- 本次 MVP 是受控执行工作台，而不是完全自主的部署 Agent。:contentReference[oaicite:23]{index=23}

- Primary actor assumptions for the current workflow are:
  - Developer uploads requests
  - TL performs review and decision
  - DevOps Admin maintains configuration
  - Audit team or management views audit records
- 当前流程中的主要角色假设为：
  - Developer 上传请求
  - TL 执行 review 和 decision
  - DevOps Admin 维护配置
  - Audit 部门或管理层查看审计记录

- Release Flow is the top-level business entity, with Request and Task as lower-level entities. :contentReference[oaicite:24]{index=24}
- Release Flow 是顶层业务实体，Request 和 Task 为其下层实体。:contentReference[oaicite:25]{index=25}

- Stage values of interest are SIT, UAT, and PROD.
- 当前关注的阶段值为 SIT、UAT 和 PROD。

- Day 1 will use the existing fixed Excel template rather than a dynamic template-definition system. :contentReference[oaicite:26]{index=26}
- Day 1 将使用现有固定 Excel 模板，而不是动态模板定义系统。:contentReference[oaicite:27]{index=27}

## Notes / 备注
- The current prototype and requirement both support a human-in-the-loop task lifecycle where execution result review is followed by explicit decision control. :contentReference[oaicite:28]{index=28} :contentReference[oaicite:29]{index=29}
- 当前原型和需求文档都支持一种人在回路的任务生命周期：先审查执行结果，再进行显式决策控制。:contentReference[oaicite:30]{index=30} :contentReference[oaicite:31]{index=31}

- The main MVP success target at this stage is not completeness of all surrounding capabilities, but successful completion of the core request → process → verification → decision flow.
- 当前阶段的 MVP 成功目标不是补齐所有外围能力，而是确保 request → process → verification → decision 的核心链路能够成功跑通。

- This raw requirements file is now suitable to be used as the direct input for req-to-user-story.
- 这份 raw requirements 现在已经适合作为 req-to-user-story 的直接输入。