---

description: "Task list for 无缝宇宙生成（4X 大战略）"
---

# Tasks: 无缝宇宙生成（4X 大战略）

**Input**: Design documents from `/specs/012-realistic-universe-gen/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 创建 `core/world/generation` 目录结构
- [ ] T002 初始化 `preset-systems.json` 示例文件于 `assets/world/preset-systems.json`
- [ ] T003 [P] 在 `build.gradle` 中添加 Jackson 依赖用于 JSON 解析
- [ ] T004 配置 JUnit 5 测试套件于 `core/build.gradle`

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T005 创建基础实体 `PresetSystem` 于 `shared/src/main/java/com/staraxis/game/shared/world/generation/PresetSystem.java`
- [ ] T006 [P] 创建基础实体 `GalaxyGenerationConfig` 于 `shared/.../generation/GalaxyGenerationConfig.java`
- [ ] T007 创建接口 `StarSystemFactory` 于 `core/.../generation/StarSystemFactory.java`
- [ ] T008 [P] 实现 `PresetSystemLoader` 于 `core/.../generation/PresetSystemLoader.java`
- [ ] T009 [P] 实现随机采样工具包目录 `core/.../generation/sampling/` 并创建空类 `StarSampler.java`, `PlanetSampler.java`, `OrbitSampler.java`
- [ ] T010 配置 `GalaxyGeneratorTest` 基础测试类于 `tests/unit/generation/GalaxyGeneratorTest.java`

- [ ] T032 [P] 实现 `ModPresetSystemLoader` 于 `core/.../generation/ModPresetSystemLoader.java`
- [ ] T033 编写 `ModPresetSystemLoaderTest` 于 `tests/unit/generation/ModPresetSystemLoaderTest.java`

**Checkpoint**: 基础结构与依赖满足，进入用户故事实现

---

## Phase 3: User Story 1 - 探索随机恒星系 (Priority: P1) 🎯 MVP

**Goal**: 玩家进入随机星区可看到科学合理的随机恒星系
**Independent Test**: 运行 `GalaxyGenerator.generate()` 使用相同种子生成，验证结果多样性与物理约束

### Implementation for User Story 1

- [ ] T011 [P] [US1] 实现 `RandomStarSystemFactory` 于 `core/.../generation/RandomStarSystemFactory.java`
- [ ] T012 [P] [US1] 完成 `StarSampler` 逻辑（恒星质量、光谱分类）
- [ ] T013 [P] [US1] 完成 `PlanetSampler` 逻辑（行星类型、半径）
- [ ] T014 [P] [US1] 完成 `OrbitSampler` 逻辑（轨道距离 log 分布）
- [ ] T015 [US1] 在 `GalaxyGenerator` 中集成随机生成流程（遍历星区 -> 调用 RandomStarSystemFactory）
- [ ] T016 [US1] 扩展 `GalaxyGeneratorTest` 编写多种子统计测试于 `tests/unit/generation/RandomGenerationTest.java`
- [ ] T034 [US1] 实现种子参数在 `GalaxyGenerator` 中生效并编写 `SeedConsistencyTest` 于 `tests/unit/generation/SeedConsistencyTest.java`

**Checkpoint**: 随机恒星系生成完成，可独立验证

---

## Phase 4: User Story 2 - 访问预设恒星系 (Priority: P1)

**Goal**: 玩家能在指定或随机星区进入固定结构的特殊恒星系
**Independent Test**: 加载包含太阳系的 `preset-systems.json`，验证坐标与天体数据

### Implementation for User Story 2

- [ ] T017 [P] [US2] 在 `PresetSystemLoader` 实现 JSON 解析为 `PresetSystem` 列表
- [ ] T018 [P] [US2] 在 `GalaxyGenerator` 中实现预设先生成逻辑，支持固定与随机坐标
- [ ] T019 [US2] 实现冲突检测与重采样：若预设占用星区，则跳过随机生成
- [ ] T020 [US2] 编写 `PresetSystemLoaderTest` 于 `tests/unit/generation/PresetSystemLoaderTest.java`
- [ ] T021 [US2] 编写 `PresetGenerationTest` 验证预设系统正确插入于 `tests/integration/PresetGenerationTest.java`

**Checkpoint**: 预设恒星系正确生成且与随机系统无冲突

---

## Phase 5: User Story 3 - 无缝缩放体验 (Priority: P2)

**Goal**: 生成逻辑支持客户端无缝缩放，不出现阻塞加载
**Independent Test**: 服务端异步生成完成后，客户端在缩放过程中帧率≥45FPS，无加载提示

### Implementation for User Story 3

- [ ] T022 [P] [US3] 在 `GalaxyGenerationService` 提供异步生成 API，返回进度事件 (`server/.../api`)
- [ ] T023 [P] [US3] 在 `shared` 创建 `GalaxySnapshot` 只读传输对象
- [ ] T024 [US3] 在 `lwjgl3` 客户端实现 `GalaxyLoadingScreen` 使用后台线程接收并流式渲染
- [ ] T025 [US3] 编写 `GalaxyGenerationPerformanceTest` 于 `tests/integration/GalaxyGenerationPerformanceTest.java`

**Checkpoint**: 客户端缩放无缝体验验证通过

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T026 [P] 更新开发文档 `docs/galaxy-generation.md`
- [ ] T027 代码清理与重构：统一命名、删除冗余
- [ ] T028 性能优化：并行线程池参数调优，记录基准
- [ ] T029 [P] 增补单元测试覆盖率 ≥ 80%
- [ ] T030 安全审计：确认 JSON 解析与文件 IO 无注入风险
- [ ] T031 运行 `quickstart.md` 验证并修正步骤

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → **Foundational (Phase 2)** → 用户故事 (Phases 3,4,5)
- US1 与 US2 均无先后依赖，可并行；US3 依赖前两故事生成数据结构
- Polish 阶段最后执行

### Parallel Opportunities

- 所有标记 [P] 的任务可并行执行
- US1 与 US2 可由不同开发者同时推进

---

## Implementation Strategy

- 首先完成 Phase 1-2，为生成逻辑奠定基础
- 以 US1 作为 MVP 可演示随机银河生成
- 合并 US2 提供真实宇宙沉浸感
- 最后实现 US3 提升用户体验

