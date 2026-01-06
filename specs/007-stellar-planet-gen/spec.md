# Feature Specification: Stellar & Planet Generation (恒星与行星生成)

**Feature Branch**: `007-stellar-planet-gen`
**Created**: 2026-01-06
**Status**: Draft
**Input**: User description: "使用中文对话，开始设计恒星和行星生成，生成根据世界的六边形区块，世界为3d渲染，但是视角为2d，接入新游戏的设置中"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 新游戏配置宇宙参数 (Priority: P1)

作为一名玩家，我希望在“新游戏”开始前配置宇宙/世界生成参数（含随机种子），以便获得可复现且可自定义的开局世界。

**Why this priority**: 生成器的输入入口；没有配置入口就无法稳定验证与复现生成结果。

**Independent Test**: 进入新游戏配置界面，设置参数并开始游戏；验证世界生成结果与配置一致，且相同种子可复现。

**Acceptance Scenarios**:

1. **Given** 玩家点击“新游戏”, **When** 进入世界生成设置, **Then** 能看到并修改：地图大小、种子、恒星密度、行星复杂度/数量倾向等核心参数，并能开始游戏。
2. **Given** 玩家输入种子 "STARAXIS" 与固定配置, **When** 连续开始两次新游戏, **Then** 两次生成的六边形世界布局与恒星/行星生成结果一致（可通过对比统计或可视化标记验证）。
3. **Given** 玩家将 `Seed` 留空, **When** 点击开始游戏, **Then** 系统必须生成随机 `seedValue` 并在日志/调试信息中输出最终 `seedValue`，以便玩家可复制该 `seedValue/seedText` 复现本次生成结果。
4. **Given** 世界生成耗时较长, **When** 玩家点击开始游戏, **Then** UI 必须显示加载态直到生成完成或失败（不要求进度条）。

---

### User Story 2 - 基于六边形区块生成“星域-恒星-行星”内容 (Priority: P1)

作为一名玩家，我希望每个六边形区块代表一个有意义的星域单元，并能在其中生成恒星与行星（或深空/星云等类型），以便探索时具备丰富且合理的差异。

**Why this priority**: 这是“恒星与行星生成”本身的核心价值；决定地图内容密度与探索玩法基础。

**Independent Test**: 生成一张指定规模的地图，抽样多个六边形区块，验证类型分布、恒星/行星存在性与属性范围满足规则。

**Acceptance Scenarios**:

1. **Given** 世界生成器运行, **When** 生成一张地图, **Then** 每个六边形区块被赋予一个明确的星域类型（如：星系、深空、星云），并在星系区块内生成 0..N 个恒星与行星数据对象。
2. **Given** 同一 `Seed` + 同一地图大小 + 固定 `NebulaRatio`, **When** 将 `StarDensity` 从低值提升到高值并生成两次, **Then** 以 `FR-008` 的统计口径对比 `galaxyTileCount`，必须满足：高值配置的 `galaxyTileCount` **不小于**低值配置（同一地图大小下可对比）。

---

### Edge Cases

- **参数边界**：当玩家输入空种子/超长种子/非法字符时，系统应将其映射为合法种子；当比例/密度类参数越界时，自动裁剪到合法范围，并在 UI 中显示修正后的最终值（不要求弹窗提示）。
- **极端地图规模**：在最小与最大地图预设下，生成器都应能完成生成且不崩溃；超大地图应避免一次性渲染全图导致卡顿。
- **内容稀疏与过密**：当恒星密度趋近 0 或 1 时，仍应保持类型分布的可解释性与可读性；不要求强制“最小多样性保底”，允许在 `StarDensity + NebulaRatio >= 1` 的情况下出现 `deep_space = 0`（与“SectorType 分配口径（方案 A）”一致），并确保不会出现不可读的拥挤叠加。
- **视觉遮挡**：当星云/特效密集时，六边形边界与交互高亮仍需可辨识。
- **确定性漂移**：规则版本变更时，应能明确区分“配置变更导致差异”与“实现不确定性导致差异”。

## Clarifications

### Session 2026-01-06

- **六边形区块含义**: 六边形是“星域单元”，可为星系/深空/星云等。恒星与行星属于“星系类区块”的内部内容。
- **视角**: 默认俯视为主，目标是“战术可读”；允许镜头平移与缩放，默认不以倾斜透视作为主要表达方式。
- **确定性**: 相同配置+种子必须生成一致的世界与星体数据。
- Q: `MapSizePresetId` 如何定义地图大小？ → A: 使用**六边形半径 R（封闭边界）**定义地图规模；`MapSizePresetId` 映射到半径 `R`（中心到边界的格数），地图边界封闭且不可通行。
- Q: 一个“星系类六边形区块”里恒星系统结构是什么？ → A: **多恒星常态**：每个星系区块生成 1 个星系系统，允许 **1..3 颗恒星**（包含双星/三星），行星生成规则需适配多恒星。
- Q: 多恒星系统里的行星归属如何定义？ → A: **行星分配到恒星**：每颗恒星各自生成一组行星，行星必须明确归属到某一颗恒星；本阶段不引入“系统重心轨道”或混合模型。

## Out of Scope (本期不做)

- **不做天体力学真实轨道**：不模拟引力/椭圆轨道/重心轨道；仅定义“归属关系”和用于展示的 `orbitIndex`。
- **不做系统重心行星/混合归属**：行星必须归属到具体恒星（与你 clarify 一致）。
- **不做无限世界/流式生成**：地图为封闭边界（半径 R）。
- **不做玩法系统**：殖民、战斗、经济、资源产出等均不在本期。
- **不做联机同步/服务器权威协议定稿**：只定义逻辑层/客户端边界，不做网络实现。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: **新游戏配置入口 (New Game World Settings)**: 在“新游戏”流程中提供世界生成设置入口，玩家可配置并确认后开始生成。
- **FR-002**: **世界生成配置模型 (WorldGenConfig)**: 定义可序列化的世界生成配置，至少包含：
  - `MapSizePresetId`：地图大小预设 ID（映射到六边形地图半径 `R`，即中心到边界的格数；**封闭边界**不可通行；默认值：`medium`）
  - `Seed`：玩家输入的字符串种子（可为空；为空则生成随机 `seedValue`，并在日志/调试信息中输出最终 `seedValue` 以便复现；同一字符串必须确定性映射到同一 `seedValue`）
  - `StarDensity`：恒星密度（0.0 - 1.0；默认值：`0.6`）
  - `PlanetComplexity`：行星复杂度/数量倾向（0.0 - 1.0；默认值：`0.5`）
  - `NebulaRatio`：星云区块占比（0.0 - 1.0；默认值：`0.2`）

#### SectorType 分配口径（方案 A）

- `StarDensity` 定义为全图中 `galaxy`（星系类区块）的**目标占比**（不是“星系区块内恒星数量”）。
- `NebulaRatio` 定义为全图中 `nebula`（星云区块）的**目标占比**。
- `deep_space` 为剩余类型。
- 分配规则：
  - 记 `g = clamp(StarDensity, 0, 1)`，`n = clamp(NebulaRatio, 0, 1)`。
  - 若 `g + n <= 1`：
    - `p(galaxy) = g`
    - `p(nebula) = n`
    - `p(deep_space) = 1 - g - n`
  - 若 `g + n > 1`：为避免负概率，对 `galaxy/nebula` 做归一化：
    - `p(galaxy) = g / (g + n)`
    - `p(nebula) = n / (g + n)`
    - `p(deep_space) = 0`
- 抽样口径：对每个六边形区块使用同一个 `Seed` 初始化的随机源生成 `roll ∈ [0,1)`（`roll` 必须仅依赖 `Seed` + 区块坐标，不得依赖 `StarDensity/NebulaRatio`，以保证“同 seed 下调整比例只改变阈值比较”）：
  - `roll < p(galaxy)` → `galaxy`
  - `p(galaxy) <= roll < p(galaxy)+p(nebula)` → `nebula`
  - 其它 → `deep_space`
- **FR-003**: **六边形区块内容生成 (Hex Tile Content Generation)**: 对每个六边形区块生成其 `SectorType`（星系/深空/星云等）以及对应内容数据；星系类区块必须支持生成恒星与行星数据。
- **FR-004**: **恒星生成规则 (Star Generation Rules)**: 恒星生成必须受 `StarDensity` 与地图规模影响，并满足可验证的上下限；在星系类区块内，星系系统允许生成 **1..3 颗恒星**（支持双星/三星）。
  - 最小可测试口径（用于单元测试/回归验证）：
    - 对每个 `galaxy` 区块：必须生成且仅生成 1 个 `StarSystem`。
    - 对非 `galaxy` 区块（`deep_space`/`nebula`）：不得生成 `StarSystem`。
    - 记全图 `galaxyTileCount = count(sectorType == galaxy)`，则全图 `starCount` 必须满足：`starCount ∈ [galaxyTileCount, 3 * galaxyTileCount]`。
    - 1..3 恒星数量分布口径（首版默认分布，后续可数据驱动替换）：对每个 `StarSystem` 抽样 `starsPerSystem`：
      - `P(1) = 0.70`
      - `P(2) = 0.20`
      - `P(3) = 0.10`
- **FR-005**: **行星生成规则 (Planet Generation Rules)**: 行星生成必须依赖所属星系区块，并受 `PlanetComplexity` 影响；在多恒星系统中，行星必须**明确归属到某一颗恒星**（每颗恒星各自生成行星组），本阶段不引入“系统重心轨道/混合归属”模型；生成结果满足可验证的数量范围与属性范围（例如行星数量不为负、类型在类型定义集合/配置表内）。
  - `PlanetComplexity` 的数量口径：对每颗恒星独立生成行星数量 `planetCountPerStar`，并满足：
    - 记 `c = clamp(PlanetComplexity, 0, 1)`。
    - 目标均值 `μ = 1 + round(c * 5)`（即 `μ ∈ [1,6]`）。
    - 允许范围：`planetCountPerStar ∈ [0, μ + 2]`。
    - 同一 `WorldGenConfig` + `Seed` 下，`planetCountPerStar` 的生成必须确定性一致。
  - 边界行为：允许某些恒星 `planetCountPerStar = 0`；不要求在多恒星之间平均分配行星数量。
- **FR-006**: **渲染可读性约束 (2D Readable Top-down Presentation)**: 世界渲染需保证六边形边界与交互反馈（悬停/选中）在任何缩放级别下可见且不与 3D 内容严重错位。
  - 最小可测试口径：六边形边界线与悬停/选中高亮必须始终可见（不被星云/特效遮挡）；超大地图下应避免全图一次性渲染导致卡顿（允许视口裁剪/按屏幕范围绘制）。
- **FR-007**: **确定性生成 (Deterministic Generation)**: 生成器必须由 `Seed` 初始化随机性来源；在相同 `WorldGenConfig` 下生成结果必须一致。
- **FR-008**: **统计与回归验证支持 (Validation Stats)**: 生成完成后应能获取关键统计（区块类型计数、含恒星区块数、恒星总数、行星总数/分布），用于测试与回归验证。
   - 关键统计指标（最小集合，作为回归对比口径）：
     - `tileCount`
     - `sectorCounts`（按 `SectorTypeId` 计数：`galaxy`/`deep_space`/`nebula`）
     - `galaxyTileCount`
     - `starCount`
     - `planetCount`

### Key Entities *(include if feature involves data)*

- **WorldGenConfig (生成配置)**: 世界生成输入参数集合。
- **HexTile (六边形星域单元)**: 地图的基础区块，包含坐标、`SectorType` 与内容引用。
- **StarSystem (星系)**: 由恒星与行星组成的结构化数据，用于承载“星系区块”的内容。
- **Star (恒星)**: 恒星数据实体（如类别/亮度/半径等抽象属性）。
- **Planet (行星)**: 行星数据实体（如类型/轨道/资源倾向等抽象属性）。
- **WorldGenerator (世界生成器)**: 根据配置生成六边形区块与星体数据。
- **WorldRenderer (世界渲染器)**: 将区块与星体数据呈现为可交互的俯视世界。

## Assumptions & Dependencies

- **Assumptions**:
  - 六边形坐标系统与基础网格渲染能力已存在或可复用既有世界生成相关基础设施。
  - “星系/深空/星云”等类型的具体美术呈现可以逐步迭代，规格优先约束数据与可读性。
- **Dependencies**:
  - 依赖现有“新游戏”流程与 UI 框架提供配置界面承载能力与输入校验能力。
  - 依赖本地化文本系统以支持配置项名称与提示文本的中英扩展（若项目已有本地化机制）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在默认地图预设下，世界（区块类型 + 恒星/行星数据）生成耗时不超过 300ms（以开发机基准测量）。
  - 测量口径：从点击“开始游戏”触发生成到 `WorldMap`（含 `FR-008` 统计）构建完成的耗时（不包含渲染帧耗时）。
- **SC-002**: 在 1080p 分辨率下，满屏浏览世界地图时帧率保持 60 FPS 以上（无明显卡顿）。
  - 测量口径：在 `WorldScreen` 中持续平移/缩放浏览世界地图，观察 FPS 显示；无明显掉帧与卡顿。
- **SC-003**: 相同 `WorldGenConfig`（包含相同 `Seed`）生成 3 次，`FR-008` 定义的关键统计指标最小集合必须 **100% 全等一致**（逐字段相等，不允许浮动）。
- **SC-004**: 新游戏配置界面中修改任意一个生成参数并开始游戏，生成结果能体现该参数变化（可通过统计或可视化差异验证）。
  - 判定口径（最小可测试）：在同一 `Seed` + 同一地图大小下：
    - 仅提高 `StarDensity`（固定 `NebulaRatio`）时，`galaxyTileCount` 不得下降。
    - 仅提高 `NebulaRatio`（固定 `StarDensity`）时，`sectorCounts[nebula]` 不得下降。
