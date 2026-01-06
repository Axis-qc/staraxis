# Tasks: 星系生成系统增强（009）

**Input**: Design documents from `/specs/009-galaxy-system-gen/`
**Prerequisites**: plan.md（required）, spec.md（required）, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: 本特性未要求 TDD，但为了满足确定性与拓扑约束的可验收性，将以 JUnit 形式补充必要的回归/属性测试任务。

**Organization**: 任务按用户故事分组，保证每个用户故事可独立完成与验证。

## Format

所有任务必须严格遵循：

- [ ] `T### [P?] [US?] 描述（含文件路径）`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认工程基线与新增包结构，避免跨模块/跨层误改。

- [ ] T001 梳理并记录现有世界生成入口与数据模型位置（core/shared）（`core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`, `core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`, `shared/src/main/java/com/staraxis/game/shared/world/stellar/*.java`）
- [ ] T002 创建本特性新增包目录（仅建包，不引入实现）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/`, `shared/src/main/java/com/staraxis/game/shared/world/stellar/surface/`, `core/src/main/java/com/staraxis/game/core/world/stellar/orbit/`, `core/src/main/java/com/staraxis/game/core/world/stellar/surface/`）
- [ ] T003 [P] 在 `specs/009-galaxy-system-gen/quickstart.md` 中补充“实现完成后的最小验证命令”（Gradle 任务名，不写 shell 组合）（`specs/009-galaxy-system-gen/quickstart.md`）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共享的基础数据结构与通用校验/诊断能力。

- [ ] T004 新增轨道精度枚举（低/中/高）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/OrbitPrecisionLevel.java`）
- [ ] T005 新增网格分辨率枚举（低/中/高）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/surface/MeshResolutionLevel.java`）
- [ ] T006 新增轨道中心引用类型（支持：主星、共同质心/子系统节点）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/OrbitCenterRef.java`）
- [ ] T007 新增轨道参数数据结构（偏心率/尺度/相位/可选倾角，不影响本轮路径输出）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/Orbit.java`）
- [ ] T008 新增轨道路径数据结构（闭合采样点 + 精度档位）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/OrbitPath.java`）
- [ ] T009 复用现有 2D 向量数据结构 `Vector2`（用于轨道采样输出；避免依赖 gdx graphics）（`shared/src/main/java/com/staraxis/game/shared/model/Vector2.java`）
- [ ] T010 新增球面网格单元与网格结构（tileType=hex/pent，neighbors 双向一致）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/surface/SurfaceTile.java`, `shared/src/main/java/com/staraxis/game/shared/world/stellar/surface/PlanetSurfaceMesh.java`）
- [ ] T011 更新行星数据模型以关联轨道与表面网格引用（不引入渲染对象）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/Planet.java`）
- [ ] T012 更新恒星/恒星系统数据模型以表达层级归属（双星共同质心/子系统节点的最小表达）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/Star.java`, `shared/src/main/java/com/staraxis/game/shared/world/stellar/StarSystem.java`）
- [ ] T013 新增“生成诊断信息”载体（用于轨道冲突修复失败原因回传）（`shared/src/main/java/com/staraxis/game/shared/world/stellar/WorldGenDiagnostics.java`）
- [ ] T014 [P] 新增轨道冲突判定工具（纯逻辑：基于轨道尺度/近似最近距离阈值）（`core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitConflictDetector.java`）
- [ ] T015 [P] 新增轨道参数合法性校验工具（e 范围、尺度 > 0、非 NaN/无穷）（`core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitValidator.java`）
- [ ] T016 [P] 新增球面网格拓扑校验工具（五边形=12、邻接双向一致、仅 hex/pent）（`core/src/main/java/com/staraxis/game/core/world/stellar/surface/SurfaceMeshValidator.java`）

**Checkpoint**: 基础数据结构与校验工具就绪，US1/US2/US3 可并行推进（按接口约定）。

---

## Phase 3: User Story 1 - 星系与多恒星生成可用 (Priority: P1) MVP

**Goal**: 在现有 `DefaultWorldGenerator`/`StellarGenerator` 基础上扩展：多恒星层级归属 + 椭圆轨道参数 + 冲突修复/重试（最多 3 次）+ 同平台同版本确定性。

**Independent Test**: 运行 `:core:test`，新增/扩展测试覆盖：

- 双星共同质心存在且可被行星轨道引用
- 在测试用例中至少构造 1 个偏心率不为 0 的轨道/行星输入，用于验证椭圆轨道相关数据链路可用
- 冲突修复触发时不会无限循环，超过 3 次返回诊断信息

### Implementation for User Story 1

- [ ] T017 [US1] 在 `StellarGenerator` 中引入“星系层级归属”的最小生成流程（双星共同质心节点 + 环双星行星归属）（`core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`）
- [ ] T018 [P] [US1] 实现轨道参数采样器（生成椭圆轨道参数：eccentricity/scale/phase；倾角字段可填但不参与输出）（`core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitParamSampler.java`）
- [ ] T019 [US1] 在行星生成时为每颗行星分配 `Orbit` 与 `OrbitCenterRef`（绕主星或绕共同质心）（`core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`）
- [ ] T020 [US1] 在 `generateStarSystem` 增加“冲突检测→自动修复→重试上限 3 次”控制流，并在失败时填充 `WorldGenDiagnostics`（`core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`）
- [ ] T021 [US1] 更新 `DefaultWorldGenerator` 的统计/日志输出，包含诊断摘要（不要求写入 UI）（`core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`）

### Tests for User Story 1

- [ ] T022 [US1] 扩展确定性测试断言：同平台同版本下同 seed 生成的轨道参数摘要一致（允许只比较离散摘要，不比较浮点逐位）（`core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java`）
- [ ] T023 [US1] 新增多恒星层级测试：双星系统存在共同质心节点且至少一颗行星归属为共同质心（`core/src/test/java/com/staraxis/game/core/world/StellarGeneratorHierarchyTest.java`）
- [ ] T024 [US1] 新增冲突修复测试：构造高冲突输入触发修复/重试，断言最大重试次数为 3 且失败有诊断（`core/src/test/java/com/staraxis/game/core/world/StellarGeneratorConflictTest.java`）

**Checkpoint**: US1 完成后，世界生成在 headless 下可生成多恒星层级与轨道数据，并满足确定性边界与修复策略。

---

## Phase 4: User Story 2 - 行星轨道可视化可验证 (Priority: P2)

**Goal**: 生成可被渲染层消费的 `OrbitPath`（闭合、低/中/高精度档位、形状一致），并在 LWJGL3 客户端用调试绘制展示轨道。

**Independent Test**:

- 运行 `:core:test` 中的轨道路径采样测试（闭合、档位差异、同档位确定性）
- 在 LWJGL3 运行时能开关轨道显示（无需最终 UI，允许调试键位）

### Implementation for User Story 2

- [ ] T025 [P] [US2] 实现开普勒椭圆在 XY 平面的轨道采样器（低/中/高档位）输出 `OrbitPath`（`core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitPathSampler.java`）
- [ ] T026 [US2] 为 `StarSystem` 提供“批量生成轨道路径描述”的服务入口（不依赖渲染）（`core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitPathService.java`）
- [ ] T027 [P] [US2] 在 LWJGL3 模块新增调试轨道渲染器（消费 `OrbitPath`，不反向依赖 core 内部实现）（`lwjgl3/src/main/java/io/staraxis/lwjgl3/debug/OrbitDebugRenderer.java`）
- [ ] T028 [US2] 在 `Lwjgl3Launcher` 增加“轨道显示开关”的输入与调用（`lwjgl3/src/main/java/io/staraxis/lwjgl3/Lwjgl3Launcher.java`）

### Tests for User Story 2

- [ ] T029 [US2] 新增轨道路径闭合性测试：采样输出首尾点闭合（允许误差阈值）且无 NaN/无穷（`core/src/test/java/com/staraxis/game/core/world/orbit/OrbitPathSamplerTest.java`）
- [ ] T030 [US2] 新增精度档位测试：低/中/高采样点数不同但形状摘要一致；同档位确定性一致（`core/src/test/java/com/staraxis/game/core/world/orbit/OrbitPathPrecisionTest.java`）

**Checkpoint**: US2 完成后，轨道路径可被生成并在客户端以调试绘制可视化。

---

## Phase 5: User Story 3 - 行星球面网格可生成与可查询 (Priority: P3)

**Goal**: 为行星生成球面六/五边网格（五边形固定 12），支持邻接查询与基础位置查询，并通过拓扑校验。

**Independent Test**: 运行 `:core:test`，网格拓扑测试通过：

- 五边形数量恒为 12
- 邻接双向一致
- 单元类型仅 hex/pent

### Implementation for User Story 3

- [ ] T031 [P] [US3] 实现球面六/五边网格生成算法（基于二十面体细分的 icosphere 拓扑，再构建 dual 网格），输出 `PlanetSurfaceMesh`（`core/src/main/java/com/staraxis/game/core/world/stellar/surface/PlanetSurfaceMeshGenerator.java`）
- [ ] T032 [US3] 在行星生成流程中按分辨率档位生成并挂载 `PlanetSurfaceMesh` 到行星对象（`core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`）
- [ ] T033 [US3] 在 `PlanetSurfaceMesh` 增加查询接口：按 `tileId` 取单元、按 `tileId` 取邻居列表、按球面方向向量查询单元；若命中边界则按 `tileId` 字典序最小规则返回（`shared/src/main/java/com/staraxis/game/shared/world/stellar/surface/PlanetSurfaceMesh.java`）

### Tests for User Story 3

- [ ] T034 [US3] 新增球面网格拓扑测试：五边形=12，邻接双向一致，tileType 限定（`core/src/test/java/com/staraxis/game/core/world/surface/PlanetSurfaceMeshTopologyTest.java`）
- [ ] T035 [US3] 新增分辨率档位测试：低/中/高的 tileCount 递增且同档位确定性一致（`core/src/test/java/com/staraxis/game/core/world/surface/PlanetSurfaceMeshResolutionTest.java`）

**Checkpoint**: US3 完成后，行星表面网格可生成、可校验、可查询。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 契约落地（可选调试 API）、性能、文档与验收脚本化。

- [ ] T036 [P] 将 `WorldGenDiagnostics` 的关键字段写入日志（避免泄露实现细节，提供可诊断性）（`core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`）
- [ ] T037 [P] 将 quickstart 中的“测试建议”与实际新增测试文件对齐更新（`specs/009-galaxy-system-gen/quickstart.md`）
- [ ] T038 [P] 对轨道路径采样与球面网格生成做一次性能基准记录（固定输入口径：`mapSizePresetId=small`、`seedValue=12345`、`starDensity=0.6`、`planetComplexity=0.5`、`nebulaRatio=0.2`；记录 `tileCount/galaxyTileCount/starCount/planetCount` 与耗时），对齐 `SC-002`（`core/src/test/java/com/staraxis/game/core/world/WorldGenPerformanceSmokeTest.java`）
- [ ] T039 [P] 实现 `openapi.yaml` 对应的 server 调试路由与 handler：`/world/generate`、`/star-system/{systemId}/orbit-paths`、`/planet/{planetId}/surface-mesh`（`server/src/main/java/com/staraxis/game/server/http/HttpRoutes.java`, `server/src/main/java/com/staraxis/game/server/http/WorldGenDebugHandler.java`）
- [ ] T040 确认所有新增逻辑均不引入 core 图形依赖，并通过 `:core:checkNoGraphicsDependencies`（`core/build.gradle` 已挂载）
- [ ] T041 [P] 新增配置加载行为回归测试：相同 `seedValue` 下修改 `WorldGenConfig`（例如 `starDensity` 或 `planetComplexity`）再次生成时统计摘要出现差异，以覆盖 `FR-022`（`core/src/test/java/com/staraxis/game/core/world/WorldGenConfigReloadTest.java`）

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2（基础数据结构与校验工具）
- Phase 2 完成后：
  - US1 可开始
  - US2/US3 可并行开始，但需以 US1 产出的数据结构为基线联调
- Polish 阶段依赖 US1/US2/US3 中你希望交付的集合

### User Story Dependencies

- **US1 (P1)**: 无依赖（基线能力，建议先完成）
- **US2 (P2)**: 依赖 US1 提供的轨道数据模型与生成结果（Orbit/OrbitCenterRef）
- **US3 (P3)**: 依赖 US1 提供的行星对象生成；网格算法可独立实现并在 US1 集成

### Parallel Opportunities

- Phase 2 中标记 [P] 的工具/校验类可并行开发
- US2 的 core 轨道采样与 LWJGL3 调试渲染可并行（通过 `OrbitPath` 契约对齐）
- US3 的网格生成算法与 US1 的多恒星/轨道生成可并行（通过 `PlanetSurfaceMesh` 契约对齐）

---

## Parallel Example: User Story 2

```text
Task: T025 [US2] core 侧实现 OrbitPathSampler（core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitPathSampler.java）
Task: T027 [US2] lwjgl3 侧实现 OrbitDebugRenderer（lwjgl3/src/main/java/io/staraxis/lwjgl3/debug/OrbitDebugRenderer.java）
```

---

## Implementation Strategy

### MVP First（只做 US1）

1. 完成 Phase 1 + Phase 2
2. 完成 US1（T017–T024）
3. 停止并验证：`core/src/test/java/...` 测试通过 + 生成日志可复现

### Incremental Delivery

- US1（数据生成）→ US2（轨道可视化）→ US3（球面网格）→ Polish

---

## Notes

- 任务描述中的路径是“目标落点”，实现时需先检索代码库避免重复（宪章要求）。
- 不要在 core 模块引入 `com.badlogic.gdx.graphics` 等图形依赖（已有 `checkNoGraphicsDependencies` 门禁）。
- 与终端相关的验证优先通过 Gradle 任务/测试完成。
