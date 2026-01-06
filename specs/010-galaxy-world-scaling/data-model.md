# Data Model: 星系与世界规模系统完善

**Feature**: [spec.md](./spec.md)  
**Branch**: `010-galaxy-world-scaling`  
**Created**: 2026-01-06

## Overview

本数据模型覆盖：星系规模配置系统（预设档位+自定义范围）、世界六边形区块规模配置系统（预设档位+自定义范围）、星系与区块协调机制、行星轨道系统完善（轨道参数扩展、位置计算、周期计算、多体系统支持、基于物理约束的稳定性检查）、配置验证机制（数值范围+性能阈值）和性能保护机制。

## Entities

### 1) GalaxyScaleConfig（星系规模配置）

| Field | Type | Rules |
|------|------|-------|
| presetId | String | 可选；引用预设档位 ID（如 `small`/`medium`/`large`）；与 customRange 互斥 |
| customRange | GalaxyScaleRange | 可选；自定义范围；与 presetId 互斥 |
| spaceRange | SpaceRange | 可选；空间范围（最小/最大坐标范围） |

**说明**: 
- 支持预设档位或自定义范围两种方式
- 预设档位提供快捷选项，自定义范围提供完整灵活性
- 配置加载器负责解析预设档位或自定义范围，合并为最终配置

### 2) GalaxyScaleRange（星系规模范围）

| Field | Type | Rules |
|------|------|-------|
| minStarSystems | int | 必填；最小恒星系统数量；>= 1 |
| maxStarSystems | int | 必填；最大恒星系统数量；>= minStarSystems |
| defaultStarSystems | int | 可选；默认恒星系统数量；在 [minStarSystems, maxStarSystems] 范围内 |

### 3) SpaceRange（空间范围）

| Field | Type | Rules |
|------|------|-------|
| minX | float | 可选；最小 X 坐标 |
| maxX | float | 可选；最大 X 坐标；>= minX |
| minY | float | 可选；最小 Y 坐标 |
| maxY | float | 可选；最大 Y 坐标；>= minY |

### 4) ScalePreset（规模预设档位定义）

| Field | Type | Rules |
|------|------|-------|
| id | String | 主键，如 `small`/`medium`/`large` |
| displayName | String | 显示名 |
| starSystemRange | GalaxyScaleRange | 必填；恒星系统数量范围 |
| spaceRange | SpaceRange | 可选；空间范围 |

> 预设定义应为数据驱动（例如资源文件/配置文件），避免硬编码与硬枚举。

### 5) WorldBlockScaleConfig（世界区块规模配置）

| Field | Type | Rules |
|------|------|-------|
| presetId | String | 可选；引用预设档位 ID（如 `small`/`medium`/`large`）；与 customRange 互斥 |
| customRange | WorldBlockScaleRange | 可选；自定义范围；与 presetId 互斥 |

**说明**: 
- 支持预设档位或自定义范围两种方式
- 预设档位提供快捷选项，自定义范围提供完整灵活性

### 6) WorldBlockScaleRange（世界区块规模范围）

| Field | Type | Rules |
|------|------|-------|
| width | int | 必填；区块宽度（六边形数量）；>= 1 |
| height | int | 必填；区块高度（六边形数量）；>= 1 |
| blockSize | float | 可选；单个区块的大小（单位：游戏单位）；> 0 |

### 7) ScaleValidationConfig（规模验证配置）

| Field | Type | Rules |
|------|------|-------|
| galaxyScaleLimits | GalaxyScaleLimits | 必填；星系规模限制 |
| blockScaleLimits | BlockScaleLimits | 必填；区块规模限制 |
| performanceThresholds | PerformanceThresholds | 必填；性能阈值 |

### 8) GalaxyScaleLimits（星系规模限制）

| Field | Type | Rules |
|------|------|-------|
| minStarSystems | int | 必填；最小恒星系统数量；>= 1 |
| maxStarSystems | int | 必填；最大恒星系统数量；>= minStarSystems |
| maxGenerationTimeMs | long | 可选；最大生成时间（毫秒）；> 0 |

### 9) BlockScaleLimits（区块规模限制）

| Field | Type | Rules |
|------|------|-------|
| minWidth | int | 必填；最小宽度；>= 1 |
| maxWidth | int | 必填；最大宽度；>= minWidth |
| minHeight | int | 必填；最小高度；>= 1 |
| maxHeight | int | 必填；最大高度；>= minHeight |
| maxGenerationTimeMs | long | 可选；最大生成时间（毫秒）；> 0 |

### 10) PerformanceThresholds（性能阈值）

| Field | Type | Rules |
|------|------|-------|
| maxGenerationTimeMs | long | 可选；最大生成时间（毫秒）；> 0 |
| maxMemoryUsageMB | long | 可选；最大内存使用（MB）；> 0 |
| validationStrategy | ValidationStrategy | 必填；验证策略（WARN 或 REJECT） |

### 11) ValidationStrategy（验证策略枚举）

| Value | Description |
|-------|-------------|
| WARN | 警告：超出阈值时记录警告，但允许继续生成 |
| REJECT | 拒绝：超出阈值时拒绝生成，返回错误 |

### 12) Orbit（轨道）- 扩展

| Field | Type | Rules |
|------|------|-------|
| centerRef | OrbitCenterRef | 必填；轨道中心引用 |
| semiMajorAxis | float | 必填；半长轴（单位：游戏单位）；> 0 |
| eccentricity | float | 必填；偏心率；0 <= e < 1 |
| inclination | float | 可选；倾角（弧度）；-π/2 <= i <= π/2 |
| longitudeOfAscendingNode | float | 可选；升交点经度（弧度）；0 <= Ω < 2π |
| argumentOfPeriapsis | float | 可选；近地点幅角（弧度）；0 <= ω < 2π |
| trueAnomaly | float | 可选；真近点角（弧度）；0 <= ν < 2π |
| phase | float | 必填；相位（用于简化计算）；0 <= phase < 2π |
| scale | float | 必填；轨道尺度（用于向后兼容）；> 0 |

**说明**: 
- 扩展现有 `Orbit` 类，添加完整的开普勒轨道参数
- 保留 `phase` 和 `scale` 字段以保持向后兼容
- 新参数优先，旧参数作为后备

### 13) OrbitStabilityCheckResult（轨道稳定性检查结果）

| Field | Type | Rules |
|------|------|-------|
| isStable | boolean | 必填；是否稳定 |
| minDistance | float | 可选；最小距离（单位：游戏单位）；> 0 |
| orbitalEnergy | float | 可选；轨道能量；< 0 表示椭圆轨道 |
| collisionRisk | boolean | 可选；碰撞风险 |
| escapeRisk | boolean | 可选；逃逸风险 |
| messages | List<String> | 可选；检查消息列表 |

### 14) GalaxyBlockCoordinationResult（星系与区块协调结果）

| Field | Type | Rules |
|------|------|-------|
| isAdjusted | boolean | 必填；是否进行了调整 |
| originalDensity | float | 可选；原始密度（每区块平均恒星系统数） |
| adjustedDensity | float | 可选；调整后密度 |
| warnings | List<String> | 可选；警告信息列表 |

### 15) WorldGenConfig（世界生成配置）- 扩展

| Field | Type | Rules |
|------|------|-------|
| (现有字段) | ... | ... |
| galaxyScaleConfig | GalaxyScaleConfig | 可选；星系规模配置 |
| worldBlockScaleConfig | WorldBlockScaleConfig | 可选；区块规模配置 |

**说明**: 
- 扩展现有 `WorldGenConfig` 类，添加规模配置字段
- 保持向后兼容，新字段可选

## Relationships

- `GalaxyScaleConfig` → `ScalePreset` (通过 presetId 引用)
- `WorldBlockScaleConfig` → `ScalePreset` (通过 presetId 引用)
- `WorldGenConfig` → `GalaxyScaleConfig` (包含关系)
- `WorldGenConfig` → `WorldBlockScaleConfig` (包含关系)
- `Orbit` → `OrbitCenterRef` (引用关系)
- `ScaleValidationConfig` → `GalaxyScaleLimits` (包含关系)
- `ScaleValidationConfig` → `BlockScaleLimits` (包含关系)
- `ScaleValidationConfig` → `PerformanceThresholds` (包含关系)

## State Transitions

### 规模配置加载流程

1. **初始状态**: 配置未加载
2. **加载预设档位**: 如果指定了 presetId，从配置文件加载预设定义
3. **加载自定义范围**: 如果指定了 customRange，直接使用自定义范围
4. **验证配置**: 使用 `ScaleConfigValidator` 验证配置有效性
5. **完成状态**: 配置加载完成，可用于生成

### 轨道稳定性检查流程

1. **初始状态**: 轨道参数未检查
2. **计算物理量**: 计算最小距离、轨道能量等物理量
3. **应用量化标准**: 应用量化阈值（最小距离 > 碰撞半径的 2 倍，轨道能量 < 0）
4. **检查结果**: 生成 `OrbitStabilityCheckResult`
5. **完成状态**: 检查完成，结果可用

### 星系与区块协调流程

1. **初始状态**: 星系和区块规模配置已加载
2. **检测不匹配**: 检测星系规模与区块规模是否匹配
3. **自动调整**: 如果不匹配，自动调整星系分布密度
4. **记录警告**: 记录调整操作和警告信息
5. **完成状态**: 协调完成，生成结果可用

## Validation Rules

### GalaxyScaleConfig 验证规则

- `presetId` 和 `customRange` 必须且仅能指定一个
- 如果指定 `customRange`，`minStarSystems` >= 1，`maxStarSystems` >= `minStarSystems`
- 如果指定 `spaceRange`，`maxX` >= `minX`，`maxY` >= `minY`

### WorldBlockScaleConfig 验证规则

- `presetId` 和 `customRange` 必须且仅能指定一个
- 如果指定 `customRange`，`width` >= 1，`height` >= 1，`blockSize` > 0

### Orbit 验证规则

- `semiMajorAxis` > 0
- `eccentricity` 满足 0 <= e < 1
- `inclination` 如果指定，满足 -π/2 <= i <= π/2
- `longitudeOfAscendingNode` 如果指定，满足 0 <= Ω < 2π
- `argumentOfPeriapsis` 如果指定，满足 0 <= ω < 2π
- `trueAnomaly` 如果指定，满足 0 <= ν < 2π

### ScaleValidationConfig 验证规则

- `galaxyScaleLimits.minStarSystems` >= 1
- `galaxyScaleLimits.maxStarSystems` >= `galaxyScaleLimits.minStarSystems`
- `blockScaleLimits.minWidth` >= 1，`maxWidth` >= `minWidth`
- `blockScaleLimits.minHeight` >= 1，`maxHeight` >= `minHeight`
- `performanceThresholds.maxGenerationTimeMs` > 0（如果指定）
- `performanceThresholds.maxMemoryUsageMB` > 0（如果指定）
