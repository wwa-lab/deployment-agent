# 开放协作提交材料：Atlas Engineering Delivery Hub - Deployment

## 摘要

Atlas Engineering Delivery Hub - Deployment 是 Atlas Engineering Delivery Hub 中 M6 Deployment 阶段的 Tool / Function 类参赛项目。它把已经通过 Build 和 Testing 验证的输出，转化为受控、可追踪、可重复的 SIT / UAT / PROD 发布运行流程。

当前仓库通过 Spring Boot + Vue 发布工作区实现这个 function，覆盖 Excel 导入、Release Flow 跟踪、任务级手动/AUTO 执行、人工评审决策、访问治理、审计日志，以及面向 Jenkins/Ansible 的配置驱动执行适配器。

## 参赛类别

**Tool / Function**

这个项目与上层 Framework 项目分开呈现。Atlas Engineering Delivery Hub 是 team framework；Deployment 是其中一个 function，已经具备足够的实现价值和设计深度，可以独立作为项目展示。

## 解决的问题

很多团队在构建和测试之后，最后一公里发布仍然容易碎片化：

- 发布任务散落在表格、聊天和外部 job 控台；
- 手动步骤与 AUTO 任务难以统一评审；
- 审批没有稳定绑定到被评审的具体任务结果；
- 重跑、拒绝、回滚和交接决策容易丢失上下文；
- 凭证和环境端点可能流入不安全位置；
- 审计记录经常事后拼接。

Atlas Engineering Delivery Hub - Deployment 提供一个受控空间，用来组织、执行、评审和追踪发布工作，同时不把当前能力包装成完全自动化发布决策系统。

## Function 范围

- 从 Excel 模板导入 deployment rundown。
- 上传时必须显式选择 `SIT`、`UAT` 或 `PROD`。
- 创建或更新 Release Flow、Request 与 Task。
- 通过 workflow identifier 关联多阶段发布和重复尝试。
- 支持手动任务执行与结果记录。
- 支持通过适配器把 AUTO 任务提交到 Jenkins 或 Ansible/AWX。
- 保存任务执行历史、外部 job 链接和结果摘要。
- 下游推进前必须经过人工评审决策。
- 支持 start、fail、archive、restore、purge 等 rundown 控制。
- 通过本地 Access Grant 执行默认拒绝的访问治理。
- 为工作流、访问和配置操作记录审计日志。

## IBM iSeries One-Click Release UTL 方向

Deployment function 也是表达 IBM iSeries one-click release UTL 设计方向的位置。这里复用的不是某个硬编码脚本，而是一套受控发布壳：

- **Input：** release identifier、stage、scope、iSeries release task list、owner、expected output 和外部执行元数据。
- **Execute：** 手动或 AUTO 执行步骤，并带有明确 owner/admin 控制。
- **Output：** 任务结果、外部链接、决策、状态、执行历史和 release-flow 状态。
- **Validate：** 人工评审、状态重算、访问检查、审计记录，以及回滚或重跑交接说明。

这样 iSeries one-click 方向既可以自动化，又保持安全和可评审：自动化负责执行或提交工作，人仍然负责审批、异常处理和最终发布责任。

## 不声称具备的能力

- 不是全自动发布审批系统。
- 不是整个 Atlas Engineering Delivery Hub framework。
- 不包含真实生产凭证、kubeconfig、客户数据或内部截图。
- 当前不提供一键基础设施回滚。
- Maintenance 阶段事件路由和发布后自动化仍是计划/TBD。

## 跨团队复用价值

它复用的是一套发布运行壳：

- **Input 契约：** stage、release identifier、scope、task list、owner、expected output、外部执行元数据。
- **Execution 契约：** 手动或 AUTO 执行，并带有 owner/admin 控制。
- **Output 契约：** 任务结果、外部链接、决策、状态、审计和 release-flow 状态。
- **Validation 契约：** 人工评审、状态重算、访问检查和追踪链。

即使不同团队的 Jenkins job、Ansible template、iSeries command、任务名称和发布证据不同，也可以复用这个工作区模型。

## 已交付材料

- [Deployment 文档索引](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment Pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Framework README](../README.md)
- [中文 README](../README.zh-CN.md)
- [贡献指南](../CONTRIBUTING.md)
- [M6 生命周期定位图](assets/atlas-deployment-lifecycle-positioning.svg)
- [Deployment 工作流图](assets/atlas-deployment-tool-workflow.svg)
- [上下游关系图](assets/atlas-deployment-upstream-downstream.svg)
- [脱敏 mini output 样例](samples/atlas-deployment-tool-mini-output/README.md)
- [M6 SDD 追踪链](00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

## 演示故事

1. 从已经验证的候选包和测试证据开始。
2. 上传 `SIT` 阶段的脱敏发布任务工作簿。
3. 展示创建出的 Release Flow 和第一个可运行任务。
4. 执行手动任务或提交 AUTO 任务。
5. 记录或查看结果。
6. 通过人工评审门 Approve、Reject、Rerun 或 Skip。
7. 后续 `UAT` 和 `PROD` 复用同一个 workflow identifier。
8. 展示审计与执行历史作为可追踪发布记录。
9. 说明 IBM iSeries one-click release UTL 工作如何接入同一套 task/evidence/review pattern。

## 共建机会

- 增加脱敏发布模板样例。
- 扩展 IBM iSeries one-click release UTL 设计文档和样例。
- 改进 Jenkins 与 Ansible 适配器测试。
- 增加 Build/Testing 到 Deployment 的证据交接样例。
- 扩展回滚交接和发布后复盘文档。
- 加强 Markdown、Mermaid 和 SDD 验证脚本。

## 安全边界

- 当前发布推进仍要求人工审批。
- 凭证不得进入文档、样例、截图或提交的工作簿。
- 不提交真实环境名称和客户数据。
- 适配器贡献必须走配置和密钥管理路径。
- 回滚和 one-click 能力描述必须与已实现或明确文档化的设计行为一致。

## 链接

- [Framework README](../README.md)
- [Framework 提交材料](open-collaboration-submission.md)
- [Deployment 文档索引](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment Pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [贡献指南](../CONTRIBUTING.md)
- [当前实现基线](wwa-agent-workspace-hub-current-baseline.md)
