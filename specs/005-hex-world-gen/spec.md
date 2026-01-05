# Feature Specification: Hexagonal World Generation

**Feature Branch**: `005-hex-world-gen`
**Created**: 2026-01-05
**Status**: Draft
**Input**: User description: "开始设计世界生成，按照六边形进行空间划分，渲染世界，镜头固定为顶部正交视角 - 大地图由六边形“星域单元”拼接而成。 - 每个六边形区域可以是星系、深空、星云等。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 六边形网格渲染 (Priority: P1)

作为一名玩家，我希望看到游戏地图由清晰的六边形格子组成，以便直观地了解不同星域的边界和相对位置。

**Why this priority**: 核心视觉基础，决定后续所有交互和寻路逻辑。

**Independent Test**: 启动游戏场景，屏幕上显示平铺的六边形网格，且每个网格位置准确无重叠。

**Acceptance Scenarios**:

1. **Given** 游戏进入主场景, **When** 初始化世界渲染, **Then** 屏幕上显示由六边形紧密排列的地图网格。
2. **Given** 不同的六边形坐标, **When** 鼠标悬停在某个格子上, **Then** 能高亮显示对应的六边形边界。

---

### User Story 2 - 星域类型生成 (Priority: P1)

作为一名玩家，我希望探索的世界充满多样性，不同的六边形区域代表不同的环境（星系、深空、星云）。

**Why this priority**: 游戏玩法的核心，不同的地形类型影响资源和策略。

**Independent Test**: 使用随机种子生成地图，验证生成的六边形包含多种类型，且符合预设分布概率。

**Acceptance Scenarios**:

1. **Given** 地图生成器启动, **When** 生成 100x100 的网格, **Then** 统计显示至少包含三种类型的区域：星系 (Galaxy)、深空 (Deep Space)、星云 (Nebula)。
2. **Given** 相同的种子, **When** 多次生成地图, **Then** 每次生成的地图布局完全一致。

---

### User Story 3 - 顶部正交视角控制 (Priority: P2)

作为一名玩家，我希望以垂直俯视的上帝视角观察世界，并且镜头高度固定，确保战术视野清晰。

**Why this priority**: 确立游戏的基本操作视角，避免透视带来的判断误差。

**Independent Test**: 调整摄像机参数，确认渲染出的画面无透视形变（远近物体大小一致）。

**Acceptance Scenarios**:

1. **Given** 场景中有多个相同大小的六边形, **When** 移动摄像机到不同位置, **Then** 屏幕边缘和中心的六边形显示尺寸保持一致（无透视缩放）。
2. **Given** 玩家尝试旋转视角, **When** 输入旋转指令, **Then** 系统忽略旋转请求或仅支持平面旋转（视具体设计而定，默认锁定俯视）。

---

### User Story 4 - 新游戏配置 (Priority: P1)

作为一名玩家，我希望在开始新游戏前配置宇宙参数，以便自定义游戏的规模和难度体验。

**Why this priority**: 提供多样化的游戏体验，是生成器的输入入口。

**Independent Test**: 在配置界面修改参数，启动生成，验证生成的地图属性与配置一致。

**Acceptance Scenarios**:

1. **Given** 玩家点击“新游戏”, **When** 进入配置界面, **Then** 显示地图大小、宜居星球比例、地图种子等选项；AI 数量、技术等级作为未来功能占位符显示但不可编辑（置灰/标注开发中）。
2. **Given** 玩家输入特定种子 "STARAXIS", **When** 点击开始游戏, **Then** 每次生成的地图布局完全一致。
3. **Given** 玩家选择“大型”地图, **When** 进入游戏, **Then** 网格半径显著大于“小型”地图。

---

### Edge Cases

- **世界边界处理**：采用**封闭边界 (Bounded)** 设计，地图边缘不可通行。当摄像机移动到边缘时，显示明确的视觉边界（如虚空或屏障）。
- **超大地图性能**：当生成的网格超过可视范围（如 1000x1000）时，仅渲染摄像机视锥体内的六边形（Culling）。
- **坐标转换精度**：由于六边形坐标系（Cubic）与屏幕像素坐标转换涉及浮点数，需确保点击判定误差小于 1 像素。
- **配置参数边界**：当输入无效种子或极端参数时，系统应自动修正为默认值或合法范围内的最近值。

## Clarifications

### Session 2026-01-05
- **地图边界**: 封闭边界 (Bounded)
- **坐标系**: 立方体坐标 (Cubic: x, y, z)
- **缩放机制**: 支持语义缩放 (Semantic Zoom/LOD)，缩小时简化显示，放大时显示细节。
- Q: 地图大小如何定义？ → A: 使用“地图大小预设”定义六边形地图半径 R（中心到边界格数），预设以 ID（如 `small`/`medium`/`large`）标识。
- Q: Seed（地图种子）输入格式是什么？ → A: 玩家输入任意字符串作为种子，系统内部转换为数值 Seed；留空则随机生成。
- Q: HabitableRatio（宜居星球比例）具体含义是什么？ → A: 对每个“星系格”按 HabitableRatio 决定是否生成宜居星球/宜居星系特性。
- Q: AICount/TechLevel（预留项）在新游戏配置中如何呈现？ → A: UI 显示但不可编辑（置灰/标注开发中），当前版本不生效。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 六边形网格系统 (Hex Grid System): 必须实现基于**立方体坐标 (Cubic Coordinates: x, y, z)** 的数学模型，支持坐标转换（屏幕 <-> 网格）及距离计算。
- **FR-002**: 程序化生成 (Procedural Generation): 提供生成器模块，支持基于噪声（如 Perlin Noise）或元胞自动机的地形生成算法。
- **FR-003**: 渲染层级 (Render Layers): 世界渲染必须分为底层（背景星空）、网格层（六边形地形）、顶层（UI/单位）。
- **FR-004**: 正交摄像机与缩放 (Camera & Zoom): 使用正交投影摄像机，支持**语义缩放 (Semantic Zoom)**。当缩放级别较低（俯瞰大地图）时，渲染简化的色块或图标；当缩放级别较高（近距离观察）时，渲染详细的纹理和特效。
- **FR-005**: 瓦片数据结构 (Tile Data Structure): 定义六边形瓦片数据模型，包含立方体坐标 (x, y, z)、地形类型标识 (`typeId`) 和资源属性。
- **FR-006**: 性能优化 (Performance Optimization): 渲染循环中必须剔除视口外的瓦片 (Frustum Culling)，避免全图渲染。
- **FR-007**: 世界配置模型 (World Configuration): 定义 `WorldGenConfig` 数据结构，包含：
    - `MapSizePresetId` (字符串；引用“地图大小预设”ID；预设定义六边形地图半径 R，即中心到边界格数)
    - `HabitableRatio` (浮点: 0.0 - 1.0；对每个“星系格”决定是否生成宜居星球/宜居星系特性)
    - `Seed` (由玩家输入字符串种子转换得到的长整型；输入为空时随机生成)
    - `AICount` (整数，预留；UI 显示但不可编辑，当前版本不生效)
    - `TechLevelPresetId` (字符串，预留；UI 显示但不可编辑，当前版本不生效)
- **FR-008**: 确定性生成 (Deterministic Generation): 生成器必须通过 `Seed` 初始化随机数生成器，确保相同配置和种子产生完全一致的结果。

### Key Entities *(include if feature involves data)*

- **HexGrid (网格管理器)**: 存储所有瓦片数据，提供邻居查找和路径计算方法。
- **HexTile (六边形单元)**: 基础数据单元，包含地形类型标识 `typeId`（如 `galaxy`/`deep_space`/`nebula`）。
- **WorldRenderer (世界渲染器)**: 负责遍历可见网格并绘制。
- **WorldGenerator (生成器)**: 负责初始化填充网格数据。
- **WorldGenConfig (生成配置)**: 传递给生成器的参数对象。

## Success Criteria *(mandatory)*

### Measurable Outcomes
 
 - **SC-001**: 地图生成速度在主流配置下（如 i5 CPU）生成 100x100 网格耗时不超过 200ms。
 - **SC-002**: 渲染帧率在 1080p 分辨率下，满屏显示六边形网格时保持 60 FPS 以上。
 - **SC-003**: 鼠标点击屏幕任意位置，能够准确（100% 准确率）返回对应的六边形网格坐标 (q, r)。
 - **SC-004**: 单元测试覆盖六边形坐标转换算法的所有边界情况（如负坐标、零坐标）。
