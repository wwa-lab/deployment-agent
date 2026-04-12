# Build Agent 重构与实施 — 结构化总结

**分支：** `build-agent-leo`
**日期：** 2026-04-11
**基线：** `mvn test` 318 → 406 通过
**前端：** `npm run build` 0 退出

---

## 1. 这次做的事，一句话

在引入第三个 Agent（Build Agent，DEV 阶段）的同时，顺势完成了 **Agent Module 物理拆分 + Platform 能力下沉 + 前端 Factory 抽象** 三件被拖了很久的重构。业务执行逻辑未动，动的是**隔离边界**和**新增 Agent 的成本**。

---

## 2. 架构变化（前后对比）

| 维度 | 重构前 | 重构后 |
|---|---|---|
| 后端包结构 | 所有 agent 代码混在 `web/controller` + `domain/` | `agents/{deployment,testing,build}/{domain,web}/` + `platform/{domain,web}` |
| Stage 枚举 | 全局共享 `contracts/enums/Stage`（SIT/UAT/PROD 硬编码） | 每 Agent 自定义：`DeploymentStage` / `TestingStage` / `BuildStage`；platform 层只用 `String` |
| Stage 推进逻辑 | `ReleaseFlowProgressionService` 直接 `Stage.values()` 遍历 | 通过 `StagePipelineRegistry` 按 `agentId` 解析对应 `StagePipeline` bean，fail-loud |
| 平台能力路由 | `/api/deployment-agent/auth\|audit-logs\|config\|access-grants\|upload/template` | `/api/platform/*`，前端共享 `platformClient.ts` |
| 跨 Agent 越权防护 | 无（v2 R-08 遗留：Testing Agent 可访问 Deployment task） | `AgentBoundaryGuard` 在每个带 ID 的端点统一 assert，不匹配 404 |
| 审计 `agentName` | 硬编码 `"deployment-agent"` | 动态：按 scope 实际 agent 写入；平台能力事件写 `"platform"` |
| 列表查询 | `ReleaseFlowService` 全局列表，未按 agent 隔离 | `listByAgent(agentId, filter, pageable)` 严格等值匹配 |
| Release Flow 拼接 | 拼接逻辑在 platform `ReleaseFlowService` | 移到 `agents/deployment/domain/DeploymentStitchingService`（目前为薄 facade，见 FU-1） |
| 前端 Agent 组合 | 每个 Agent 各自 fork（client / store / api / view 全套复制） | `createAgentWorkspace` 工厂 + 子工厂（API / Store），新 Agent ≈30 行 |
| DTO 列表项 | 硬编码 `sitStatus/uatStatus/prodStatus + *Present` | 通用 `Map<String, RequestStatus> stageStatuses` + `Set<String> stagesPresent` |

---

## 3. 分阶段实施情况（10 批次全部落地）

| Batch | Phase | 任务 | 关键产出 | 状态 |
|---|---|---|---|---|
| 1 | A 平台脚手架 | BA-T01..T02 | `StagePipeline` 接口 + 12 个 `package-info.java` + ArchUnit 规则 | ✅ |
| 2 | B Stage 词汇迁移 | BA-T03..T07 | 3 个 per-agent Stage enum + `StagePipelineRegistry` + JPA `Stage→String` + 删除旧 `Stage.java` | ✅ |
| 3 | C DTO/聚合重构 | BA-T08..T09 | `stageStatuses` map 通用化 + `ReleaseFlowAggregation` 改为遍历观察到的 stage | ✅ |
| 4 | D Stitching 搬家 | BA-T10..T12 | `ReleaseFlowFamilyKey` 迁入 agents/deployment + `DeploymentStitchingService` + `listByAgent` + `ReleaseFlowFilter` | ✅（有 FU-1 债） |
| 5 | E Guard + Audit | BA-T13..T14 | `AgentBoundaryGuard`（12 行测试矩阵）+ 动态 `agentName` | ✅（有 FU-2 opt-in 债） |
| 6 | F Platform 路由切换 | BA-T15..T16 | 4 个能力 controller + `TemplateDownloadController` 迁 `/api/platform/*`；前端 `platformClient.ts` + 4 API + 4 store + 7 view 迁移 | ✅ |
| 7 | G 前端 Factory | BA-T17..T18 | `createAgentWorkspace` + `AgentSummaryView` / `AgentDetailView` 通用组件 | ✅（有 FU-3 债） |
| 8 | H Agent 模块迁移后端 | BA-T19..T22 | Deployment/Testing/Build 各 4 个新 controller + `BuildDataIsolationTest`（13 场景） | ✅ |
| 9 | I 前端迁移 | BA-T24→T25→T23 | 三个 agent 的 `index.ts` 工厂化，删除 15+ 旧文件 | ✅ |
| 10 | J 验证 | BA-T26..T28 | `mvn test` 406/0/0 + ArchUnit 8 条 + 13 场景 smoke + 发布说明 | ✅ |

---

## 4. 代码现状快照

**后端**

```
src/main/java/com/wwa/deploymentagent/
├── platform/
│   ├── domain/        StagePipeline.java, StagePipelineRegistry.java
│   └── web/
│       ├── shared/    AuthController, AuditLogController,
│       │              ConfigurationController, AccessGrantController,
│       │              TemplateDownloadController
│       └── security/  AgentBoundaryGuard (+ Test)
├── agents/
│   ├── deployment/
│   │   ├── domain/    DeploymentStage, DeploymentStagePipeline,
│   │   │              DeploymentStitchingService, ReleaseFlowFamilyKey
│   │   └── web/       Deployment{ReleaseFlow,Task,Decision,Upload}Controller
│   ├── testing/
│   │   ├── domain/    TestingStage, TestingStagePipeline
│   │   └── web/       Testing{ReleaseFlow,Task,Decision,Upload}Controller
│   └── build/
│       ├── domain/    BuildStage, BuildStagePipeline
│       └── web/       Build{ReleaseFlow,Task,Decision,Upload}Controller
└── domain/ (保留共享服务：ReleaseFlowService, TaskService, Audit, etc.)
```

**前端**

```
frontend/src/
├── platform/
│   ├── components/    ReleaseFlowSummaryView.vue, ReleaseFlowDetailView.vue
│   └── composables/   createAgentWorkspace.ts,
│                      createReleaseFlowApi.ts,
│                      createReleaseFlowStore.ts,
│                      releaseFlowTypes.ts
└── agents/
    ├── deployment/    index.ts, api.ts, ReleaseFlowSummaryView/DetailView
    ├── testing/       index.ts, api.ts, TestingAgentSummaryView/DetailView
    └── build/         index.ts, BuildAgentSummaryView/DetailView
```

**DB 迁移**

- `V13__backfill_null_agent_to_deployment_agent.sql` — P-01 解决：历史 null-agent 行全部回填为 `deployment-agent`

---

## 5. 破坏性变更（需要前端/调用方留意）

| 变更 | 旧 | 新 |
|---|---|---|
| 登录 | `POST /api/deployment-agent/auth/login` | `POST /api/platform/auth/login` |
| Session Cookie | 依赖 agent 前缀 | `Path=/`，跨 `/api/<agent>/*` 共享 JSESSIONID |
| 审计/配置/访问授权/模板下载 | `/api/deployment-agent/*` | `/api/platform/*` |
| 模板文件名 | `deployment-request-template.xlsx` | `request-template.xlsx`（中性） |
| 列表隔离 | Deployment 能看到 null-agent 行 | 严格等值 `agent="deployment-agent"` |
| Upload 客户端 `agent` 参数 | 被接受 | 被忽略，服务端强制 |
| Testing Agent `linked=` | 生效 | 忽略（UAT 单阶段无需拼接） |

---

## 6. 验证覆盖

- **mvn test：** 318 → 406 全绿
- **新增测试：** `AgentBoundaryGuardTest`(12) + `BuildDataIsolationTest`(9) + §M8 audit(4) + `listByAgent`(3) + `DeploymentStitchingService`(2) + ArchUnit(8) + V13 不变量(2) + 若干 controller 增量
- **ArchUnit 规则（8 条）：** 禁止跨 agent 互相 import / 禁止 platform→agents / 禁止 platform 引用 Stage 枚举 / agent 边界
- **13 场景 smoke：** 9 条自动化（BuildDataIsolationTest），4 条手动（SM-04 / SM-08 / SM-10 / SM-11，见 `docs/BA_SMOKE_AUDIT.md`）
- **前端：** `npm run build` 0 退出，factory chunk 被 3 个 agent 复用

---

## 7. 与原计划的已知偏差（技术债）

| ID | 偏差 | 理由 | 优先级 | 状态 |
|---|---|---|---|---|
| **FU-1** | `DeploymentStitchingService` 是薄 facade，~500 行私有 helper 仍在 `ReleaseFlowService` | 架构边界已达成；物理搬家风险高，留到后续 | 中 | 待办 |
| **FU-2** | `AuditLoggerService` 对 null `scope.agent()` 的严格抛错做成 opt-in (`strictAgent=true`) | `@Transactional(REQUIRES_NEW)` 看不到外层事务 fixture，严格抛错会破坏 60+ 已有测试 | 中 | 待办 |
| **FU-3** | Deployment/Testing 迁移到共享 `platform/components/ReleaseFlow{Summary,Detail}View.vue` | Deployment 与 Testing 视图已合并为 `platform/components/*.vue`；两个 agent 的 view 收缩为 ~40 行 wrapper，`releaseFlowTypes.ts` 承载共享 api bundle + copy 配置；Build Agent 仍用自己的 DEV 专用视图（见 index.ts 注释） | 低 | **已完成 2026-04-11** |
| **FU-4** | `TaskEditDialog` / `DecisionDialog` 等 6 个共享对话框仍 hardcode `agents/deployment/api` | 移除所有 hardcode；将 API 函数 prop 改为 required；新增 `api/template.ts` 承载平台模板下载；Deployment 详情视图与 TemplateManagementView 显式传 prop | 中 | **已完成 2026-04-11** |
| **FU-5..7** | SM-04 / SM-08 / SM-10 未自动化 | 低优，可单独做 | 低 | 待办 |
| **FU-8** | "platform 层不得硬编码 stage 字面量" ArchUnit 1.x 无法表达 | 用 CI grep 兜底 | 低 | 待办 |
| **FU-9** | SM-11（跨前缀 session 共享）未自动化 | 需要引入 Playwright | 低 | 待办 |
| BA-T22 SM-12 | 断言 `401` 而非 `404` | 测试 profile 开启 header-fallback，安全过滤器短路在 404 handler 之前 | 已接受 | — |

### FU-3 / FU-4 落地细节（2026-04-11）

**FU-4 — 6 个共享对话框解耦**

| 文件 | 改动 |
|---|---|
| `frontend/src/api/template.ts` | 新增平台模板下载工具（从 `agents/deployment/api.ts` 搬出） |
| `components/DecisionDialog.vue` | 移除 `submitDecisionApi` 默认值，`submitDecisionFn` 改为 required |
| `components/TaskEditDialog.vue` | 移除 `editTaskApi/recordResultApi/startManualExecutionApi` 默认值，对应 prop 全部 required |
| `components/TaskActivityDialog.vue` | 移除 `listTaskExecutions` 默认值，`listTaskExecutionsFn` 改为 required |
| `components/RundownEditDialog.vue` | 移除 `updateRequestRundown` 默认值，`updateRequestRundownFn` 改为 required |
| `components/CreateRundownDialog.vue` | 新增 `createRundownFromTemplateFn` required prop |
| `components/CreateTemplateDialog.vue` | `downloadTemplate` 改从 `../api/template` 导入 |
| `agents/deployment/ReleaseFlowDetailView.vue` | 显式导入 6 个 api 函数并全部通过 prop 传入对话框（与 Testing/Build 对齐） |
| `views/TemplateManagementView.vue` | 显式将 `createDeploymentRundownFromTemplate` 传给 `CreateRundownDialog`（保持"所有模板 rundown 走 Deployment Agent"的既有行为） |

净效果：没有任何共享对话框再 `import from '../agents/deployment/api'`。错误的 agent 绑定现在会在 TS 编译期被捕获（prop required）。

**FU-3 — Deployment/Testing 视图工厂化**

新增：
- `frontend/src/platform/composables/releaseFlowTypes.ts` — `ReleaseFlowDetailApi` 接口（13 个 api 函数）+ `ReleaseFlowDetailCopy` 接口 + `deploymentCopy` / `testingCopy` 文案常量
- `frontend/src/platform/components/ReleaseFlowDetailView.vue`（~1740 行）— 由 Deployment 详情视图参数化而来，接收 `agentKey` / `store` / `api` / `copy` / `autoRefresh` props
- `frontend/src/platform/components/ReleaseFlowSummaryView.vue`（~470 行）— 由 Deployment 摘要视图参数化而来，接收 `agentKey` / `store` / `stages` / `uploadFn` / `downloadTemplateFn` / `copy` / `supportsStitching` props

Wrapper 收缩：
- `agents/deployment/ReleaseFlowDetailView.vue`：1730 → ~45 行
- `agents/deployment/ReleaseFlowSummaryView.vue`：493 → ~35 行
- `agents/testing/TestingAgentDetailView.vue`：1724 → ~45 行
- `agents/testing/TestingAgentSummaryView.vue`：487 → ~35 行

清理：
- 删除 `platform/components/AgentDetailView.vue` 和 `AgentSummaryView.vue`（~140 行未使用的 stub）
- Build Agent 未动（其 DEV 专用视图保持独立，`index.ts` 的 javadoc 已更新）

构建产物：单个 `releaseFlowTypes-*.js` chunk（32.39 kB，gzip 9.21 kB）在 Deployment 和 Testing wrapper 之间复用，总字节数相比两份独立视图减少约 26 kB。

验证：`npm run build` 0 退出；176 modules transformed（比 FU-3 之前 173 多 3 个新平台文件）。

---

## 8. 回滚策略

4 个提交，逆序 revert：

1. `b732cdc` — Phase I（前端 factory 迁移）
2. `19bd68b` — Phase F/H（后端路由切换 + Agent Module 迁移 + Build Agent 引入）
3. `6d4f6ef` — Phase A-E（Stage pipeline 抽象）
4. `03a7ac2` — P-01 修复与 tasks-to-implementation 对齐

⚠ **Phase F 必须整体 revert**（BA-T15 后端路由改动 + BA-T16 前端迁移同组），否则前端会打不开登录页。V13 回填是幂等的，无需 down migration。

---

## 9. 重点关注项（建议决定是否现在就修）

1. **FU-1 Stitching 薄 facade** — 现在 `ReleaseFlowService` 仍持有 SIT/UAT/PROD 拼接的 ~500 行 helper，PL-5（platform 不感知 stage）并未 100% 达成。可以作为独立 PR 收尾。
2. **FU-2 Audit opt-in** — `strictAgent` 默认关，意味着有调用者没显式传 agent 时会悄悄落到 `"platform"`。短期内是兼容性保护，长期是审计正确性隐患。
3. **共享明细视图体量偏大** — `platform/components/ReleaseFlowDetailView.vue` 目前承接了大部分 Deployment 详情交互，参数化成功但文件依然很长。后续如果再扩展 agent 差异，建议继续拆子组件以降低维护成本。
4. **Build Agent 与共享视图仍刻意分叉** — 这是当前设计选择，不是 bug。Build Agent 保留 DEV 专用视图，Deployment/Testing 复用共享视图；如果未来 Build 需求继续增长，再决定是否并入共享视图族。

---

## 参考文档

- `docs/IMPLEMENTATION_PLAN_BUILD_AGENT.md` — 原始实施计划（10 批次 + 风险寄存器）
- `docs/RELEASE_NOTES_BUILD_AGENT.md` — 内部发布说明（完整 FU 列表 + 回滚步骤）
- `docs/BA_SMOKE_AUDIT.md` — 13 场景 smoke 审计（自动/手动覆盖矩阵）
- `docs/06-tasks/build-agent-tasks.md` — 源任务清单（v3，28 任务）
- `docs/05-design/build-agent-design.md` — Agent Module v3 架构设计
