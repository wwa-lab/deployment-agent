# Requirement Document / 需求文档

## Overview / 概述

This document defines the MVP user stories for Deployment Agent under the WWA platform.
The MVP focuses on enabling a controlled, human-in-the-loop deployment workflow that allows users to upload requests, monitor release progress, inspect execution results, make explicit decisions, maintain key configuration, and review audit records.

本文档定义了 WWA 平台下 Deployment Agent 的 MVP 用户故事。
MVP 聚焦于构建一个受控、人在回路（human-in-the-loop）的部署工作流，使用户能够上传请求、监控发布进度、查看执行结果、做出明确决策、维护关键配置并查看审计记录。

The main MVP objective is:
**Ensure the core workflow can successfully run through request → process → verification → decision.**

MVP 的核心目标是：
**确保主链路能够顺利完成 request → process → verification → decision。**

---

## Data Model Hierarchy / 数据模型层级

A Release Flow contains one or more Requests.
Each Request contains one or more Tasks.
Task operations such as View Result, Edit, Approve, Reject, Rerun, and Skip occur at the Task level within the selected Request context.

一个 Release Flow 包含一个或多个 Request。
每个 Request 包含一个或多个 Task。
查看结果、编辑、批准、拒绝、重跑、跳过等操作均发生在选中 Request 上下文中的 Task 层级。

---

# User Stories / 用户故事

---

## User Story 1 / 用户故事 1

**Title / 标题**
Access Deployment Agent workspace within WWA platform navigation / 在 WWA 平台导航中访问 Deployment Agent 工作台

**Story / 故事**
As a Developer, TL, DevOps Admin, or Audit/Management user,
I want to access the Deployment Agent workspace from the WWA platform menu,
so that I can use a unified workspace for deployment-related activities.

作为 Developer、TL、DevOps Admin 或 Audit/Management 用户，
我希望能够从 WWA 平台菜单进入 Deployment Agent 工作台，
以便在统一的工作台中处理部署相关活动。

**Acceptance Criteria / 验收标准**

1. Given the user is logged into the system,
   When the user views the main navigation,
   Then the WWA level-1 menu is visible and contains Deployment Agent as a level-2 entry.

   假设用户已登录系统，
   当用户查看主导航时，
   则应看到 WWA 一级菜单，且其中包含 Deployment Agent 二级入口。

2. Given the user clicks the Deployment Agent entry,
   When the workspace page loads,
   Then the Deployment Agent workspace is displayed.

   假设用户点击 Deployment Agent 入口，
   当工作台页面加载完成时，
   则应显示 Deployment Agent 工作台页面。

3. Given the user is in the Deployment Agent workspace,
   When the user views the left-side navigation,
   Then the shared menu entries Template Management, Configuration Management, and Audit Log are visible.

   假设用户已进入 Deployment Agent 工作台，
   当用户查看左侧导航时，
   则应看到共享菜单项 Template Management、Configuration Management 和 Audit Log。

**Notes / Assumptions / 备注 / 假设**

- WWA is a reusable platform layer for future agent workspaces.
- WWA 是未来多个 Agent 工作台的复用平台层。
- Deployment Agent is the first workspace implemented under WWA.
- Deployment Agent 是 WWA 下首个落地的工作台。

**Dependencies / 依赖**

- Application navigation system supports multi-level menu structure.
- 应用导航系统支持多级菜单结构。
- Authentication is already available.
- 系统已具备登录认证能力。

**Out of Scope / 不在范围内**

- Full implementation of Template Management, Configuration Management, or Audit Log pages.
- Template Management、Configuration Management、Audit Log 的完整页面实现。

**Open Questions / 开放问题**

- What is the exact routing path for Deployment Agent?
- Deployment Agent 的具体路由路径是什么？
- Should breadcrumb navigation be shown in the workspace?
- 工作台内是否需要显示 breadcrumb 面包屑导航？

---

## User Story 2 / 用户故事 2

**Title / 标题**
Upload deployment request via Excel file / 通过 Excel 文件上传部署请求

**Story / 故事**
As a Developer,
I want to upload a deployment request using the fixed Excel template,
so that I can submit deployment input into the system.

作为 Developer，
我希望通过固定的 Excel 模板上传部署请求，
以便将部署输入提交到系统中。

**Acceptance Criteria / 验收标准**

1. Given the Developer is in the Deployment Agent workspace,
   When the Developer clicks the "Upload Excel" action,
   Then an upload dialog is displayed.

   假设 Developer 位于 Deployment Agent 工作台中，
   当 Developer 点击 “Upload Excel” 操作时，
   则应显示上传弹窗。

2. Given the upload dialog is displayed,
   When the Developer views the available actions,
   Then the dialog provides Download Template, View Sample, and Upload actions.

   假设上传弹窗已显示，
   当 Developer 查看可用操作时，
   则应看到 Download Template、View Sample 和 Upload 操作。

3. Given the Developer selects a valid Excel file,
   When the Developer confirms upload,
   Then the file is accepted and the system starts import processing.

   假设 Developer 选择了一个有效的 Excel 文件，
   当 Developer 确认上传时，
   则系统应接收该文件并开始导入处理。

4. Given the upload is processed successfully,
   When the import completes,
   Then the system displays a success message and provides access to the import log entry.

   假设上传处理成功，
   当导入完成时，
   则系统应显示成功提示，并提供查看导入日志记录的入口。

5. Given the Developer uploads an invalid or malformed Excel file,
   When validation fails,
   Then the system rejects the upload and displays validation errors.

   假设 Developer 上传了无效或格式错误的 Excel 文件，
   当校验失败时，
   则系统应拒绝该上传并显示校验错误信息。

**Notes / Assumptions / 备注 / 假设**

- Day 1 uses a fixed Excel template.
- Day 1 使用固定 Excel 模板。
- Dynamic template definition is not part of MVP.
- 动态模板定义不属于 MVP 范围。

**Dependencies / 依赖**

- Excel parsing and validation capability.
- Excel 解析与校验能力。
- Fixed template is available to users.
- 固定模板已可供用户下载使用。

**Out of Scope / 不在范围内**

- Dynamic template management.
- 动态模板管理。
- Resume upload after network interruption.
- 网络中断后的断点续传。

**Open Questions / 开放问题**

- What are the exact mandatory fields in the fixed Excel template?
- 固定 Excel 模板中的必填字段具体有哪些？
- What is the maximum supported file size?
- 支持的最大文件大小是多少？

---

## User Story 3 / 用户故事 3

**Title / 标题**
Create or update Release Flow from imported deployment request / 基于导入的部署请求创建或更新 Release Flow

**Story / 故事**
As a Developer,
I want the imported deployment request to create or update Release Flow records,
so that deployment activities can be tracked in a structured release journey.

作为 Developer，
我希望导入后的部署请求能够创建或更新 Release Flow 记录，
以便部署活动能够在结构化的发布链路中被跟踪。

**Acceptance Criteria / 验收标准**

1. Given a valid Excel file is imported successfully,
   When the system processes the request data,
   Then one or more Release Flow records are created or updated in the system.

   假设一个有效的 Excel 文件已成功导入，
   当系统处理请求数据时，
   则系统应创建或更新一条或多条 Release Flow 记录。

2. Given Release Flow records are created or updated,
   When the user views the Deployment Flow Summary,
   Then the corresponding Release Flow records are visible in the summary list.

   假设 Release Flow 记录已被创建或更新，
   当用户查看 Deployment Flow Summary 时，
   则应能在汇总列表中看到对应的 Release Flow 记录。

3. Given the imported file contains multiple request groups,
   When the import processing completes,
   Then the system can create multiple Release Flow records as needed.

   假设导入文件中包含多个请求分组，
   当导入处理完成时，
   则系统应能按需创建多条 Release Flow 记录。

**Notes / Assumptions / 备注 / 假设**

- Release Flow is the top-level business object.
- Release Flow 是顶层业务对象。
- A single Excel file may produce multiple Release Flows.
- 一个 Excel 文件可能产生多条 Release Flow。

**Dependencies / 依赖**

- Release Flow data model.
- Release Flow 数据模型。
- Import mapping rules from Excel to Release Flow / Request / Task.
- 从 Excel 到 Release Flow / Request / Task 的映射规则。

**Out of Scope / 不在范围内**

- Manual merge/split management for Release Flows.
- Release Flow 的手动合并/拆分管理。

**Open Questions / 开放问题**

- What is the exact grouping logic for creating a Release Flow?
- 创建 Release Flow 的精确分组规则是什么？
- If Release ID is missing, what fallback rule should be used?
- 如果缺少 Release ID，应采用什么兜底规则？

---

## User Story 4 / 用户故事 4

**Title / 标题**
View Release Flow summary with stage progress / 查看带阶段进度的 Release Flow 汇总视图

**Story / 故事**
As a Developer, TL, or DevOps Admin,
I want to see all Release Flows in a summary view with stage progress across SIT, UAT, and PROD,
so that I can monitor release status at a glance.

作为 Developer、TL 或 DevOps Admin，
我希望在汇总视图中查看所有 Release Flow 以及 SIT、UAT、PROD 阶段进度，
以便快速掌握发布状态。

**Acceptance Criteria / 验收标准**

1. Given the Deployment Agent workspace is open,
   When the user views the Deployment Flow Summary section,
   Then the system displays a list of Release Flows.

   假设 Deployment Agent 工作台已打开，
   当用户查看 Deployment Flow Summary 区域时，
   则系统应显示 Release Flow 列表。

2. Given a Release Flow row is displayed,
   When the user examines the row,
   Then the row shows the Release Flow identifier and stage statuses for SIT, UAT, and PROD.

   假设某条 Release Flow 记录已显示，
   当用户查看该行时，
   则该行应显示 Release Flow 标识，以及 SIT、UAT、PROD 的阶段状态。

3. Given stage statuses are displayed,
   When the user reads the values,
   Then each stage status is shown using supported summary values such as Done, Running, or Pending.

   假设阶段状态已显示，
   当用户读取状态值时，
   则每个阶段状态应以 Done、Running、Pending 等支持的汇总值显示。

4. Given the user applies a supported filter,
   When the filter takes effect,
   Then the summary list updates to show only matching Release Flows.

   假设用户应用了一个支持的筛选条件，
   当筛选生效时，
   则汇总列表应只显示匹配的 Release Flow。

**Notes / Assumptions / 备注 / 假设**

- Release Flow summary is a top-level monitoring view.
- Release Flow 汇总视图是顶层监控视图。
- Summary status values are simplified for MVP.
- MVP 中汇总状态值采用简化表达。

**Dependencies / 依赖**

- Depends on User Story 3: Release Flow records must exist before summary display.
- 依赖 User Story 3：在展示汇总视图前，系统中必须已存在 Release Flow 记录。
- Release Flow data model with stage-level status aggregation.
- 具备阶段状态聚合能力的 Release Flow 数据模型。
- Filter controls and filtering logic.
- 筛选控件和筛选逻辑。

**Out of Scope / 不在范围内**

- Historical dashboards and trend analytics.
- 历史趋势分析和仪表盘。
- Export/reporting features.
- 导出/报表能力。

**Open Questions / 开放问题**

- Should historical/completed Release Flows be shown by default?
- 默认是否显示历史/已完成的 Release Flow？
- What is the default sorting rule?
- 默认排序规则是什么？

---

## User Story 5 / 用户故事 5

**Title / 标题**
View selected Release Flow details / 查看选中 Release Flow 的详细信息

**Story / 故事**
As a TL or DevOps Admin,
I want to view the details of a selected Release Flow,
so that I can understand its current stage and review context before taking action.

作为 TL 或 DevOps Admin，
我希望查看选中 Release Flow 的详细信息，
以便在采取操作前了解其当前阶段和审核上下文。

**Acceptance Criteria / 验收标准**

1. Given the user selects a Release Flow from the summary list,
   When the selection is applied,
   Then the Selected Release Flow Details section is updated.

   假设用户从汇总列表中选中一条 Release Flow，
   当选中生效时，
   则应更新 Selected Release Flow Details 区域。

2. Given the Selected Release Flow Details section is displayed,
   When the user views the details,
   Then the section shows Project, Release ID, Current Stage, Current Request ID, Review Status, and Review Owner.

   假设 Selected Release Flow Details 区域已显示，
   当用户查看详情时，
   则该区域应显示 Project、Release ID、Current Stage、Current Request ID、Review Status 和 Review Owner。

3. Given the selected Release Flow changes,
   When the user selects another Release Flow,
   Then the details section refreshes to reflect the newly selected Release Flow.

   假设选中的 Release Flow 发生变化，
   当用户选中另一条 Release Flow 时，
   则详情区域应刷新为新选中 Release Flow 的信息。

**Notes / Assumptions / 备注 / 假设**

- This section provides review context for the current release journey.
- 此区域用于提供当前发布链路的审核上下文。

**Dependencies / 依赖**

- Release Flow detail fields are available in the backend model.
- 后端模型已提供 Release Flow 详情字段。

**Out of Scope / 不在范围内**

- Editing Release Flow metadata in this section.
- 在该区域中编辑 Release Flow 元数据。

**Open Questions / 开放问题**

- Should Review Owner always be a single user, or can it be a group?
- Review Owner 是否始终为单个用户，还是可以是一个组？
- How should empty or unassigned review fields be displayed?
- 为空或未分配的审核字段应如何显示？

---

## User Story 6 / 用户故事 6

**Title / 标题**
View task-level details and execution results / 查看任务级详情和执行结果

**Story / 故事**
As a TL,
I want to view task-level details including status, result summary, and timestamps,
so that I can understand execution outcomes before making a decision.

作为 TL，
我希望查看任务级详情，包括状态、结果摘要和时间信息，
以便在做出决策前了解执行结果。

**Acceptance Criteria / 验收标准**

1. Given a Release Flow is selected,
   When the user views the Task Details section,
   Then the system displays the tasks associated with the current Request or Release Flow context.

   假设某条 Release Flow 已被选中，
   当用户查看 Task Details 区域时，
   则系统应显示与当前 Request 或 Release Flow 上下文关联的任务列表。

2. Given a task row is displayed,
   When the user examines the row,
   Then the row shows Task Name, Status, Result Summary, Start Time, End Time, and Available Actions.

   假设某条任务记录已显示，
   当用户查看该行时，
   则该行应显示 Task Name、Status、Result Summary、Start Time、End Time 和 Available Actions。

3. Given a task has execution output available,
   When the user clicks "View Result",
   Then the system displays the task result content.
   Result content format (summary, raw logs, parsed output) will be finalized in design.
   For MVP, assume the system can display at least a result summary and raw logs.

   假设某个任务已有执行输出，
   当用户点击 “View Result” 时，
   则系统应显示该任务的结果内容。
   结果内容格式（摘要、原始日志、解析输出）将在设计阶段最终确定。
   MVP 默认系统至少能够展示结果摘要和原始日志。

4. Given a task supports operator actions,
   When the user opens the Available Actions menu,
   Then the supported actions are displayed for that task, including Edit, View Result, and Decision actions.
   When applicable, Decision actions include Approve, Reject, Rerun, and Skip.

   假设某个任务支持操作动作，
   当用户打开 Available Actions 菜单时，
   则系统应显示该任务支持的操作项，包括 Edit、View Result 和 Decision。
   在条件满足时，Decision 操作包括 Approve、Reject、Rerun 和 Skip。

**Notes / Assumptions / 备注 / 假设**

- Result Summary is a short inline view of execution outcome.
- Result Summary 是执行结果的简短内联摘要。
- Full result content may be shown in a separate UI container.
- 完整结果内容可通过独立 UI 容器展示。

**Dependencies / 依赖**

- Task data model with execution result fields.
- 包含执行结果字段的 Task 数据模型。
- Result retrieval/display capability.
- 结果获取/展示能力。

**Out of Scope / 不在范围内**

- Real-time streaming logs.
- 实时流式日志。
- Push notifications for task result updates.
- 任务结果更新的推送通知。

**Open Questions / 开放问题**

- Is the task list scoped by Request, Stage, or full Release Flow?
- 任务列表的展示范围应按 Request、Stage，还是整个 Release Flow？

---

## User Story 7 / 用户故事 7

**Title / 标题**
Edit task input parameters before execution / 在任务执行前编辑任务输入参数

**Story / 故事**
As a TL,
I want to edit task input parameters before execution,
so that I can correct or refine execution input without restarting the whole Release Flow.

作为 TL，
我希望在任务执行前编辑任务输入参数，
以便在不重启整个 Release Flow 的情况下修正或完善执行输入。

**Acceptance Criteria / 验收标准**

1. Given a task is in an editable status,
   When the TL clicks the "Edit" action,
   Then the system displays editable task input parameters.

   假设某个任务处于可编辑状态，
   当 TL 点击 “Edit” 操作时，
   则系统应显示可编辑的任务输入参数。

2. Given the edit view is displayed,
   When the TL updates one or more input values and saves,
   Then the system validates and persists the updated input.

   假设编辑视图已显示，
   当 TL 修改一个或多个输入值并保存时，
   则系统应校验并保存更新后的输入。

3. Given the updated input is invalid,
   When the TL attempts to save,
   Then the system rejects the change and displays validation errors.

   假设更新后的输入无效，
   当 TL 尝试保存时，
   则系统应拒绝变更并显示校验错误。

4. Given task input is updated successfully,
   When the task is later executed or rerun,
   Then the execution uses the latest saved input values.

   假设任务输入已成功更新，
   当该任务随后被执行或重跑时，
   则执行应使用最新保存的输入值。

5. Given a task input change is saved,
   When the save completes,
   Then an audit log entry is created for the edit action.

   假设任务输入变更已保存，
   当保存完成时，
   则系统应为该编辑动作创建一条审计日志。

**Notes / Assumptions / 备注 / 假设**

- Editable fields are limited to defined task input parameters.
- 可编辑字段仅限于预定义的任务输入参数。
- Editing after task execution starts is not supported in MVP.
- MVP 不支持任务开始执行后的编辑。

**Dependencies / 依赖**

- Task input schema and validation rules.
- 任务输入 schema 和校验规则。
- Audit logging capability.
- 审计日志能力。

**Out of Scope / 不在范围内**

- Editing task type or task identity.
- 编辑任务类型或任务标识。
- Free-form input outside defined schema.
- 超出定义 schema 的自由输入。

**Open Questions / 开放问题**

- Which task statuses should be considered editable?
- 哪些任务状态应被视为可编辑？
- Should only changed fields be logged in audit?
- 审计日志中是否只记录变更字段？

---

## User Story 8 / 用户故事 8

**Title / 标题**
Execute task-level decisions to control Release Flow progression / 执行任务级决策以控制 Release Flow 推进

**Story / 故事**
As a TL,
I want to make explicit task-level decisions after reviewing execution results,
so that the Release Flow progresses in a controlled and traceable way.

作为 TL，
我希望在查看执行结果后做出明确的任务级决策，
以便 Release Flow 能够以受控且可追踪的方式推进。

**Acceptance Criteria / 验收标准**

1. Given a task has completed execution and is waiting for review,
   When the TL opens the Decision action,
   Then the system displays the supported decision options: Approve, Reject, Rerun, and Skip.

   假设某个任务已执行完成并正在等待审核，
   当 TL 打开 Decision 操作时，
   则系统应显示支持的决策选项：Approve、Reject、Rerun、Skip。

2. Given the TL selects Approve,
   When the decision is confirmed,
   Then the current task is marked accordingly and the Release Flow continues to the next available step.

   假设 TL 选择了 Approve，
   当该决策被确认时，
   则当前任务应被标记为相应状态，且 Release Flow 继续到下一个可用步骤。

3. Given the TL selects Reject,
   When the decision is confirmed,
   Then the current Release Flow is stopped and no further steps are executed.

   假设 TL 选择了 Reject，
   当该决策被确认时，
   则当前 Release Flow 应被停止，且不再执行后续步骤。

4. Given the TL selects Rerun,
   When the decision is confirmed,
   Then the system re-executes the current step.

   假设 TL 选择了 Rerun，
   当该决策被确认时，
   则系统应重新执行当前步骤。

5. Given the TL selects Skip,
   When the decision is confirmed,
   Then the current step is skipped and the Release Flow continues to the next available step.

   假设 TL 选择了 Skip，
   当该决策被确认时，
   则当前步骤应被跳过，且 Release Flow 继续到下一个可用步骤。

6. Given any decision action is completed,
   When the action is processed successfully,
   Then an audit log entry is created for that decision.

   假设任一决策动作已完成，
   当该动作被成功处理时，
   则系统应为该决策创建一条审计日志。

**Notes / Assumptions / 备注 / 假设**

- Human-in-the-loop decision control is mandatory in MVP.
- 人在回路的决策控制是 MVP 的强制要求。
- The system must not auto-progress after execution without decision.
- 系统在未做出决策前不得因执行完成而自动推进。

**Dependencies / 依赖**

- Task and Release Flow state transition rules.
- Task 和 Release Flow 的状态流转规则。
- Audit logging capability.
- 审计日志能力。
- Execution integration for rerun.
- 用于重跑的执行集成能力。

**Out of Scope / 不在范围内**

- Automatic decision-making based on result content.
- 基于结果内容的自动决策。
- Parallel branch workflow execution.
- 并行分支工作流执行。

**Open Questions / 开放问题**

- What exact statuses should be displayed after Reject?
- Reject 之后具体应显示哪些状态？
- Should Reject require an extra confirmation step?
- Reject 是否需要额外确认步骤？
- Should Rerun preserve prior execution history?
- Rerun 是否需要保留历史执行记录？

---

## User Story 9 / 用户故事 9

**Title / 标题**
Record operator actions for audit traceability / 记录操作行为以支持审计追踪

**Story / 故事**
As an Audit team member or management user,
I want key operator actions to be logged with traceable information,
so that I can review deployment-related operations for compliance and accountability.

作为 Audit 团队成员或管理层用户，
我希望关键操作行为能够被记录并具备可追踪信息，
以便我可以出于合规和问责目的审查部署相关操作。

**Acceptance Criteria / 验收标准**

1. Given a user performs a supported key action in the Deployment Agent workspace,
   When the action is processed successfully,
   Then the system creates and persists an audit log entry for that action.

   假设用户在 Deployment Agent 工作台中执行了一个受支持的关键操作，
   当该操作被成功处理时，
   则系统应为该操作创建并持久化一条审计日志记录。

2. Given a supported action is audit-relevant,
   When the action occurs,
   Then the audit log entry includes operator identity, action type, timestamp, and related context.

   假设某个受支持操作属于审计相关动作，
   当该动作发生时，
   则审计日志记录应包含操作人身份、动作类型、时间戳和相关上下文信息。

3. Given the system supports key actions such as upload, edit, view result, approve, reject, rerun, and skip,
   When those actions are performed,
   Then each action is logged consistently using the audit mechanism.

   假设系统支持 upload、edit、view result、approve、reject、rerun、skip 等关键动作，
   当这些动作发生时，
   则系统应通过统一的审计机制一致地记录这些动作。

**Notes / Assumptions / 备注 / 假设**

- MVP requires backend audit logging capability.
- MVP 需要具备后端审计日志能力。
- Detailed audit query UI is not required in MVP.
- MVP 不要求实现完整的审计查询 UI。

**Dependencies / 依赖**

- Audit log storage and schema.
- 审计日志存储和 schema。
- User identity is available from authentication context.
- 用户身份可从认证上下文中获取。

**Out of Scope / 不在范围内**

- Full audit query/filter/reporting page.
- 完整的审计查询/筛选/报表页面。
- Audit retention policy design.
- 审计保留策略设计。

**Open Questions / 开放问题**

- Where should audit logs be stored?
- 审计日志应存储在哪里？
- Who can access audit log data in MVP?
- MVP 中谁可以访问审计日志数据？

---

## User Story 10 / 用户故事 10

**Title / 标题**
Maintain integration configuration in UI / 在 UI 中维护集成配置

**Story / 故事**
As a DevOps Admin,
I want to maintain key integration configuration values in the UI,
so that deployment execution can use managed configuration instead of hardcoded values.

作为 DevOps Admin，
我希望在 UI 中维护关键集成配置，
以便部署执行能够使用受管理的配置，而不是硬编码值。

**Acceptance Criteria / 验收标准**

1. Given the DevOps Admin enters Configuration Management,
   When the page is displayed,
   Then the system shows editable MVP configuration items, including Jenkins URL, Ansible URL, and one additional configuration item to be confirmed.

   假设 DevOps Admin 进入 Configuration Management，
   当页面显示时，
   则系统应展示 MVP 支持的可编辑配置项，包括 Jenkins URL、Ansible URL，以及一个待确认的附加配置项。

2. Given the DevOps Admin updates a configuration value,
   When the admin saves the change,
   Then the system validates the input and persists the updated configuration.

   假设 DevOps Admin 修改了某个配置值，
   当管理员保存变更时，
   则系统应校验输入并持久化保存更新后的配置。

3. Given the configuration is saved successfully,
   When a related deployment task is executed,
   Then the task uses the latest saved configuration value.

   假设配置已成功保存，
   当相关部署任务执行时，
   则该任务应使用最新保存的配置值。

4. Given the DevOps Admin enters an invalid configuration value,
   When validation fails,
   Then the system rejects the save and displays an error message.

   假设 DevOps Admin 输入了无效配置值，
   当校验失败时，
   则系统应拒绝保存并显示错误信息。

**Notes / Assumptions / 备注 / 假设**

- Configuration Management is a shared WWA capability.
- Configuration Management 是 WWA 的共享能力。
- MVP focuses on key configuration required by deployment execution.
- MVP 聚焦于部署执行所需的关键配置项。

**Dependencies / 依赖**

- Configuration storage mechanism.
- 配置存储机制。
- Deployment execution can read managed configuration values.
- 部署执行能够读取受管理的配置值。

**Out of Scope / 不在范围内**

- Advanced configuration versioning and rollback.
- 高级配置版本管理和回滚。
- Environment-specific override matrix.
- 环境级覆盖矩阵。

**Open Questions / 开放问题**

- What is the additional Day 1 configuration item to be confirmed?
- Day 1 还需确认的附加配置项是什么？
- Do configuration changes take effect immediately?
- 配置变更是否立即生效？

---

## User Story 11 / 用户故事 11

**Title / 标题**
View audit logs for compliance review / 查看审计日志以支持合规审查

**Story / 故事**
As an Audit team member or management user,
I want to view audit logs in a minimal read-only format,
so that I can review deployment-related operations for compliance and accountability during MVP.

作为 Audit 团队成员或管理层用户，
我希望能够以最小只读方式查看审计日志，
以便在 MVP 阶段对部署相关操作进行合规和问责审查。

**Acceptance Criteria / 验收标准**

1. Given the user enters the Audit Log area,
   When the page or section is displayed,
   Then the system shows a read-only list of recent audit log records.

   假设用户进入 Audit Log 区域，
   当页面或区域显示时，
   则系统应展示最近审计日志记录的只读列表。

2. Given an audit log record is displayed,
   When the user views the record,
   Then the record includes operator identity, action type, timestamp, and related context.

   假设某条审计日志记录已显示，
   当用户查看该记录时，
   则该记录应包含操作人身份、动作类型、时间戳和相关上下文信息。

3. Given the user is viewing audit logs in MVP,
   When the user interacts with the list,
   Then the user can read the records but cannot edit or delete them.

   假设用户正在 MVP 中查看审计日志，
   当用户与该列表交互时，
   则用户只能读取记录，不能编辑或删除。

**Notes / Assumptions / 备注 / 假设**

- MVP only requires a minimal audit log viewing capability.
- MVP 仅要求最小化的审计日志查看能力。
- Advanced filtering, export, and reporting remain future scope.
- 高级筛选、导出和报表能力留待后续阶段。

**Dependencies / 依赖**

- Audit log records are already created and persisted by the audit logging mechanism.
- 审计日志记录已由审计机制创建并持久化。
- Read-only access control is available for Audit/Management users.
- 已具备面向 Audit/Management 用户的只读访问控制。

**Out of Scope / 不在范围内**

- Advanced audit filtering and search.
- 高级审计筛选和搜索。
- Exporting audit logs.
- 导出审计日志。
- Audit analytics dashboard.
- 审计分析仪表盘。

**Open Questions / 开放问题**

- Should the MVP audit log view be a standalone page or a simple embedded list?
- MVP 审计日志视图应是独立页面，还是一个简单嵌入式列表？
- How many recent records should be shown by default?
- 默认应展示多少条最近记录？

---

## User Story 12 / 用户故事 12

**Title / 标题**
Manage deployment templates with full CRUD lifecycle / 通过完整 CRUD 生命周期管理部署模板

**Story / 故事**
As a TL or DevOps Admin,
I want to create, view, edit, clone, and delete deployment templates,
so that I can define reusable multi-task deployment blueprints for future release flows.

作为 TL 或 DevOps Admin，
我希望能够创建、查看、编辑、克隆和删除部署模板，
以便定义可复用的多任务部署蓝图供后续 Release Flow 使用。

**Acceptance Criteria / 验收标准**

1. Given the user enters Template Management,
   When the page is displayed,
   Then the system shows a list of existing templates with name, description, stage, and task count.

2. Given the user clicks Create New Template,
   When the creation modal opens,
   Then the user can create a template via Manual Entry or Upload Excel tabs.

3. Given a template row is displayed,
   When the user opens the More menu,
   Then Clone, Edit, and Delete actions are available.

4. Given the user selects a template from the list,
   When the selection is applied,
   Then the Template Details panel shows the full task table with task definitions.

5. Given the user clones a template,
   When the clone completes,
   Then a new draft template is created with copied metadata and task definitions.

6. Given the user deletes a template,
   When the delete is confirmed,
   Then the template is removed from the list and the selection state resets.

**Notes / Assumptions / 备注 / 假设**

- Templates are currently stored locally in frontend state only — backend API persistence is pending.
- Each template is treated as a multi-task deployment blueprint.
- Template selection switches the shared Template Details workspace directly (no separate View Details button).

**Dependencies / 依赖**

- Template data model and backend API (pending).
- Excel template download/upload capability.

**Out of Scope / 不在范围内**

- Template versioning or history.
- Template approval workflow.

---

## User Story 13 / 用户故事 13

**Title / 标题**
Author and maintain tasks within a deployment template / 在部署模板中编写和维护任务

**Story / 故事**
As a TL or DevOps Admin,
I want to add, edit, and delete tasks within a template, including dependency maintenance,
so that each template defines the exact task sequence and gate structure for a deployment.

作为 TL 或 DevOps Admin，
我希望在模板中添加、编辑和删除任务（包括依赖关系维护），
以便每个模板定义精确的任务序列和审批门结构。

**Acceptance Criteria / 验收标准**

1. Given a template is selected for editing,
   When the user clicks Add Task,
   Then a task authoring dialog is displayed with fields aligned to the deployment task structure.

2. Given a task exists in the template,
   When the user clicks Edit on the task row,
   Then the task dialog opens with pre-filled values for editing.

3. Given a task exists in the template,
   When the user clicks Delete on the task row,
   Then the task is removed from the template definition.

4. Given task authoring is active,
   When the user sets the Critical (Y/N) flag on a task,
   Then the flag is saved as part of the template task definition.

5. Given Manual Entry is selected during template creation,
   When the first task is created,
   Then the system automatically hands off from Manual Entry into the task-creation step.

**Notes / Assumptions / 备注 / 假设**

- Task structure in templates mirrors the deployment task structure (name, execution type, category, critical flag, dependencies).
- Critical flag in templates defines review-blocking steps before a rundown is created.

**Dependencies / 依赖**

- Template CRUD (User Story 12).

---

## User Story 14 / 用户故事 14

**Title / 标题**
Navigate WWA platform with two-level menu and workspace flyout / 通过两级菜单和工作台飞出面板导航 WWA 平台

**Story / 故事**
As any authenticated user,
I want to navigate WWA capabilities through a clear two-level navigation structure,
so that I can access Deployment Agent, Template Management, Configuration Management, and Audit Log from a unified platform shell.

作为任何已认证用户，
我希望通过清晰的两级导航结构访问 WWA 各项能力，
以便从统一的平台外壳访问 Deployment Agent、模板管理、配置管理和审计日志。

**Acceptance Criteria / 验收标准**

1. Given the user is in the workspace,
   When the user views the left navigation,
   Then WWA appears as a first-level menu item with a second-level flyout panel.

2. Given the user clicks WWA in the sidebar,
   When the flyout opens,
   Then the second-level items include Deployment Agent, Template Management, Configuration Management, and Audit Log.

3. Given the user selects a workspace page from the flyout,
   When the navigation completes,
   Then the flyout closes and the selected page is displayed.

4. Given the user clicks outside the flyout,
   When the click is detected,
   Then the flyout closes.

5. Given the user does not have access to a shared capability,
   When the user views the sidebar,
   Then the menu entry is still visible but the page shows access guidance instead of hiding the entry.

6. Given first-level placeholder applications exist alongside WWA,
   When the user views the navigation,
   Then the left sidebar reads like a broader platform shell with only WWA opening a working second-level flyout.

**Notes / Assumptions / 备注 / 假设**

- The flyout panel is not clipped by the sidebar scroll container.
- No redundant mini WWA heading appears in the flyout panel.

**Dependencies / 依赖**

- Authentication and role management.

---

## User Story 15 / 用户故事 15

**Title / 标题**
View task activity history for traceability / 查看任务活动历史以支持追溯

**Story / 故事**
As a TL or DevOps Admin,
I want to view task-level activity history showing who did what, when it happened, and the related input/output,
so that I can trace all actions taken on a task from audit and execution records.

作为 TL 或 DevOps Admin，
我希望查看任务级活动历史，了解谁做了什么、何时发生、以及相关的输入/输出，
以便从审计和执行记录中追溯对某任务的所有操作。

**Acceptance Criteria / 验收标准**

1. Given a task is displayed in the release detail view,
   When the user clicks the Activity action,
   Then a task activity dialog opens.

2. Given the activity dialog is open,
   When the data loads,
   Then the dialog displays combined audit log entries and execution history records for that task.

3. Given audit logs or execution history fail to load,
   When the dialog renders,
   Then a warning is shown indicating partial data, and the available data is still displayed.

**Dependencies / 依赖**

- Audit logging capability (User Story 9).
- Execution history records.

---

## User Story 16 / 用户故事 16

**Title / 标题**
Manage stage-level rundown information for deployment requests / 管理部署请求的阶段级 Rundown 信息

**Story / 故事**
As a TL or DevOps Admin,
I want to view and edit stage-level rundown fields such as SNOW group, application, site, and estimated remaining time,
so that the deployment context is captured per request for operational coordination.

作为 TL 或 DevOps Admin，
我希望查看和编辑阶段级 Rundown 字段（如 SNOW group、application、site、预计剩余时间），
以便按请求捕获部署上下文用于运营协调。

**Acceptance Criteria / 验收标准**

1. Given a release flow detail page is open with stage tabs,
   When the user views a stage tab,
   Then a Rundown Information panel is displayed for that stage.

2. Given the Rundown Information panel is visible,
   When the user clicks Edit on the rundown,
   Then a dialog opens with editable fields for SNOW group, application, site, and estimated remaining time.

3. Given the user saves rundown changes,
   When the save completes,
   Then the updated values are persisted via the backend API.

4. Given a request is in a runnable state,
   When the user clicks Start Deployment, Refresh, or Mark as Failed,
   Then the corresponding request-level action is executed and the detail refreshes.

**Dependencies / 依赖**

- Release Flow detail API with rundown fields.

---

## User Story 17 / 用户故事 17

**Title / 标题**
Gate workflow progression on critical task review / 基于关键任务审查控制流程推进

**Story / 故事**
As a TL,
I want tasks marked as Critical to block the next pending task from being released until the critical task is reviewed,
so that review-blocking steps enforce governance before the workflow progresses.

作为 TL，
我希望被标记为 Critical 的任务能够阻止下一个待处理任务被释放，直到该关键任务被审查，
以便在流程推进前通过审查阻断步骤实施治理。

**Acceptance Criteria / 验收标准**

1. Given a task has the Critical (Y/N) field set to Y,
   When the task is displayed in the release detail table,
   Then a Critical badge is visible on the task row.

2. Given a critical task is in Awaiting_Review status,
   When the system evaluates task progression,
   Then the next pending task is not released until the critical task receives a decision.

3. Given the critical task is approved or skipped,
   When the decision is recorded,
   Then the workflow gate is lifted and the next task becomes available.

4. Given the Excel import includes a Critical column,
   When the import completes,
   Then the critical flag is correctly mapped to the task entity.

**Notes / Assumptions / 备注 / 假设**

- Critical flag is also supported in Template Management task authoring.
- The gate logic may be enforced on the backend.

**Dependencies / 依赖**

- Decision control (User Story 8).
- Excel import mapping.

---

## User Story 18 / 用户故事 18

**Title / 标题**
Control task action permissions by ownership and role / 基于所有权和角色控制任务操作权限

**Story / 故事**
As a TL or DevOps Admin,
I want task-level actions (edit input, record results, submit auto execution, apply decisions) to be restricted to the task owner or DEVOPS_ADMIN,
so that only authorized users can modify task execution state.

作为 TL 或 DevOps Admin，
我希望任务级操作（编辑输入、记录结果、提交自动执行、应用决策）仅限任务所有者或 DEVOPS_ADMIN，
以便只有授权用户可以修改任务执行状态。

**Acceptance Criteria / 验收标准**

1. Given a task row is displayed,
   When the user views available actions,
   Then Edit, View Result, and Decision dropdown are always shown, with state-based disabling.

2. Given the current user is neither the task owner nor a DEVOPS_ADMIN,
   When the user hovers over a disabled action,
   Then a tooltip explains whether the action is blocked by role or task status.

3. Given a MANUAL task needs result submission,
   When the user opens the Edit dialog,
   Then the result can be recorded from within the Edit dialog (not a separate action).

**Dependencies / 依赖**

- User authentication and role context.
- Task ownership assignment.

---

## User Story 19 / 用户故事 19

**Title / 标题**
View execution mix and task category in release detail / 在发布详情中查看执行组合和任务类别

**Story / 故事**
As a TL or DevOps Admin,
I want to see the execution mix (manual vs auto task counts and percentages) and task category in the release detail view,
so that I can understand the composition and automation coverage of each deployment.

作为 TL 或 DevOps Admin，
我希望在发布详情视图中查看执行组合（手动 vs 自动任务数和百分比）和任务类别，
以便了解每次部署的组成和自动化覆盖率。

**Acceptance Criteria / 验收标准**

1. Given a release flow detail page is open,
   When the user views the execution mix section,
   Then both task counts and manual/auto percentages are displayed.

2. Given a task row is displayed in the release detail table,
   When the user views the row,
   Then the task category (from imported Activity category) is shown.

**Dependencies / 依赖**

- Excel import maps Activity category to task category.

---

## User Story 20 / 用户故事 20

**Title / 标题**
View stage status on deployment summary table / 在部署汇总表中查看阶段状态

**Story / 故事**
As a Developer, TL, or DevOps Admin,
I want to see SIT, UAT, and PROD stage statuses directly in the deployment summary table,
so that I can assess release progress at a glance without opening each flow detail.

作为 Developer、TL 或 DevOps Admin，
我希望在部署汇总表中直接查看 SIT、UAT、PROD 阶段状态，
以便一目了然地评估发布进展，无需逐个打开流程详情。

**Acceptance Criteria / 验收标准**

1. Given the Deployment Agent summary page is open,
   When the user views the Release Flow table,
   Then SIT, UAT, and PROD status columns are visible for each row.

2. Given a stage has a status value,
   When the user reads the column,
   Then the status is displayed with a visual badge (e.g. Done, Running, Pending).

**Dependencies / 依赖**

- Backend stage status aggregation.

---

## User Story 21 / 用户故事 21

**Title / 标题**
Manage Deployment Agent access grants / 管理 Deployment Agent 访问授权

**Story / 故事**
As a DevOps Admin,
I want to grant, suspend, and reactivate Deployment Agent access for employees,
so that product access can be managed without building a separate user account system.

**Acceptance Criteria / 验收标准**

1. Given an employee has a valid enterprise identity but does not yet have Deployment Agent access,
   When the DevOps Admin creates an access grant,
   Then the system stores the employee ID, display name snapshot, status, assigned roles, and note.

2. Given an employee already has an active access grant,
   When the DevOps Admin suspends the employee,
   Then the system keeps the access record and changes the status to Suspended instead of physically deleting it.

3. Given an employee has a suspended access grant,
   When the DevOps Admin reactivates the employee,
   Then the employee regains product access and the authorization history remains preserved.

**Notes / Assumptions / 备注 / 假设**

- Authentication continues to come from Team Book or enterprise SSO.
- Phase 1 manages product-level access only and does not yet support project-level authorization.

**Dependencies / 依赖**

- Enterprise identity source returns employee ID and display name.
- A local access grant data model and service are added to Deployment Agent.

**Out of Scope / 不在范围内**

- Password management.
- User self-service access request and approval workflow.

**Open Questions / 开放问题**

- Should role changes require a mandatory admin note?
- Should access grants support future effective/expiry dates in a later phase?

---

## User Story 22 / 用户故事 22

**Title / 标题**
Authorize product entry with deny-by-default access control / 通过默认拒绝策略控制产品访问

**Story / 故事**
As a platform owner,
I want Deployment Agent to allow only explicitly authorized employees into the product,
so that platform access is controlled and auditable.

**Acceptance Criteria / 验收标准**

1. Given an employee is successfully authenticated by Team Book but has no Deployment Agent access grant,
   When the employee logs in,
   Then the system denies entry and displays an "Access not granted" message.

2. Given an employee has a suspended Deployment Agent access grant,
   When the employee logs in,
   Then the system denies entry and displays an "Access suspended" message.

3. Given an employee has an active Deployment Agent access grant,
   When the employee logs in,
   Then the system returns the employee's effective roles and permissions rather than relying on a single hardcoded role value.

**Notes / Assumptions / 备注 / 假设**

- Phase 1 uses a deny-by-default model.
- The existing session-based login mechanism remains in place.

**Dependencies / 依赖**

- Login and auth/session endpoints must be extended to resolve local access grants.
- Effective role and permission calculation logic must be introduced.

**Out of Scope / 不在范围内**

- Multi-factor authentication.
- SSO federation setup.

**Open Questions / 开放问题**

- Should the "Access not granted" message include guidance to contact a DevOps Admin?
- Should suspended users be allowed to view any limited read-only information?

---

## User Story 23 / 用户故事 23

**Title / 标题**
Use an Access Management console for authorization operations / 通过 Access Management 控制台执行授权操作

**Story / 故事**
As a DevOps Admin,
I want a dedicated Access Management page to manage user access and roles,
so that I can operate authorization without changing code or stub data.

**Acceptance Criteria / 验收标准**

1. Given the DevOps Admin opens the Access Management page,
   When the page finishes loading,
   Then the system displays employee ID, name, status, roles, last login time, updated by, and updated at.

2. Given the DevOps Admin searches for an employee,
   When an employee ID or name keyword is entered,
   Then matching employees are shown and the admin can grant access, edit roles, suspend access, or reactivate access.

3. Given a non-admin user attempts to access the Access Management page,
   When the user navigates from the menu or enters the URL directly,
   Then the frontend and backend both block access.

**Notes / Assumptions / 备注 / 假设**

- Phase 1 needs only a list view and detail drawer/dialog.
- The page is a product authorization console, not a full HR directory.

**Dependencies / 依赖**

- Access management API endpoints are available.
- Route-level role guards are implemented in the frontend.

**Out of Scope / 不在范围内**

- Bulk import/export of access grants.
- Organizational hierarchy visualization.

**Open Questions / 开放问题**

- Should the page search only existing grants or also allow search of non-granted enterprise users?
- Should the UI show the reason for the most recent authorization change?

---

## User Story 24 / 用户故事 24

**Title / 标题**
Enforce effective permissions consistently across UI and API / 在 UI 和 API 中一致执行有效权限

**Story / 故事**
As a product user,
I want menus, routes, and APIs to consistently reflect my effective permissions,
so that the product behaves predictably and securely.

**Acceptance Criteria / 验收标准**

1. Given a user only has Developer permissions,
   When the user enters Deployment Agent,
   Then the user sees only the menus and actions that the Developer permission set allows.

2. Given a user lacks the permission required for a page or API,
   When the user attempts to access it through navigation, direct URL entry, or an API call,
   Then the frontend blocks entry and the backend returns a permission error.

3. Given a user has multiple assigned roles,
   When the system resolves the user's effective permissions,
   Then page visibility and action availability are derived from the combined permission set.

**Notes / Assumptions / 备注 / 假设**

- Phase 1 may continue to show a primary role in the UI, but execution must be permission-based.
- Existing hardcoded role checks will be progressively centralized behind a permission model.

**Dependencies / 依赖**

- Unified permission model is defined for Deployment Agent.
- Frontend navigation and route guards are updated to use permissions.

**Out of Scope / 不在范围内**

- Project-level permissions.
- Environment-level permissions.

**Open Questions / 开放问题**

- Should Template Management be limited to DevOps Admin only in Phase 1?
- Does the Management role need a separate read-only dashboard in a later phase?

---

## User Story 25 / 用户故事 25

**Title / 标题**
Audit access grant changes / 审计访问授权变更

**Story / 故事**
As an Audit or DevOps Admin user,
I want access grant changes to be recorded and searchable,
so that authorization administration is traceable and reviewable.

**Acceptance Criteria / 验收标准**

1. Given a DevOps Admin grants access, edits roles, suspends access, or reactivates access,
   When the operation succeeds,
   Then the system writes an audit log entry containing the operator, target employee, action, and timestamp.

2. Given an Audit or DevOps Admin user opens the audit log,
   When the user filters by employee ID or action type,
   Then matching access-management records are visible in the result set.

3. Given a permission dispute or investigation occurs,
   When an authorized user reviews the audit history,
   Then the system clearly distinguishes grant, role edit, suspend, and reactivate events.

**Notes / Assumptions / 备注 / 假设**

- Access governance is audit-first.
- Existing audit logging infrastructure can be extended with new action types.

**Dependencies / 依赖**

- Access-related audit action types are added.
- Audit log access is tightened to authorized roles only.

**Out of Scope / 不在范围内**

- Approval workflow audit.
- SIEM or external compliance tool integration.

**Open Questions / 开放问题**

- Should the audit payload include before/after role diffs?
- Should access-governance audit logs be exportable in a later phase?

---

## Summary / 总结

These user stories define the full capabilities for Deployment Agent under the WWA platform.
They cover the core deployment workflow, platform navigation, template management, task governance, and shared capabilities.

这些用户故事定义了 WWA 平台下 Deployment Agent 的全部能力。
它们覆盖了核心部署工作流、平台导航、模板管理、任务治理和共享能力。

### Core workflow / 核心主链路
1. Workspace Access / 工作台访问
2. Request Upload / 请求上传
3. Release Flow Creation or Update / Release Flow 创建或更新
4. Release Flow Summary / Release Flow 汇总
5. Selected Release Flow Details / 选中 Release Flow 详情
6. Task Details and Results / 任务详情与结果
7. Task Input Editing / 任务输入编辑
8. Decision Control / 决策控制
9. Audit Logging / 审计日志记录
10. Configuration Management / 配置管理
11. Audit Log View / 审计日志查看

### Enhanced capabilities / 增强能力
12. Template Management CRUD / 模板管理 CRUD
13. Template Task Authoring / 模板任务编写
14. WWA Platform Navigation / WWA 平台导航
15. Task Activity History / 任务活动历史
16. Rundown Information Management / Rundown 信息管理
17. Critical Task Gate / 关键任务门控
18. Task Action Permissions / 任务操作权限
19. Execution Mix and Task Category / 执行组合和任务类别
20. Stage Status on Summary / 汇总表阶段状态
21. Access Grant Lifecycle / 访问授权生命周期
22. Deny-by-Default Product Entry / 默认拒绝的产品访问控制
23. Access Management Console / Access Management 控制台
24. Effective Permission Enforcement / 有效权限一致性执行
25. Access Grant Auditability / 访问授权可审计性

The main MVP objective remains:
**Ensure the core workflow can run through successfully from request → process → verification → decision.**

MVP 的核心目标保持不变：
**确保主链路能够从 request → process → verification → decision 顺利跑通。**
