# Data Model: 真实天文单位系统重构

**Feature**: [spec.md](./spec.md)  
**Branch**: `011-astronomical-units`  
**Created**: 2026-01-07

## Overview

本数据模型覆盖：天文单位系统（定点数表示）、单位转换器、星区/轨道/恒星/行星大小定义、可视化缩放配置、迁移工具数据结构。

## Entities

### 1) AstronomicalUnit（天文单位）

**作用**：使用定点数表示天文单位值，确保确定性和高性能。

| Field | Type | Rules |
|------|------|-------|
| internalUnits | long | 必填；内部单位值（1 AU = 10^12 内部单位）；范围：-2^63 到 2^63-1 |
| scaleFactor | long | 常量；缩放因子 = 10^12；不可修改 |

**方法**：
- `fromAU(double auValue)`: 从 AU 值创建 AstronomicalUnit
- `toAU()`: 转换为 AU 值（double）
- `toLightYears()`: 转换为光年值
- `toParsecs()`: 转换为秒差距值
- `add(AstronomicalUnit other)`: 加法运算
- `subtract(AstronomicalUnit other)`: 减法运算
- `multiply(long factor)`: 乘法运算（整数因子）
- `divide(long divisor)`: 除法运算（整数除数）

**验证规则**：
- `internalUnits` 必须在 long 类型范围内
- 转换时检查溢出（使用单位转换避免）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`

---

### 2) UnitConverter（单位转换器）

**作用**：处理 AU、光年、秒差距之间的转换。

| Field | Type | Rules |
|------|------|-------|
| (无状态字段) | - | 工具类，所有方法为静态方法 |

**方法**：
- `auToLightYears(AstronomicalUnit au)`: AU 转光年
- `lightYearsToAU(AstronomicalUnit ly)`: 光年转 AU
- `auToParsecs(AstronomicalUnit au)`: AU 转秒差距
- `parsecsToAU(AstronomicalUnit pc)`: 秒差距转 AU
- `convert(AstronomicalUnit value, String fromUnit, String toUnit)`: 通用转换方法

**转换常数**（常量）：
- `AU_TO_KM = 1.496 × 10^8`（公里）
- `LY_TO_AU = 63,241.077`（精确值）
- `PC_TO_AU = 206,265`（精确值）

**验证规则**：
- 转换精度必须达到 99.9% 以上
- 转换结果必须在合理范围内

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`

---

### 3) SectorSizeDefinition（星区大小定义）

**作用**：定义星区的大小，基于天文单位。

| Field | Type | Rules |
|------|------|-------|
| sizeInAU | AstronomicalUnit | 必填；星区大小（以 AU 为单位）；默认 1 光年 = 63,241 AU |
| isConfigurable | boolean | 必填；是否可配置；默认 true |

**方法**：
- `getSizeInAU()`: 获取星区大小（AU）
- `getSizeInLightYears()`: 获取星区大小（光年）
- `setSizeInAU(AstronomicalUnit size)`: 设置星区大小

**验证规则**：
- `sizeInAU` 必须 > 0
- 星区大小必须能够合理容纳一个恒星系（建议 >= 1 光年）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/SectorSizeDefinition.java`

**配置文件**：`assets/i18n/sector-size-config.properties`
```properties
# 默认星区大小（光年）
sector.size.default.lightYears=1.0

# 是否可配置
sector.size.configurable=true
```

---

### 4) OrbitSizeDefinition（轨道大小定义）

**作用**：定义轨道半长轴的大小，基于天文单位。

| Field | Type | Rules |
|------|------|-------|
| semiMajorAxis | AstronomicalUnit | 必填；轨道半长轴（以 AU 为单位）；> 0 |
| minValue | AstronomicalUnit | 可选；最小轨道半长轴；默认 0.1 AU |
| maxValue | AstronomicalUnit | 可选；最大轨道半长轴；默认 100 AU |

**方法**：
- `getSemiMajorAxis()`: 获取轨道半长轴
- `setSemiMajorAxis(AstronomicalUnit axis)`: 设置轨道半长轴
- `validate()`: 验证轨道大小在合理范围内

**验证规则**：
- `semiMajorAxis` 必须在 [minValue, maxValue] 范围内
- 必须符合真实行星系统的比例关系（例如地球轨道 = 1 AU，木星轨道 ≈ 5.2 AU）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/OrbitSizeDefinition.java`

**配置文件**：`assets/i18n/orbit-size-config.properties`
```properties
# 轨道半长轴范围（AU）
orbit.size.min.au=0.1
orbit.size.max.au=100.0

# 默认轨道半长轴（按轨道索引）
orbit.size.default.base.au=1.0
orbit.size.default.increment.au=1.25
```

---

### 5) StarSizeDefinition（恒星大小定义）

**作用**：定义恒星半径的大小，基于天文单位，按类型分类。

| Field | Type | Rules |
|------|------|-------|
| starTypeId | String | 必填；恒星类型标识（如 "yellow_dwarf", "red_giant"） |
| radiusInAU | AstronomicalUnit | 必填；恒星半径（以 AU 为单位）；> 0 |
| minRadius | AstronomicalUnit | 可选；该类型的最小半径 |
| maxRadius | AstronomicalUnit | 可选；该类型的最大半径 |

**方法**：
- `getRadiusInAU()`: 获取恒星半径
- `setRadiusInAU(AstronomicalUnit radius)`: 设置恒星半径
- `validate()`: 验证恒星大小在合理范围内

**验证规则**：
- `radiusInAU` 必须在 [minRadius, maxRadius] 范围内（如果定义了范围）
- 必须符合真实恒星的物理特性（例如太阳半径 ≈ 0.00465 AU，红巨星半径可达 1-10 AU）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/StarSizeDefinition.java`

**配置文件**：`assets/i18n/star-size-config.properties`
```properties
# 恒星类型大小定义（半径，AU）
star.size.yellow_dwarf.radius.au=0.00465
star.size.red_giant.min.radius.au=1.0
star.size.red_giant.max.radius.au=10.0
star.size.white_dwarf.radius.au=0.0001
```

---

### 6) PlanetSizeDefinition（行星大小定义）

**作用**：定义行星半径的大小，基于天文单位，按类型分类。

| Field | Type | Rules |
|------|------|-------|
| planetTypeId | String | 必填；行星类型标识（如 "rocky", "gas_giant"） |
| radiusInAU | AstronomicalUnit | 必填；行星半径（以 AU 为单位）；> 0 |
| minRadius | AstronomicalUnit | 可选；该类型的最小半径 |
| maxRadius | AstronomicalUnit | 可选；该类型的最大半径 |

**方法**：
- `getRadiusInAU()`: 获取行星半径
- `setRadiusInAU(AstronomicalUnit radius)`: 设置行星半径
- `validate()`: 验证行星大小在合理范围内

**验证规则**：
- `radiusInAU` 必须在 [minRadius, maxRadius] 范围内（如果定义了范围）
- 必须符合真实行星的物理特性（例如地球半径 ≈ 0.0000426 AU，木星半径 ≈ 0.000477 AU）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/PlanetSizeDefinition.java`

**配置文件**：`assets/i18n/planet-size-config.properties`
```properties
# 行星类型大小定义（半径，AU）
planet.size.rocky.radius.au=0.0000426
planet.size.gas_giant.min.radius.au=0.0001
planet.size.gas_giant.max.radius.au=0.001
```

---

### 7) VisualScaleConfig（可视化缩放配置）

**作用**：定义逻辑单位到渲染单位的转换比例和可视化缩放参数。

| Field | Type | Rules |
|------|------|-------|
| auToPixels | float | 必填；AU 到像素的转换比例；> 0 |
| autoScaleEnabled | boolean | 必填；是否启用自动缩放；默认 true |
| manualScaleFactor | float | 可选；手动缩放因子；默认 1.0；> 0 |
| starBaseScale | float | 可选；恒星基础缩放比例；默认 1.0；> 0 |
| planetBaseScale | float | 可选；行星基础缩放比例；默认 0.5；> 0 |
| orbitBaseScale | float | 可选；轨道基础缩放比例；默认 1.0；> 0 |

**方法**：
- `getEffectiveScaleFactor()`: 获取有效缩放因子（自动或手动）
- `setManualScaleFactor(float factor)`: 设置手动缩放因子
- `resetToAuto()`: 重置为自动缩放

**验证规则**：
- 所有缩放因子必须 > 0
- `auToPixels` 必须基于当前系统确定（默认值需计算）

**位置**：`shared/src/main/java/com/staraxis/game/shared/world/astronomical/VisualScaleConfig.java`

**配置文件**：`assets/i18n/visual-scale-config.properties`
```properties
# AU 到像素的转换比例（默认值，可配置）
visual.scale.au.to.pixels=0.00079

# 自动缩放设置
visual.scale.auto.enabled=true

# 基础缩放比例
visual.scale.star.base=1.0
visual.scale.planet.base=0.5
visual.scale.orbit.base=1.0
```

---

### 8) AstronomicalUnitSystem（天文单位系统）

**作用**：天文单位系统的核心类，负责配置加载和初始化。

| Field | Type | Rules |
|------|------|-------|
| sectorSizeDefinition | SectorSizeDefinition | 必填；星区大小定义 |
| visualScaleConfig | VisualScaleConfig | 必填；可视化缩放配置 |
| unitConverter | UnitConverter | 常量；单位转换器（工具类） |

**方法**：
- `loadFromConfig()`: 从配置文件加载系统配置
- `getSectorSizeDefinition()`: 获取星区大小定义
- `getVisualScaleConfig()`: 获取可视化缩放配置
- `validate()`: 验证系统配置的合理性

**位置**：`core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`

---

### 9) MigrationTool（迁移工具）

**作用**：将现有游戏数据迁移到新单位系统。

| Field | Type | Rules |
|------|------|-------|
| sourceVersion | String | 必填；源数据版本 |
| targetVersion | String | 必填；目标数据版本（新单位系统版本） |
| conversionRatio | double | 必填；旧单位到新单位的转换比例 |

**方法**：
- `migrateSaveFile(String filePath)`: 迁移单个存档文件
- `migrateDirectory(String dirPath)`: 批量迁移目录中的所有存档
- `validateMigration(String filePath)`: 验证迁移结果
- `backupOriginal(String filePath)`: 备份原始文件

**验证规则**：
- 迁移前必须备份原始数据
- 迁移后必须验证数据完整性
- 转换比例必须正确（需要根据当前系统确定）

**位置**：`core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`

---

## Relationships

- `AstronomicalUnitSystem` → `SectorSizeDefinition` (包含关系)
- `AstronomicalUnitSystem` → `VisualScaleConfig` (包含关系)
- `SectorSizeDefinition` → `AstronomicalUnit` (使用关系)
- `OrbitSizeDefinition` → `AstronomicalUnit` (使用关系)
- `StarSizeDefinition` → `AstronomicalUnit` (使用关系)
- `PlanetSizeDefinition` → `AstronomicalUnit` (使用关系)
- `UnitConverter` → `AstronomicalUnit` (转换关系)
- `MigrationTool` → `AstronomicalUnitSystem` (使用关系)

---

## State Transitions

### 天文单位系统初始化流程

1. **初始状态**: 系统未初始化
2. **加载配置**: 从配置文件加载所有大小定义和缩放配置
3. **验证配置**: 验证所有配置的合理性
4. **完成状态**: 系统初始化完成，可用于计算和转换

### 数据迁移流程

1. **初始状态**: 旧格式数据存在
2. **检测版本**: 识别数据版本
3. **备份数据**: 创建原始数据备份
4. **执行转换**: 根据转换比例转换所有距离和大小值
5. **验证结果**: 验证转换后的数据完整性和合理性
6. **完成状态**: 迁移完成，旧格式不再支持

---

## Validation Rules

### AstronomicalUnit 验证规则

- `internalUnits` 必须在 long 类型范围内
- 转换时检查溢出，使用单位转换避免

### SectorSizeDefinition 验证规则

- `sizeInAU` 必须 > 0
- 星区大小建议 >= 1 光年（能够容纳恒星系）

### OrbitSizeDefinition 验证规则

- `semiMajorAxis` 必须在 [minValue, maxValue] 范围内
- 必须符合真实行星系统的比例关系

### StarSizeDefinition 验证规则

- `radiusInAU` 必须在 [minRadius, maxRadius] 范围内（如果定义了范围）
- 必须符合真实恒星的物理特性

### PlanetSizeDefinition 验证规则

- `radiusInAU` 必须在 [minRadius, maxRadius] 范围内（如果定义了范围）
- 必须符合真实行星的物理特性

### VisualScaleConfig 验证规则

- 所有缩放因子必须 > 0
- `auToPixels` 必须基于当前系统确定

### MigrationTool 验证规则

- 迁移前必须备份原始数据
- 迁移后必须验证数据完整性
- 转换比例必须正确

---

## Integration Points

### 与现有系统的集成

1. **WorldGenConfig 扩展**：
   - 添加 `astronomicalUnitSystem` 字段
   - 在生成时使用新的单位系统

2. **Orbit 类扩展**：
   - `semiMajorAxis` 字段改为使用 `AstronomicalUnit`
   - 保持向后兼容（通过迁移工具）

3. **Star 和 Planet 类扩展**：
   - 添加 `radius` 字段（`AstronomicalUnit` 类型）
   - 从类型定义中加载大小

4. **HexTile 集成**：
   - 星区大小使用 `SectorSizeDefinition`
   - 渲染时使用 `VisualScaleConfig`

5. **渲染系统集成**：
   - `HexGridRenderer` 使用新的转换比例
   - `StellarMarkerRenderer` 使用可视化缩放配置
