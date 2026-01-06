# Requirements Checklist: 008 Server/Client Separation & Communication

**Purpose**: 作为“需求文档单元测试”，检查 008 的 spec/plan/契约文档是否完整、清晰、无歧义且可验收（作者自查，标准严格度）。
**Created**: 2026-01-06
**Feature**:
- [spec.md](../spec.md)
- [plan.md](../plan.md)
- [data-model.md](../data-model.md)
- [contracts/worldgen-api.yaml](../contracts/worldgen-api.yaml)
- [quickstart.md](../quickstart.md)

## Requirement Completeness（需求是否写全）

- [x] **CHK001**：是否明确了本特性的范围边界仅为 WorldGen 闭环，并在文档中有“显式排除项”？[Completeness]（Spec §Out of Scope）
- [x] **CHK002**：是否明确“客户端不得直接调用生成器/不得持有权威对象引用”的约束，并指出适用范围（新游戏、进入世界等）？[Completeness]（Spec §FR-001）
- [x] **CHK003**：是否明确“HTTP（局域网可访问）+ JSON”是 MVP 的通讯形态（不是可选项）？[Completeness]（Spec §FR-002 / Clarifications）
- [x] **CHK004**：是否明确 `schemaVersion` 的来源位置（响应/快照）以及客户端的校验行为要求？[Completeness]（Spec §FR-002 / SC-002）
- [x] **CHK005**：是否明确成功响应必须包含哪些“最小可渲染信息”（边界、tile 列表、统计、星系结构）？[Completeness]（Spec §FR-003 / Data Model §WorldSnapshot）
- [x] **CHK006**：是否明确失败响应必须包含哪些字段（errorCode/messageKey/details）以及各字段用途？[Completeness]（Spec §FR-006 / Data Model §ErrorEnvelope）
- [x] **CHK007**：是否定义了局域网可访问但无认证的“风险收敛要求”（提示/可配置暴露范围等），而不是只写一句“无认证”？[Completeness]（Spec §Edge Cases / Plan §Research Decision 4）
- [x] **CHK008**：是否明确 seed 的权威规则：输入（seedText）、输出（seedValue 回填）与用途（确定性验证）？[Completeness]（Spec §FR-004 / Key Entities / Clarifications）

## Requirement Clarity（需求是否清晰无歧义）

- [x] **CHK009**：`schemaVersion` 是否有明确格式示例或命名约定，避免双方各写各的？[Clarity][Gap]（Spec §FR-002 / Contracts）
- [x] **CHK010**：是否明确“默认地图规模”的定义来源（例如具体 presetId），否则 SC-005~SC-007 的验收对象不清晰？[Clarity][Gap]（Spec §SC-005~SC-007）
- [x] **CHK011**：`StartNewGameRequest.seedText` 的空值/空串含义是否明确（使用默认、随机、还是拒绝）？[Clarity][Gap]（Data Model §StartNewGameRequest）
- [x] **CHK012**：请求参数的 clamp/修正规则是否明确：服务端是“自动修正并回传最终值”还是“直接 400 拒绝”？[Clarity][Gap]（Spec §Edge Cases / FR-006）
- [x] **CHK013**：错误码 `errorCode` 与 `messageKey` 的命名约定是否明确（例如枚举空间/前缀），避免散乱？[Clarity][Gap]（Data Model §ErrorEnvelope）
- [x] **CHK014**：局域网“可访问”是否明确监听范围（0.0.0.0/某网卡/IP 白名单），以及该配置是否属于本特性需求？[Clarity][Gap]（Spec §FR-002 / Edge Cases）

## Requirement Consistency（文档之间是否一致）

- [x] **CHK015**：Spec/Plan/Contracts 是否一致描述传输层为“HTTP（局域网可访问）+ JSON”，避免一处写 localhost、一处写 LAN？[Consistency]（Spec §FR-002 / Plan §Summary / Contracts §servers）
- [x] **CHK016**：Spec 中的实体名称与 data-model.md 中的字段是否一致（命名、可选性、含义）？[Consistency]（Spec §Key Entities / data-model.md）
- [x] **CHK017**：Spec 的 FR-003（快照必须含完整星系结构）是否与 OpenAPI schema 一致体现（starSystem/stars/planets）？[Consistency]（Spec §FR-003 / Contracts §WorldSnapshot）
- [x] **CHK018**：Spec 的错误处理要求（messageKey 本地化）是否与 quickstart 的错误路径验证描述一致？[Consistency]（Spec §FR-006 / quickstart.md §错误路径验证）
- [x] **CHK019**：Spec 的 Out of Scope 是否与 plan 的 Phase 0/1/2 规划一致（没有在 plan 引入实时同步/认证等）？[Consistency]（Spec §Out of Scope / plan.md）

## Acceptance Criteria Quality（验收标准是否可验证）

- [x] **CHK020**：P1 用户故事的验收场景是否能在“无客户端渲染”条件下独立验证，并说明验证口径（例如响应字段存在性/统计一致性）？[Measurability]（Spec §User Story 1）
- [x] **CHK021**：确定性验收是否明确“比较对象”（至少 stats 与 seedValue），以及允许的容差/不允许差异项？[Measurability][Gap]（Spec §FR-004 / SC-003）
- [x] **CHK022**：性能门槛是否明确测量点（从请求发出到响应完成？是否含网络传输？客户端计时起止点？）[Measurability][Gap]（Spec §SC-005~SC-007）
- [x] **CHK023**：响应大小门槛（<=20MB）是否明确以“HTTP body 字节数/JSON 文本大小”为准？[Measurability][Gap]（Spec §SC-007）

## Scenario Coverage（场景覆盖是否充分）

- [x] **CHK024**：是否定义了服务端不可用/连接失败时客户端 UX（提示、返回、重试）要求，而不是只列为边界情况？[Coverage][Gap]（Spec §Edge Cases）
- [x] **CHK025**：是否定义了“生成耗时较长”的加载态/取消行为（客户端与服务端各自期望）？[Coverage][Gap]（Spec §Edge Cases）
- [x] **CHK026**：是否覆盖 schemaVersion 不匹配的用户体验：提示文案（messageKey）、回退路径（返回菜单/退出）？[Coverage][Gap]（Spec §Edge Cases / FR-006）

## Edge Case Coverage（边界条件是否可落地）

- [x] **CHK027**：是否明确“超时/过大快照”的处理策略是：返回错误（ErrorEnvelope）还是部分成功（降级快照）？[Edge Cases][Gap]（Spec §Edge Cases）
- [x] **CHK028**：是否明确当星系结构异常（例如 stars=0 或 >3）时的处理（拒绝/修正/日志）？[Edge Cases][Gap]（Data Model §StarSystemSnapshot constraints）
- [x] **CHK029**：是否明确当 tiles 数量为空或 boundsRadius=0 时客户端行为？[Edge Cases][Gap]（Data Model §WorldSnapshot）

## Non-Functional Requirements（非功能性需求是否明确）

- [x] **CHK030**：是否将“局域网可访问但无认证”的限制清晰标记为开发/测试用途，并避免误导为生产可用？[Clarity]（Spec §Out of Scope / Plan §Research Decision 4）
- [x] **CHK031**：是否对可观测性提出最低要求（例如服务端记录生成耗时与快照大小，便于满足 SC-005~SC-007 的验收）？[Completeness][Gap]（Spec §Edge Cases / Plan §Research）
- [x] **CHK032**：是否对资源/性能（避免帧内分配、解析大 JSON 的内存压力）在需求层给出“必须/应该”的约束或提示？[Completeness][Gap]（Constitution §性能与资源管理；Spec/Plan）

## Dependencies & Assumptions（依赖与假设是否写明）

- [x] **CHK033**：是否明确本特性新增 `server` 模块属于计划的一部分（依赖项），并与端侧分离目标一致？[Completeness]（plan.md §Structure Decision）
- [x] **CHK034**：是否明确 JSON 编解码与 HTTP 容器的选择属于实现决策，但不影响契约的稳定性（契约优先）？[Clarity]（research.md / contracts）
- [x] **CHK035**：是否记录了“默认端口/host 的约定与可配置性”，避免开发环境不一致？[Clarity][Gap]（Contracts §servers / research.md）

## Ambiguities & Conflicts（仍可能导致返工的点）

- [x] **CHK036**：Spec 是否明确了 `schemaVersion` 的具体值（例如 `world_snapshot_v1`），否则很难写出一致的断言与回退逻辑。[Ambiguity][Gap]（Spec §FR-002 / Contracts）
- [x] **CHK037**：Spec 是否明确成功响应中的 `schemaVersion` 与快照版本是否必须一致，还是允许独立版本？[Ambiguity][Gap]（Spec/Contracts）
- [x] **CHK038**：Spec 是否明确“局域网可访问”的最低安全提示（例如 UI 需要提示未启用认证），避免后续实现遗漏。[Ambiguity][Gap]（Spec §Edge Cases / FR-006）

## Notes

- 本清单测试的是“需求是否写得好”，不是测试实现。
- 标记为 **[Gap]** 的条目表示当前文档可能需要补充/澄清；如果你希望在进入 `/speckit.tasks` 前一次性补齐，我可以用 `/speckit.clarify` 的方式把这些点写回 spec。
