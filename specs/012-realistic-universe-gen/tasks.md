---

description: "Task list for 012 真实比例宇宙生成 – 包含多核性能优化"
---

# Tasks: 012 真实比例宇宙生成

**Input**: Design documents from `/specs/012-realistic-universe-gen/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 创建 `shared`、`client`、`server` 子模块目录结构（按照 plan.md）
- [X] T002 在根 `build.gradle` 添加 Kryo、LuaJ、JMH 依赖
- [X] T003 [P] 初始化 JMH Gradle 插件并生成示例基准类 `shared/src/jmh/java/BenchmarkStub.java`
- [X] T004 [P] 添加 `universegen` 包占位类 `GalaxyGenerator.java`、`SectorGenerator.java` 等空实现

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T005 实现 `CoordinateSystem` (分层坐标) 于 `shared/src/main/java/com/staraxis/universegen/CoordinateSystem.java`
- [X] T006 创建 `UniverseGenConfig.java` 并解析 JSON + Lua 脚本（LuaJ）
- [X] T007 [P] 实现 `RandomUtil.java` 使用 `SplittableRandom` 并提供种子派生方法
- [X] T008 [P] 实现 `KryoSerializer.java` 支持 Galaxy ↔︎ 文件 序列化
- [X] T009 实现 `ThreadPools.java`（generationPool、ioPool）并封装关闭逻辑
- [X] T010 初版 `SequentialGalaxyGenerator` 完成（单线程）
- [X] T011 实现 `ParallelGalaxyGenerator` 使用 ForkJoinPool 并行 Sector 生成
- [X] T012 JUnit5 测试 `CoordinateSystemTest` 验证 1km 精度与 sectorId 映射

**Checkpoint**: 基础设施 & 多核管线可运行 Demo

---

## Phase 3: User Story 1 – 银河层浏览 (P1) 🎯 MVP

**Goal**: 玩家可生成银河并在客户端查看整体缩放，像素/距离匹配。

**Independent Test**: 生成含 N 个星系的银河文件；客户端加载后最大缩放 1px=1km。

### Implementation

- [X] T013 [P] [US1] 完成 `GalaxyGenerator.generate()` 整体流程（调用 Parallel 版本）
- [X] T014 [US1] 在 client 实现 `UniverseRenderer` 基础渲染 + 最大缩放级别逻辑
- [X] T015 [US1] 添加 `GalaxyLoadScreen` 负责加载和显示生成进度
- [X] T016 [US1] 性能基准：JMH `GalaxyGenBenchmark` 目标 10⁵ 恒星系 ≤ 5s
- [X] T017 [US1] 日志 & 异常处理（生成失败提示）

**Checkpoint**: 生成 + 渲染银河 OK，基准达标

---

## Phase 4: User Story 2 – 星区快速跳转 (P1)

**Goal**: 玩家点击星区列表可在 <1s 进入星区视图，真实比例显示。

**Independent Test**: 选择任意 Sector 跳转，边界尺寸符合真实比例。

### Implementation

- [X] T018 [P] [US2] 在 client 实现 `SectorCameraController` 支持平滑动画
- [X] T019 [US2] 在 shared 添加 `SectorLocatorService` 提供 Sector → 坐标查询
- [X] T020 [US2] client 渲染星区内恒星分布 `SectorRenderer`
- [X] T021 [US2] 最大跳转耗时计时 & 优化（<1s）

---

## Phase 5: User Story 3 – 恒星系内轨道建造 (P2)

**Goal**: 行星/卫星轨道与距离正确，玩家放置建筑位置精准。

**Independent Test**: 放置采矿站距行星中心距离 = 显示公里数。

### Implementation

- [X] T022 [P] [US3] 实现 `StarSystemGenerator` 轨道计算 (开普勒第三定律)
- [X] T023 [US3] 在 client 实现 `StarSystemRenderer` (行星、卫星轨道)
- [X] T024 [US3] 校验轨道半径误差 <2% 单元测试

---

## Phase 6: User Story 4 – 坐标轴调试开关 (P2)

**Goal**: F3 显示/隐藏世界坐标轴，长度随缩放自适应。

**Independent Test**: 手动测试按 F3 切换轴线，缩放相机长度自适应。

### Implementation

- [X] T025 [P] [US4] 在 client 添加 `CoordinateAxisOverlay.java`
- [X] T026 [US4] 实现 `DebugInputProcessor` 监听 F3 并切换可见性

---

## Phase 7: User Story 5 – 极端参数生成测试 (P2)

**Goal**: 系统能处理非法/极端配置并安全回退。

**Independent Test**: 提供 5 组非法参数，生成不崩溃、报错清晰。

### Implementation

- [X] T027 [P] [US5] 在 shared 添加参数校验 `ConfigValidator`
- [X] T028 [US5] 单元测试 `ExtremeParamsTest` 覆盖负半径、零密度等

---

## Phase 7.5: 复现性、六边形边界 & 重叠检测补强

- [X] T034 [P] 实现 `OverlapDetector.java` 在生成后验证天体距离，写入报告
- [X] T035 编写 `NoOverlapTest` 单元测试，随机采样 1000 对天体（ThreadLocalRandom），若发现 ≥1 起重叠则失败
- [X] T036 追加 `RepeatabilityTest`：相同种子生成两次，二进制 diff 输出为空
- [X] T038 [P] 实现 `SectorBoundaryTest` 计算六边形边长标准差，断言 <5%
- [ ] T037 更新 CI 脚本，在生成步骤后执行 T036

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T039 MemoryBenchmark：JMH/Java Flight Recorder 监控生成阶段峰值内存，目标 <256 MB

- [ ] T029 [P] 完成多核性能调优：线程池大小自适应 + 分片序列化
- [ ] T030 JMH 比对并写入 `research.md` 加速比图表
- [ ] T031 文档更新：quickstart.md 增补并行参数说明
- [ ] T032 代码清理与注释审查
- [ ] T033 [P] 在 `.github/workflows/ci.yml` 集成 JMH baseline 上传

---

## Dependencies & Execution Order

- **Phase 1** → Phase 2 (Foundational blocks all)
- User Stories 1 & 2 可并行（同优先级 P1）
- User Stories 3–5 依赖 Galaxy & Sector 数据完成后开始
- Polish phase最后

### Parallel Opportunities

- T002 / T003 / T004 可并行
- 在 Phase 2，T007、T008 可并行
- 各 `[P]` 标记任务跨文件无依赖

---

## MVP Scope

完成 Phase 1、Phase 2、User Story 1（T001–T017）即可产出可见银河生成与渲染 Demo。
