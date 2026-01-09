# 015 - 真实比例六边形星区与内容分配：数据模型

> 目标：从 spec 中提取实体、字段、关系与约束，为实现与测试提供稳定契约。

## 1. 领域实体

### 1.1 Galaxy（星系）
- **标识**: `galaxyId`（字符串或 UUID）
- **字段**:
  - `radiusR`（int）：六边形半径 R（圈数）
  - `sectors`（List<Sector> 或 Map<SectorId, Sector>）
- **约束**:
  - 总星区数：`1 + 3R(R+1)`

### 1.2 Sector（星区）
- **标识**: `sectorId`（字符串；建议基于 HexCoord 可推导，或独立生成但需可复现）
- **字段**:
  - `hexCoord`（HexCoord：轴向坐标 q,r）
  - `worldCenterKm`（WorldCoordinate：double x,y,z；z=0）
  - `contentTypeId`（string）：数据驱动内容类型 ID，例如 `star-system`（星系星区） / `nebula`（星云） / `deep_space`（深空）
  - `occupancySource`（可选 string）：`preset:<presetId>` / `allocated`（用于调试）
  - `starSystemId`（可选 string）：当 `contentTypeId == "star-system"` 时存在（占位符）

### 1.3 SectorContentTypeDefinition（内容类型定义，数据驱动）
- **标识**: `typeId`（string）
- **字段**（建议最小集合）:
  - `typeId`
  - `displayNameZh`（string）
  - `debugColor`（string，例如 `#RRGGBB`）或 `iconKey`（string）
- **约束**:
  - 本期至少存在：`star-system` / `nebula` / `deep-space`
  - 后续可扩展：`storm` / `deep-space-resource` 等

### 1.4 GalaxyPreset（星系预设，JSON）
- **标识**: `presetId`（string）
- **字段**:
  - `loadOrder`（int）：加载顺序；冲突采用“后来者覆盖”
  - `placementType`（string）：`fixed-hex` / `random-hex`
  - `fixedHexCoords`（可选 List<HexCoord>）
  - `randomCount`（可选 int）
  - `randomConstraints`（可选 object）：例如必须在某半径范围、避开中心等（本期可先不实现复杂约束）
  - `contentTypeId`（string）：该预设占用星区的内容类型（通常 `star-system`）

## 2. 快照/DTO（shared）

> 原则：客户端只消费快照，不直接引用生成器内部模型。

### 2.1 UniverseSnapshot
- `galaxyId`
- `radiusR`
- `sectors: List<SectorSnapshot>`
- `seed`（可选：用于复现与调试）

### 2.2 SectorSnapshot
- `hexQ`, `hexR`
- `worldCenterXKm`, `worldCenterYKm`（以及 `worldCenterZKm`=0 可选）
- `contentTypeId`（string）
- `starSystemId`（可选 string，占位）
- `occupancySource`（可选 string，调试用）

## 3. 关键算法约束（与数据模型绑定）

### 3.1 HexCoord → worldCenterKm
- 输入：HexCoord（q,r）
- 输出：世界坐标（km）
- 约束：相邻星区中心距离与“边长=1ly”的理论值误差 ≤ 1%（SC-002）

### 3.2 分配确定性
- 分配处理 MUST：
  - 对剩余星区按 HexCoord 稳定排序
  - 使用 `seed + HexCoord` 派生随机（无共享 RNG）

### 3.3 剩余不足截断
- 固定顺序：恒星系 → 星云 → 深空
- 策略：尽量分配，不足则截断，并记录日志/调试提示
