# Research: Hex World Generation & New Game Config

**Feature**: [spec.md](./spec.md)  
**Branch**: `005-hex-world-gen`  
**Created**: 2026-01-05

## Decisions

### 1) 六边形坐标系
- **Decision**: 使用立方体坐标系（Cubic: x, y, z），并提供屏幕坐标与网格坐标双向转换。
- **Rationale**: 立方体坐标距离计算与邻居计算更直接，减少偏移坐标带来的边界/奇偶行复杂度。
- **Alternatives considered**:
  - 轴向坐标（Axial: q, r）：存储更省，但部分算法仍需转换。
  - 偏移坐标（Offset）：直观但算法复杂、容易出错。

### 2) 地图边界
- **Decision**: 封闭边界（Bounded）。
- **Rationale**: 首版玩法更可控，边界渲染与摄像机限制更简单，便于后续扩展为循环/无限。
- **Alternatives considered**:
  - 循环边界（Toroidal）：更有探索感，但坐标与邻接规则复杂。
  - 无限生成：复杂度最高，暂不进入首版。

### 3) 地图大小预设（MapSizePreset）含义
- **Decision**: 使用“地图大小预设”以 ID（如 `small`/`medium`/`large`）标识，并由预设定义六边形地图半径 R（中心到边界格数）。
- **Rationale**: 与六边形网格天然匹配，生成与裁剪更明确。
- **Alternatives considered**:
  - 宽高/矩形裁剪：与六边形边界的关系不直观。
  - 目标总格数反推：不利于 UI 清晰表达。

### 4) Seed 输入
- **Decision**: 玩家输入任意字符串作为种子，内部转换为数值；留空则随机生成。
- **Rationale**: 玩家体验更好（可输入如 "STARAXIS"），同时保持确定性生成。
- **Alternatives considered**:
  - 仅数字种子：限制较大。

### 5) 宜居星球比例（HabitableRatio）语义
- **Decision**: 对每个“星系格”按 HabitableRatio 决定是否生成宜居星球/宜居星系特性。
- **Rationale**: 将“宜居”限定在星系环境，避免对深空/星云等格子产生歧义。
- **Alternatives considered**:
  - 全图比例：会与地形分布耦合。
  - 仅星系格总体比例：不如逐格概率易扩展。

### 6) 语义缩放（Semantic Zoom）
- **Decision**: 支持语义缩放（LOD），低缩放显示抽象信息，高缩放显示细节。
- **Rationale**: 大地图阅读性更好，并为后续单位/标记层提供空间。
- **Alternatives considered**:
  - 仅视口缩放：实现简单但信息密度差。

### 7) 预留项（AI 数量/技术等级）UI 表现
- **Decision**: 在“新游戏配置”界面显示但禁用（置灰/标注开发中），不影响本期生成结果。
- **Rationale**: 明确传达路线图，同时避免玩家误以为可生效。

## Risks & Mitigations

- **点击拾取精度**: 六边形屏幕映射与点包含测试容易出现边缘误差。
  - **Mitigation**: 为坐标转换、拾取与边界点补偿编写单测；采用“像素→世界→网格”的单路径，避免重复转换。
- **大地图性能**: 全量渲染会导致性能问题。
  - **Mitigation**: 视锥裁剪（只遍历可见范围内的 q/r/x/y/z 区间）+ 分层渲染。

## Open Questions (defer)

- 是否将本局配置持久化到设置偏好（偏好系统/存档系统）
- 世界生成结果是否需要序列化并下发到客户端（单机/联机架构联动）
