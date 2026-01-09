# Tasks: 015 - 真实比例六边形星区与内容分配

> 目标：生成一个可直接执行、按依赖顺序排列的任务清单，按 User Story（P1→P3）组织。
> 约束：严格 C/S 分离；数据驱动（JSON）；可复现（seed + HexCoord 派生随机）。

## Dependencies（用户故事完成顺序）

- US1（P1）为基础：必须先完成六边形星区网格与真实比例 worldCenter 计算
- US2（P2）依赖 US1：在已有星区集合上应用预设占用 + 剩余分配
- US3（P3）依赖 US1+US2：客户端消费快照渲染与 F3 调试验收

## Parallel execution examples

- US1 阶段：
  - [P] 可并行：实现 HexCoord→worldCenter 计算（shared） 与 快照字段补齐（shared snapshot）
- US2 阶段：
  - [P] 可并行：JSON schema/加载器（shared） 与 分配算法实现（shared）
- US3 阶段：
  - [P] 可并行：客户端 adapter（lwjgl3/client/world） 与 调试面板显示增强（lwjgl3/ui/view/debug）

---

## Phase 1: Setup（初始化/对齐）

- [x] T001 校验 015 相关设计文档齐全并在 tasks 中锁定输入路径：`specs/015-realistic-galaxy-sector-gen/spec.md`、`specs/015-realistic-galaxy-sector-gen/plan.md`
- [x] T002 在 `shared` 与 `lwjgl3` 中定位/确认现有 HexCoord、坐标系、UniverseSnapshot/SectorSnapshot 的具体实现位置（仅记录到任务实现注释，不做无关改动）

## Phase 1.5: Terminology Alignment (Refactoring for I1)

**Goal**: 将既有代码中的星区类型 `"galaxy"` 统一为权威术语 `"star-system"`，解决 CRITICAL 问题 I1。

- [x] T002a [Refactor] 更新 `shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/SectorTypes.java`：将 `GALAXY = "galaxy"` 重命名为 `STAR_SYSTEM = "star-system"`。
- [x] T002b [Refactor] 更新 `shared/src/main/java/com/staraxis/universegen/SectorGenerator.java`：将所有对 `"galaxy"` 的判断与返回，改为使用 `SectorTypes.STAR_SYSTEM`。
- [x] T002c [Refactor] 更新 `shared/src/main/java/com/staraxis/game/shared/world/WorldGenDefinitions.java`：将 `TILE_TYPES.put("galaxy", ...)` 修改为 `TILE_TYPES.put("star-system", ...)`，并同步更新对应的 `.properties` 文件。

---

## Phase 2: Foundational（所有故事共用的基础设施）

- [x] T003 定义/补齐 1ly 常量（km）与相关工具方法，放在 `shared/src/main/java/com/staraxis/universegen/`（例如 `CoordinateSystem` 或新 util 类，避免硬编码散落）
- [x] T004 定义 HexCoord 的稳定排序工具（q,r 字典序）放在 `shared/src/main/java/com/staraxis/universegen/`（或 HexCoord 所在包），供分配与快照输出排序复用
- [x] T005 [P] 实现 `seed + HexCoord` 派生随机的工具（hash64 + SplittableRandom），文件：`shared/src/main/java/com/staraxis/universegen/util/RandomUtil.java`（或等价位置）

---

## Phase 3: User Story 1（P1）- 生成六边形星区网格与真实比例 worldCenter

**Goal**: 开局生成六边形星区拓扑；星系大小为半径 R；计算 worldCenter（km），满足相邻中心距误差 ≤ 1%。

**Independent test**:
- 给定 R=8，生成星区数 N=217
- 任意相邻 HexCoord 的 worldCenter 距离满足 SC-002

### Tasks

- [x] T006 [US1] 实现“按半径 R 生成六边形网格 HexCoord 集合”的方法，文件：`shared/src/main/java/com/staraxis/universegen/SectorLocatorService.java`（或新增 `HexGridGenerator`）
- [x] T007 [US1] 实现 HexCoord → worldCenterKm 的坐标换算（XY 平面，Z=0），文件：`shared/src/main/java/com/staraxis/universegen/CoordinateSystem.java`
- [x] T008 [US1] 在 shared 生成流程中组装 Sector 基础数据（hexCoord、worldCenterKm、sectorId），文件：`shared/src/main/java/com/staraxis/universegen/GalaxyGeneratorFacade.java`（或实际生成入口类）
- [x] T009 [P] [US1] 为 US1 补充最小单元测试：R=0/1/8 的星区数公式校验，文件：`shared/src/test/java/com/staraxis/universegen/SectorLocatorServiceTest.java`
- [x] T010 [P] [US1] 为 US1 补充相邻中心距验证测试（允许 1% 误差），文件：`shared/src/test/java/com/staraxis/universegen/SectorLocatorServiceTest.java`（相邻中心距验证）

---

## Phase 4: User Story 2（P2）- 预设占用 + 剩余星区按比例分配内容类型（确定性）

**Goal**: 先应用 JSON 预设占用（冲突后来者覆盖），再对剩余星区按比例分配 `contentTypeId`，并满足可复现与统计误差要求。

**Independent test**:
- 固定 seed + 固定配置，连续生成 3 次完全一致（SC-004）
- N≥200 时比例偏差 ≤ ±10%（SC-003）

### Tasks

- [x] T011 [US2] 定义内容类型注册表的数据结构（`typeId/displayNameZh/debugColor/iconKey`），并确定其加载入口，文件：`shared/src/main/java/com/staraxis/universegen/config/UniverseGenConfig.java`（或新配置类）
- [x] T012 [US2] 实现内容类型注册表 JSON 加载（最小可用：内置默认三种类型 + 可从文件覆盖/扩展），文件：`shared/src/main/java/com/staraxis/universegen/config/UniverseGenConfig.java`（通过 Jackson 直接加载，并由 UniverseGenConfigTest 验证）
- [x] T013 [US2] 定义 GalaxyPreset 的 JSON schema 对应的 Java DTO，并实现加载，文件：`shared/src/main/java/com/staraxis/universegen/config/GalaxyPreset.java`（并在 `UniverseGenConfig` 增加 galaxyPresets 字段）
- [x] T014 [US2] 实现预设占用应用：fixed-hex 与 random-hex（random 必须可复现），并实现冲突“后来者覆盖”，文件：`shared/src/main/java/com/staraxis/universegen/PresetApplicator.java`（并在 `ParallelGalaxyGenerator` 接入）
- [x] T015 [US2] 实现“剩余星区”计算（排除 preset 占用），并保持 HexCoord 稳定排序，文件：`shared/src/main/java/com/staraxis/universegen/SectorContentAllocator.java`
- [x] T016 [US2] 实现按比例分配算法（配额 + score 排序），使用 `seed+HexCoord` 派生随机，文件：`shared/src/main/java/com/staraxis/universegen/SectorContentAllocator.java`
- [x] T017 [US2] 实现“剩余不足截断”策略：尽量分配、不足截断，截断顺序固定（恒星系→星云→深空），并输出日志/调试提示，文件：`shared/src/main/java/com/staraxis/universegen/SectorContentAllocator.java`
- [x] T018 [US2] 实现内容生成占位：
  - `contentTypeId == star-system` 时生成 `starSystemId` 占位符并绑定
  - `nebula/deep_space` 生成对应标记字段
  文件：`shared/src/main/java/com/staraxis/universegen/SectorGenerator.java`（或等价）
- [x] T019 [P] [US2] 新增可复现性测试：相同 seed/config 连续 3 次结果完全一致，文件：`shared/src/test/java/com/staraxis/universegen/RepeatabilityTest.java`
- [x] T019a [US2] 增强可复现性测试：验证两个预设冲突时，后加载的预设会覆盖先加载的预设，且 `occupancySource` 记录正确。
- [x] T020 [P] [US2] 新增比例误差测试：R=8 时统计三类占比偏差 ≤ ±10%，文件：`shared/src/test/java/com/staraxis/universegen/SectorContentAllocatorTest.java`

---

## Phase 5: User Story 3（P3）- 快照/DTO 输出 + 客户端适配渲染与 F3 调试验收

**Goal**: shared 输出 UniverseSnapshot/SectorSnapshot（含 worldCenterKm、contentTypeId、starSystemId 占位符），客户端通过 adapter 转渲染模型，并在星图界面用不同标记渲染；F3 调试用于测距与统计。

**Independent test**:
- 进入星图界面能看到不同类型星区分布
- F3 打开后能读取比例尺并验证相邻中心距符合 1ly

### Tasks

- [ ] T021 [US3] 补齐/扩展 `shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/UniverseSnapshot.java` 与 `SectorSnapshot.java` 字段以符合 contracts（hexQ/hexR/worldCenterXKm/YKm/contentTypeId/starSystemId/occupancySource）
- [ ] T022 [US3] 在生成完成后构建 UniverseSnapshot（按 HexCoord 稳定排序输出 sectors），文件：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/`（新增 `UniverseSnapshotConverter.java` 或复用既有 converter）
- [x] T023 [US3] 在客户端消费 UniverseSnapshot 并转换为渲染模型（SectorModel/UniverseModel），文件：`lwjgl3/src/main/java/com/staraxis/game/client/world/UniverseModelToWorldMapAdapter.java`
- [ ] T024 [P] [US3] 在星图渲染层实现不同 `contentTypeId` 的可视化区分（颜色/图标），文件：`lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/WorldGridRenderer.java`（或星图渲染组件）
- [ ] T025 [P] [US3] 扩展/确认 F3 调试显示：
  - 当前比例尺
  - 两个选定/相邻星区的 world 距离（km→ly 反算）
  文件：`lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/DebugSystem.java`
- [ ] T026 [US3] 将 US3 的数据流完整接通：开局设置 → shared 生成 → snapshot → client adapter → 渲染，文件：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java`（或实际入口）

---

## Phase 6: Polish & Cross-Cutting（收尾与质量）

- [ ] T027 增加关键日志与调试输出（生成耗时、星区数、比例统计、预设占用数），文件：`shared/src/main/java/com/staraxis/universegen/GalaxyGeneratorFacade.java`
- [ ] T028 [P] 增加一个最小 benchmark（可选）：R=8/12 的生成耗时基准，文件：`shared/src/jmh/java/com/staraxis/UniverseGenBenchmark.java`
- [ ] T029 更新 quickstart 的实际配置路径/文件名（当 JSON 路径在实现中确定后），文件：`specs/015-realistic-galaxy-sector-gen/quickstart.md`

---

## Implementation strategy

- MVP 建议：仅实现 US1（T006~T010），确保“真实比例六边形网格”打通并可验收。
- 之后实现 US2（预设 + 分配 + 占位符），完成生成逻辑闭环。
- 最后 US3 接入客户端与调试验收。

