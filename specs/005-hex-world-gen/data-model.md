# Data Model: Hex World Generation

**Feature**: [spec.md](./spec.md)  
**Branch**: `005-hex-world-gen`  
**Created**: 2026-01-05

## Overview

本数据模型覆盖：六边形网格、瓦片类型、世界生成配置（新游戏配置），以及生成后的世界输出。

## Entities

### 1) WorldGenConfig（世界生成配置）

| Field | Type | Rules |
|------|------|-------|
| mapSizePresetId | string | 必填；引用地图大小预设（定义六边形半径 R） |
| habitableRatio | float | 必填；范围 0.0-1.0；用于星系格决定是否生成宜居特性 |
| seedText | string | 可选；玩家输入字符串；空表示随机 |
| seedValue | long | 必填；由 seedText 转换得到；空 seedText 则随机生成 |
| aiCount | int | 预留；UI 显示但不可编辑；当前版本不生效 |
| techLevelPresetId | string | 预留；UI 显示但不可编辑；当前版本不生效 |

### 2) MapSizePreset（地图大小预设）

| Field | Type | Rules |
|------|------|-------|
| id | string | 主键，如 `small`/`medium`/`large` |
| radius | int | 六边形边界半径 R（中心到边界格数） |

> 预设定义应为数据驱动（例如资源文件/配置文件），避免硬编码与硬枚举。

### 3) TechLevelPreset（技术等级预设，预留）

| Field | Type | Rules |
|------|------|-------|
| id | string | 主键（预留） |
| displayName | string | 预留 |

### 4) HexCoord（六边形立方体坐标）

| Field | Type | Rules |
|------|------|-------|
| x | int | 必填 |
| y | int | 必填 |
| z | int | 必填；恒满足 x + y + z = 0 |

### 5) TileTypeDef（瓦片类型定义）

| Field | Type | Rules |
|------|------|-------|
| id | string | 主键，如 `galaxy`/`deep_space`/`nebula` |
| displayName | string | 显示名 |
| weight | float | 生成分布权重（首版可选） |

### 6) HexTile（六边形单元）

| Field | Type | Rules |
|------|------|-------|
| coord | HexCoord | 主键（在一个世界内唯一） |
| typeId | string | 必填；引用 TileTypeDef.id |
| hasHabitable | boolean | 仅当 typeId 对应“星系格”时可能为 true；其他类型恒为 false |

### 7) WorldMap（生成后的世界）

| Field | Type | Rules |
|------|------|-------|
| config | WorldGenConfig | 生成输入快照 |
| tiles | map<HexCoord, HexTile> | coord 唯一；数量由 mapSizePresetId 对应的 radius 决定 |
| boundsRadius | int | 世界边界半径 R（封闭边界） |

## Relationships

- WorldGenConfig 作为输入 → 生成 WorldMap。
- WorldMap 包含多个 HexTile。
- HexTile 通过 HexCoord 唯一定位。

## Validation

- `habitableRatio` 必须被 clamp 到 [0,1]。
- `seedText` 允许为空；为空时生成随机 `seedValue` 并用于本局显示（可选）。
- `HexCoord` 必须满足 x+y+z=0。

## State Transitions

- 新游戏配置界面：编辑 WorldGenConfig（部分字段只读/禁用）。
- 点击开始：冻结 WorldGenConfig → 生成 WorldMap → 进入世界渲染/交互。
