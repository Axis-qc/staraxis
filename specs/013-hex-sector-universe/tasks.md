# Tasks: 013 - 真实宇宙生成与六边形网格集成

> 目标：将 `universegen` 真实星系生成接入六边形星区（Sector）网格；开局一次性生成全图；开局配置提供“恒星系/星云/深空”三滑条比例（总和≤1，剩余补深空）；无需向后兼容旧版世界/快照协议。\
> 术语：`galaxy` 表示“星系星区（星系）”，`star_system` 表示“恒星系（星系内部的系统）”。

## Phase 1: Setup（准备）

- [x] T001 盘点并确认接入点：定位新游戏生成链路（server StartNewGameHandler → core StartNewGameUseCase → worldgen）并记录将被替换的旧生成器入口（core/src/main/java/com/staraxis/game/core/worldgen/StartNewGameUseCase.java）
- [x] T002 确认 universegen 侧输出结构：梳理 `com.staraxis.universegen.model` 中 Galaxy/Sector/StarSystem/Star/Planet 的可用字段，列出需要映射到“世界快照/客户端渲染”的最小字段集（shared/src/main/java/com/staraxis/universegen/**）

## Phase 2: Foundational（基础改造，阻塞后续）

- [x] T003 定义新版“世界快照”顶层协议对象与 schemaVersion 升级策略（shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/**）
- [x] T004 [P] 定义 SectorSnapshot：包含 HexCoord、sectorType（galaxy/nebula/deep_space）。NOTE：此处 `galaxy` 表示“星系星区（包含一个 star_system）”，并非恒星系本体、可选 StarSystemSnapshot（shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/**）
- [x] T005 [P] 定义 StarSystemSnapshot/StarSnapshot/PlanetSnapshot（含多恒星：stars[] 支持 1~3）（shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/**）
- [x] T006 [P] 破坏性替换 StartNewGameResponse 的 world 字段为新版世界快照类型，并同步 schemaVersion（shared/src/main/java/com/staraxis/game/shared/net/worldgen/StartNewGameResponse.java）
- [x] T007 实现服务端快照映射器：universegen Galaxy → 新版世界快照（core/src/main/java/com/staraxis/game/core/worldgen/**Mapper.java 或新增类）

## Phase 3: User Story 1（US1）新游戏生成真实宇宙并进入世界

**目标**：玩家点击“开始游戏”后，服务端一次性生成整张六边形星区地图；星区类型按三项比例分配；星系星区（galaxy）内生成真实比例恒星系（star_system，允许单/双/三恒星）；客户端能正确进入 WorldScreen 并显示星区与恒星/行星统计。

**独立验收（手工进游戏）**：
- 生成不会崩溃；能进入 WorldScreen。
- `galaxy` 星区数量与“恒星系比例”显著正相关。
- 任意一个 `galaxy` 星区的恒星系（`star_system`）内恒星数量 ∈ {1,2,3}。

- [x] T008 [US1] 在服务端实现“星区类型分配”规则：三滑条比例总和≤1、<1 剩余补深空（core/src/main/java/com/staraxis/game/core/worldgen/** 或 core/src/main/java/com/staraxis/game/core/world/**）
- [x] T009 [US1] 在服务端实现“六边形星区网格生成”：根据 mapSizePresetId 生成 boundsRadius 范围内全部 HexCoord（core/src/main/java/com/staraxis/game/core/worldgen/** 或复用现有 WorldGenDefinitions）
- [x] T010 [US1] 在服务端实现“每星区最多 1 个星系（galaxy）”；在 galaxy 星区调用 universegen 生成恒星系（star_system）（core/src/main/java/com/staraxis/game/core/worldgen/**）
- [x] T011 [US1] 重构 StartNewGameUseCase：替换 DefaultWorldGenerator，改为调用新的“HexSectorUniverseGenerator”（core/src/main/java/com/staraxis/game/core/worldgen/StartNewGameUseCase.java）
- [x] T012 [US1] 更新 StartNewGameHandler 的日志与错误处理，确保 schemaVersion 与响应结构一致（server/src/main/java/com/staraxis/game/server/http/StartNewGameHandler.java）

## Phase 4: User Story 2（US2）客户端适配新版世界快照并渲染

**目标**：客户端能解析新版世界快照结构并构建客户端侧世界数据（可为新结构），WorldScreen 渲染使用该数据。

- [x] T013 [US2] 更新 WorldGenApiClient：解析新版 StartNewGameResponse/world 快照（lwjgl3/src/main/java/com/staraxis/game/client/net/WorldGenApiClient.java）
- [x] T014 [US2] 替换/重写快照转换：新版快照 → 客户端 UniverseModel（优先放在客户端侧；shared 仅保留 DTO）（lwjgl3/src/main/java/com/staraxis/game/client/** 或 shared/src/main/java/com/staraxis/game/shared/world/**）
- [x] T015 [US2] 调整 WorldScreen 的 debug/hover 显示字段：读取 galaxy/nebula/deep_space 与恒星系统计（lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java）

## Phase 5: User Story 3（US3）新游戏配置 UI 改为三滑条比例联动

**目标**：开局配置提供“恒星系/星云/深空”三滑条，联动保持总和≤1；总和<1 的剩余将自动补给深空（深空滑条可为只读派生值）。

- [x] T016 [US3] 修改 NewGameConfigScreen：将 starDensity/nebulaRatio 改为三滑条比例（恒星系/星云/深空）（lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java）
- [x] T017 [US3] 实现三滑条联动逻辑：调节一项时自动降低其他项以保证总和≤1（lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java）
- [x] T018 [US3] 破坏性更新 StartNewGameRequest 字段：改为传递三项比例（恒星系/星云/深空）到服务端（shared/src/main/java/com/staraxis/game/shared/net/worldgen/StartNewGameRequest.java 与客户端组装处）

## Phase 6: Polish & Cross-Cutting（收尾与质量）

- [x] T019 端到端手工验证：多组比例（如 0.1/0.2/0.7 与 0.9/0.05/0.05）生成结果符合预期（server + client）
- [x] T020 性能与并行：确保生成阶段可利用多核并行（例如按星区并行生成恒星系），并记录中等地图生成耗时（core/shared/universegen 相关生成入口）
- [x] T021 清理旧占位符路径：仅处理仍被新游戏生成链路调用到的旧入口（避免跨模块大扫除）（core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java 等）

## Dependencies（依赖顺序）

- Phase 2（快照协议/映射器）→ Phase 3（服务端生成）→ Phase 4（客户端适配）→ Phase 5（UI 三滑条）→ Phase 6（验证与优化）

## Parallel Opportunities（可并行任务示例）

- T004/T005/T006 可并行（不同 DTO 文件）
- T013 与 T016 可并行（客户端网络层 vs UI）

## MVP 建议

- MVP = 完成 US1 + US2：服务端使用新生成器生成真实星区 + 客户端能解析并进入 WorldScreen
