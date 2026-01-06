# Data Model: Stellar & Planet Generation

**Feature**: [spec.md](./spec.md)  
**Branch**: `007-stellar-planet-gen`  
**Created**: 2026-01-06

## Overview

本数据模型覆盖：世界生成配置（新游戏配置扩展）、六边形星域区块、星系系统（多恒星）与行星归属，以及用于回归验证的生成统计。

## Entities

### 1) WorldGenConfig（世界生成配置）

| Field | Type | Rules |
|------|------|-------|
| mapSizePresetId | string | 必填；引用地图大小预设（定义六边形半径 R；封闭边界） |
| seedText | string | 可选；玩家输入字符串；空表示随机 |
| seedValue | long | 必填；由 seedText 转换得到；空 seedText 则随机生成 |
| starDensity | float | 必填；范围 0.0-1.0；影响“星系类区块/含恒星区块”的数量/占比 |
| planetComplexity | float | 必填；范围 0.0-1.0；影响每颗恒星的行星数量倾向/复杂度 |
| nebulaRatio | float | 必填；范围 0.0-1.0；影响星云区块占比 |

### 2) MapSizePreset（地图大小预设）

| Field | Type | Rules |
|------|------|-------|
| id | string | 主键，如 `small`/`medium`/`large` |
| radius | int | 六边形边界半径 R（中心到边界格数；封闭边界） |

> 预设定义应为数据驱动（例如资源文件/配置文件），避免硬编码与硬枚举。

### 3) HexCoord（六边形立方体坐标）

| Field | Type | Rules |
|------|------|-------|
| x | int | 必填 |
| y | int | 必填 |
| z | int | 必填；恒满足 x + y + z = 0 |

### 4) SectorTypeDef（星域类型定义）

| Field | Type | Rules |
|------|------|-------|
| id | string | 主键，如 `galaxy`/`deep_space`/`nebula` |
| displayName | string | 显示名 |
| weight | float | 生成权重（可选；若采用权重分布） |

### 5) HexTile（六边形星域单元）

| Field | Type | Rules |
|------|------|-------|
| coord | HexCoord | 主键（在一个世界内唯一） |
| sectorTypeId | string | 必填；引用 SectorTypeDef.id |
| starSystem | StarSystem? | 仅当 `sectorTypeId == galaxy` 时可存在；否则必须为 null |

### 6) StarSystem（星系系统）

| Field | Type | Rules |
|------|------|-------|
| id | string | 在世界内唯一（生成时可使用 coord 派生或随机但需确定性） |
| stars | list<Star> | 必填；长度 1..3 |

### 7) Star（恒星）

| Field | Type | Rules |
|------|------|-------|
| id | string | 在所属 StarSystem 内唯一 |
| starTypeId | string | 恒星类型标识（数据驱动；首版可简化为少量类型） |
| planets | list<Planet> | 行星必须归属到某一颗恒星；列表可为空 |

### 8) Planet（行星）

| Field | Type | Rules |
|------|------|-------|
| id | string | 在所属 Star 内唯一 |
| planetTypeId | string | 行星类型标识（数据驱动；首版可简化） |
| orbitIndex | int | 可选；用于 UI 排序/展示；必须 >= 0 |

### 9) WorldMap（生成后的世界）

| Field | Type | Rules |
|------|------|-------|
| config | WorldGenConfig | 生成输入快照 |
| tiles | map<HexCoord, HexTile> | coord 唯一；数量由 mapSizePresetId 对应的 radius 决定 |
| boundsRadius | int | 世界边界半径 R（封闭边界） |
| stats | WorldGenStats? | 生成统计，用于回归验证；生成完成后必须非空 |

### 10) WorldGenStats（生成统计）

| Field | Type | Rules |
|------|------|-------|
| tileCount | int | 总格子数量 |
| sectorCounts | map<string,int> | 按 sectorTypeId 统计数量（key 为 `galaxy`/`deep_space`/`nebula`） |
| galaxyTileCount | int | 星系区块数量 |
| starCount | int | 恒星总数 |
| planetCount | int | 行星总数 |
| starsPerSystemMinMax | string | 可选；如 "min=1,max=3"（便于日志） |

## Relationships

- WorldGenConfig 作为输入 → 生成 WorldMap。
- WorldMap 包含多个 HexTile（以 HexCoord 唯一定位）。
- `galaxy` 类型的 HexTile 关联 1 个 StarSystem。
- StarSystem 包含 1..3 个 Star。
- 每个 Planet 必须归属到某一颗 Star。

## Validation

- `starDensity`、`planetComplexity`、`nebulaRatio` 必须 clamp 到 [0,1]。
- `HexCoord` 必须满足 x+y+z=0。
- `HexTile.starSystem` 只允许出现在 `sectorTypeId == galaxy`。
- `StarSystem.stars` 长度必须在 [1,3]。

## State Transitions

- 新游戏配置界面：编辑 WorldGenConfig。
- 点击开始：冻结 WorldGenConfig → 生成 WorldMap（含 stats）→ 进入世界渲染/交互。
