# Data Model: 008 Server/Client Separation & Communication

**Created**: 2026-01-06  
**Feature**: [spec.md](./spec.md)

## Overview

本特性的数据模型以“跨端契约 DTO”为中心：客户端发送请求 DTO，服务端返回响应 DTO（成功时包含 WorldSnapshot，失败时包含 ErrorEnvelope）。

所有 DTO：

- 字段命名使用英文（camelCase），避免拼音。
- 响应必须包含 `schemaVersion`，用于兼容门禁；MVP 固定为 `worldgen_v1`。

辅助组件（非 DTO）：

- `WorldSnapshotConverter`：用于将 `WorldSnapshot` 转换为客户端可渲染的 `WorldMap` 视图数据结构。该组件不属于跨端协议体本身，但属于“消费快照”的必要适配层。

## StartNewGameRequest

**Purpose**: 客户端请求服务端生成世界。

**Fields**:

- `seedText` (string, required)
  - 约束：长度上限（建议 0..256）。
  - 规则：当 seedText 为 null/空字符串/仅空白时，服务端生成随机 seedValue（不可复现）。当 seedText 为非空字符串时，服务端进行确定性解析（同字符串 -> 同 seedValue）。
  - 说明：最终 `seedValue` 由服务端权威解析并回填到 WorldSnapshot。
- `mapSizePresetId` (string, required)
- `habitableRatio` (number, required)
  - 约束：clamp 到 [0, 1]
- `starDensity` (number, required)
  - 约束：clamp 到 [0, 1]
- `planetComplexity` (number, required)
  - 约束：clamp 到 [0, 1]
- `nebulaRatio` (number, required)
  - 约束：clamp 到 [0, 1]

## StartNewGameResponse

**Purpose**: 服务端返回生成结果或错误。

**Fields**:

- `schemaVersion` (string, required)
- `effectiveConfig` (StartNewGameEffectiveConfig, optional)
- `world` (WorldSnapshot, optional)
- `error` (ErrorEnvelope, optional)

**Invariant**:

- 成功：`world` 非空，`error` 为空。
- 成功：`world` 非空，`error` 为空；`effectiveConfig` 非空，用于回填服务端最终采用的有效参数（例如 clamp 后的比例、最终 seedValue）。
- 失败：`error` 非空，`world` 为空。

## StartNewGameEffectiveConfig

**Purpose**: 成功响应中回填服务端最终采用的有效参数，便于客户端展示与验收对比。

**Fields**:

- `mapSizePresetId` (string, required)
- `seedText` (string, required)
- `seedValue` (integer/long, required)
- `habitableRatio` (number, required)
- `starDensity` (number, required)
- `planetComplexity` (number, required)
- `nebulaRatio` (number, required)

## WorldSnapshot

**Purpose**: 服务端权威世界快照，用于客户端渲染。

**Fields**:

- `seedValue` (integer/long, required)
- `boundsRadius` (integer, required)
- `stats` (WorldGenStatsSnapshot, required)
- `tiles` (array<HexTileSnapshot>, required)

## HexTileSnapshot

**Fields**:

- `coord` (HexCoordSnapshot, required)
- `typeId` (string, required)
- `hasHabitable` (boolean, required)
- `starSystem` (StarSystemSnapshot, optional)

## HexCoordSnapshot

**Fields**:

- `x` (integer, required)
- `y` (integer, required)
- `z` (integer, required)

## StarSystemSnapshot

**Fields**:

- `id` (string, required)
- `stars` (array<StarSnapshot>, required)

**Constraints**:

- `stars` 数量范围：1..3

## StarSnapshot

**Fields**:

- `id` (string, required)
- `starTypeId` (string, required)
- `planets` (array<PlanetSnapshot>, required)

## PlanetSnapshot

**Fields**:

- `id` (string, required)
- `planetTypeId` (string, required)
- `orbitIndex` (integer, required)

## WorldGenStatsSnapshot

**Fields**:

- `tileCount` (integer, required)
- `sectorCounts` (map<string, integer>, required)
- `galaxyTileCount` (integer, required)
- `starCount` (integer, required)
- `planetCount` (integer, required)
- `starsPerSystemMinMax` (string, required)

## ErrorEnvelope

**Purpose**: 结构化错误对象；客户端基于 `messageKey` 做本地化展示。

**Fields**:

- `errorCode` (string, required)
- `messageKey` (string, required)
- `details` (string, optional)

**Notes**:

- `details` 仅用于诊断与日志，不直接展示给玩家。
