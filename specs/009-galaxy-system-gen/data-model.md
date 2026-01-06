# Data Model: 星系生成系统增强（009）

> 本文档描述“数据实体与约束”，不绑定具体渲染实现。实体命名与字段仅用于对齐实现拆分与测试口径。

## Entities

### WorldGenConfig（已存在）

- **Represents**: 世界生成配置（地图大小、密度、可居住比例、复杂度等）
- **Key fields (existing)**:
  - `mapSizePresetId`
  - `seedValue`
  - `habitableRatio`
  - `starDensity`
  - `nebulaRatio`
  - `planetComplexity`
- **Rules**:
  - “新生成时加载最新配置”（不要求运行期热更新影响已生成对象）

### WorldMap / HexTile（已存在）

- **Represents**: 六边形世界地图与 tile
- **Rules**:
  - `galaxy` 类型 tile 才生成 `StarSystem`
  - tile 随机源由 `seedValue + coord` 混合得到（同平台同版本确定性）

### StarSystem（扩展：层级归属）

- **Represents**: 单个 tile 内的恒星系统
- **Key fields**:
  - `id`（现有）
  - `stars[]`（现有：目前限制 [1,3]）
  - `orbitCenters[]`（新增建议：可包含“共同质心/子系统节点”）
- **Rules**:
  - 至少覆盖单星与双星场景
  - 双星必须能表达“共同质心”作为轨道中心节点

### Star（扩展：归属与轨道中心）

- **Represents**: 恒星实体
- **Key fields**:
  - `id`（现有）
  - `starTypeId`（现有：数据驱动）
  - `planets[]`（现有：归属该恒星的行星）
  - `orbitCenterId`（新增建议：恒星围绕的中心；用于双星结构表达）

### Planet（扩展：轨道 + 表面网格）

- **Represents**: 行星实体
- **Key fields**:
  - `id`（现有）
  - `planetTypeId`（现有：数据驱动）
  - `orbitIndex`（现有：展示排序）
  - `orbit`（新增：轨道参数与归属）
  - `surfaceMeshRef`（新增：表面网格引用或缓存标识）

### Orbit（新增）

- **Represents**: 行星（或恒星）轨道的抽象描述
- **Key fields**:
  - `centerRef`（轨道中心引用：主星或共同质心/子系统节点）
  - `eccentricity`（偏心率，用于区分圆/椭圆；`0 <= e < 1`）
  - `phase`（初相位）
  - `scale`（等价于半长轴/尺度参数，需 > 0）
  - `inclination`（可选扩展字段；本轮不影响轨道路径输出）
- **Rules**:
  - 本轮轨道路径输出以系统平面（XY）为准

### OrbitPath（新增）

- **Represents**: 可被渲染层消费的“轨道路径描述”
- **Key fields**:
  - `orbitId`
  - `precisionLevel`（低/中/高）
  - `samples[]`（平面路径采样点集，闭合；元素类型复用 `shared/model/Vector2`）
- **Rules**:
  - 低/中/高离散档位
  - 同档位在相同输入下确定性一致

### PlanetSurfaceMesh（新增）

- **Represents**: 行星球面网格拓扑
- **Key fields**:
  - `resolutionLevel`（低/中/高）
  - `tiles[]`（SurfaceTile 集合）
- **Rules**:
  - 仅由六边形与五边形构成
  - 五边形数量固定为 12，其余为六边形
  - 邻接关系必须双向一致

### SurfaceTile（新增）

- **Represents**: 表面网格单元
- **Key fields**:
  - `tileId`（行星内唯一）
  - `tileType`（Hex/Pent）
  - `neighbors[]`（邻接 tileId 列表）

## Determinism & Validation

- **Determinism boundary**: 同一平台/同一游戏版本下，相同配置+种子产出等价结果
- **Conflict handling**:
  - 轨道冲突检测 → 自动修复（重采样/微调）→ 最多重试 3 次 → 失败返回诊断信息
