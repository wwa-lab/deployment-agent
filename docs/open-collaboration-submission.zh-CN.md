# 开放协作提交材料：Atlas Engineering Delivery Hub

## 摘要

Atlas Engineering Delivery Hub 是一个面向端到端 SDLC 交付治理的 Framework 参赛项目。它提供可复用的生命周期模型、门控模型、平台核心、Agent 模块扩展模式、文档链和采用样例，帮助团队进行受控、可追踪、AI 友好的软件交付。

## 参赛类别

**Framework**

本项目符合 Framework 类别，因为它不是一个单点工具，也不只覆盖一个阶段。它定义了一套可在 Planning、Estimation、Discovery、Build、Testing、Deployment、Maintenance 全流程复用的工程交付方法。

## 解决的问题

很多交付团队存在流程割裂：

- 规划证据分散；
- 设计意图与实现脱节；
- 构建、测试、部署状态难以统一比较；
- 人工评审没有稳定记录；
- AI Agent 可以参与，但缺少稳定流程表面；
- 审计和权限上下文经常事后补齐。

Atlas Engineering Delivery Hub 通过统一框架提供生命周期可视化、流程控制、质量验证、可追溯性和持续交付运行能力。

## 框架概念

框架由以下部分组成：

- **Seven Mountains SDLC：** Planning、Estimation、Discovery、Build、Testing、Deployment、Maintenance。
- **Seven Gates Flow：** 每个 Mountain 在进入下游前都有一个阶段门。
- **I-E-O-V：** Input、Execute、Output、Validate，是每个阶段门的通用契约。
- **Platform Core：** 共享认证、访问治理、审计、配置、上传、任务和 Release Flow 服务。
- **Agent Modules：** 阶段型工作区和未来子能力。
- **SDD Traceability：** 从需求、用户故事、规格、架构、设计到任务和验证证据的链路。

## 可复用资产

- 英文和中文框架 README。
- 开放协作提交材料和 Pitch。
- 覆盖文档、模块、验证和安全的贡献指南。
- Mermaid 源文件和 SVG 图表。
- 合成采用样例。
- SDD 切片和追踪索引。
- 现有 WWA Agent Workspace Hub 实现基线。
- 可扩展到 Build、Testing、Deployment 以及未来阶段的 Agent Module 模式。

## 为什么 AI 友好

框架为人和 AI Agent 提供稳定工作表面：

- 清晰阶段名称和职责；
- 明确 I-E-O-V 输入输出；
- 实现前的 SDD 文档链；
- 简洁贡献规则和验证门；
- 结构化审计与任务历史；
- 不含敏感数据、可复制的样例。

AI Agent 可以在某个阶段内工作，同时框架仍保留人工评审、负责人、验证和审计。

## 采用路径

1. 阅读 [README](../README.md) 和 [文档索引](atlas-engineering-delivery-hub-index.md)。
2. 将团队交付流程映射到 Seven Mountains。
3. 为每个阶段门定义 I-E-O-V 证据。
4. 选择适用的当前工作区：Build Agent、Testing Agent、Deployment Agent，或仅采用文档模板。
5. 通过 SDD 文档提出新的阶段子能力。
6. 按贡献指南和轻量检查完成验证。

## 交付治理支持

Atlas Engineering Delivery Hub 通过以下方式支持端到端交付治理：

- 生命周期覆盖可视化；
- 范围化访问控制和委托管理；
- 带应用、组和 Agent 上下文的审计记录；
- 任务执行历史和评审决策；
- 从需求到任务的 SDD 链路；
- 可复用的框架样例和贡献规则。

## 子能力模型

子能力接入一个或多个阶段。每个子能力需要说明所属阶段、I-E-O-V 契约、验证证据、集成边界和安全规则。

Atlas Phoenix Lens 是 Discovery 阶段示例。它可以在更大的 Atlas Engineering Delivery Hub 框架内支持需求和发现智能，但不是父级项目。

## 当前范围与路线图

当前实现：

- WWA Agent Workspace Hub；
- Build Agent；
- Testing Agent 基线；
- Deployment Agent；
- Agent Contribute Dashboard；
- 平台访问、审计、配置、上传和任务服务。

路线图：

- Discovery 和 Maintenance 运行时能力；
- 更多框架模板和门控证据样例；
- 更强的文档与 SDD 验证自动化；
- 经审批后的脱敏视觉素材。

## 链接

- [框架 README](../README.md)
- [中文 README](../README.zh-CN.md)
- [Pitch](atlas-engineering-delivery-hub-pitch.md)
- [贡献指南](../CONTRIBUTING.md)
- [合成采用样例](samples/atlas-framework-adoption-sample.md)
- [SDD 追踪](00-context/atlas-engineering-delivery-hub-traceability.md)

