# Quickstart: 真实天文单位系统重构

**Feature**: [spec.md](./spec.md)  
**Branch**: `011-astronomical-units`  
**Created**: 2026-01-07

## 概述

本快速开始指南介绍如何使用新的天文单位系统，包括配置、使用和迁移现有数据。

---

## 1. 配置天文单位系统

### 1.1 基础配置

编辑 `assets/i18n/astronomical-units-config.properties`：

```properties
# 天文单位基础定义
astronomical.unit.au.to.km=149600000.0
astronomical.unit.ly.to.au=63241.077
astronomical.unit.pc.to.au=206265.0

# 定点数缩放因子
astronomical.unit.scale.factor=1000000000000
```

### 1.2 星区大小配置

编辑 `assets/i18n/sector-size-config.properties`：

```properties
# 默认星区大小（光年）
sector.size.default.lightYears=1.0

# 是否可配置
sector.size.configurable=true
```

### 1.3 可视化缩放配置

编辑 `assets/i18n/visual-scale-config.properties`：

```properties
# AU 到像素的转换比例
visual.scale.au.to.pixels=0.00079

# 自动缩放设置
visual.scale.auto.enabled=true

# 基础缩放比例
visual.scale.star.base=1.0
visual.scale.planet.base=0.5
visual.scale.orbit.base=1.0
```

---

## 2. 使用天文单位系统

### 2.1 创建天文单位值

```java
// 从 AU 值创建
AstronomicalUnit distance = AstronomicalUnit.fromAU(1.0);  // 1 AU

// 从光年值创建
AstronomicalUnit distance = AstronomicalUnit.fromLightYears(1.0);  // 1 光年

// 从秒差距值创建
AstronomicalUnit distance = AstronomicalUnit.fromParsecs(1.0);  // 1 秒差距
```

### 2.2 单位转换

```java
// 使用 UnitConverter
AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
AstronomicalUnit ly = UnitConverter.auToLightYears(au);
double lyValue = ly.toLightYears();

// 通用转换方法
AstronomicalUnit result = UnitConverter.convert(
    AstronomicalUnit.fromAU(1.0),
    "AU",
    "ly"
);
```

### 2.3 算术运算

```java
AstronomicalUnit a = AstronomicalUnit.fromAU(1.0);
AstronomicalUnit b = AstronomicalUnit.fromAU(2.0);

// 加法
AstronomicalUnit sum = a.add(b);  // 3 AU

// 减法
AstronomicalUnit diff = b.subtract(a);  // 1 AU

// 乘法（整数因子）
AstronomicalUnit product = a.multiply(5);  // 5 AU

// 除法（整数除数）
AstronomicalUnit quotient = b.divide(2);  // 1 AU
```

---

## 3. 使用大小定义

### 3.1 星区大小

```java
// 加载星区大小定义
AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
SectorSizeDefinition sectorSize = system.getSectorSizeDefinition();

// 获取星区大小
AstronomicalUnit size = sectorSize.getSizeInAU();
double sizeInLY = sectorSize.getSizeInLightYears();

// 修改星区大小（如果可配置）
sectorSize.setSizeInAU(AstronomicalUnit.fromLightYears(2.0));
```

### 3.2 轨道大小

```java
// 创建轨道大小定义
OrbitSizeDefinition orbitSize = new OrbitSizeDefinition();
orbitSize.setSemiMajorAxis(AstronomicalUnit.fromAU(1.0));  // 地球轨道

// 验证轨道大小
orbitSize.validate();
```

### 3.3 恒星大小

```java
// 从配置加载恒星大小定义
StarSizeDefinition starSize = StarSizeDefinition.loadFromConfig("yellow_dwarf");
AstronomicalUnit radius = starSize.getRadiusInAU();  // 约 0.00465 AU（太阳）
```

### 3.4 行星大小

```java
// 从配置加载行星大小定义
PlanetSizeDefinition planetSize = PlanetSizeDefinition.loadFromConfig("rocky");
AstronomicalUnit radius = planetSize.getRadiusInAU();  // 约 0.0000426 AU（地球）
```

---

## 4. 可视化缩放

### 4.1 获取缩放配置

```java
AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
VisualScaleConfig scaleConfig = system.getVisualScaleConfig();

// 获取有效缩放因子
float scaleFactor = scaleConfig.getEffectiveScaleFactor();

// 获取 AU 到像素的转换比例
float auToPixels = scaleConfig.getAuToPixels();
```

### 4.2 手动调整缩放

```java
// 设置手动缩放因子
scaleConfig.setManualScaleFactor(2.0f);  // 放大 2 倍

// 重置为自动缩放
scaleConfig.resetToAuto();
```

### 4.3 渲染转换

```java
// 在渲染器中转换
AstronomicalUnit distance = AstronomicalUnit.fromAU(1.0);
float pixels = distance.toAU() * scaleConfig.getAuToPixels() * scaleConfig.getEffectiveScaleFactor();
```

---

## 5. 数据迁移

### 5.1 迁移单个存档

```java
MigrationTool migrationTool = new MigrationTool();
migrationTool.setSourceVersion("1.0");
migrationTool.setTargetVersion("2.0");
migrationTool.setConversionRatio(1.0);  // 需要根据当前系统确定

// 迁移存档文件
String saveFilePath = "saves/game1.sav";
migrationTool.migrateSaveFile(saveFilePath);

// 验证迁移结果
boolean isValid = migrationTool.validateMigration(saveFilePath);
```

### 5.2 批量迁移

```java
// 迁移整个目录
String savesDir = "saves/";
migrationTool.migrateDirectory(savesDir);
```

### 5.3 迁移前备份

```java
// 迁移工具会自动备份，也可以手动备份
migrationTool.backupOriginal(saveFilePath);
```

---

## 6. 集成到现有系统

### 6.1 更新 WorldGenConfig

```java
WorldGenConfig config = new WorldGenConfig();
AstronomicalUnitSystem auSystem = AstronomicalUnitSystem.loadFromConfig();
// 注意：WorldGenConfig 需要扩展以支持 AstronomicalUnitSystem
// config.setAstronomicalUnitSystem(auSystem);
```

### 6.2 更新轨道生成

```java
// 在 OrbitParamSampler 中使用新的单位系统
OrbitSizeDefinition orbitSize = new OrbitSizeDefinition();
orbitSize.setSemiMajorAxis(AstronomicalUnit.fromAU(1.0 + orbitIndex * 1.25));

Orbit orbit = new Orbit();
orbit.setSemiMajorAxis(orbitSize.getSemiMajorAxis().toAU());
```

### 6.3 更新恒星/行星生成

```java
// 在 StellarGenerator 中使用新的单位系统
StarSizeDefinition starSize = StarSizeDefinition.loadFromConfig(starTypeId);
AstronomicalUnit radius = starSize.getRadiusInAU();

PlanetSizeDefinition planetSize = PlanetSizeDefinition.loadFromConfig(planetTypeId);
AstronomicalUnit radius = planetSize.getRadiusInAU();
```

---

## 7. 测试示例

### 7.1 单位转换测试

```java
@Test
void testUnitConversion() {
    AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
    AstronomicalUnit ly = UnitConverter.auToLightYears(au);
    
    // 验证转换精度
    double expectedLY = 1.0 / 63241.077;
    assertEquals(expectedLY, ly.toLightYears(), 0.0001);
}
```

### 7.2 确定性测试

```java
@Test
void testDeterminism() {
    AstronomicalUnit a1 = AstronomicalUnit.fromAU(1.0);
    AstronomicalUnit a2 = AstronomicalUnit.fromAU(1.0);
    AstronomicalUnit sum1 = a1.add(a2);
    
    // 使用相同输入再次计算
    AstronomicalUnit a3 = AstronomicalUnit.fromAU(1.0);
    AstronomicalUnit a4 = AstronomicalUnit.fromAU(1.0);
    AstronomicalUnit sum2 = a3.add(a4);
    
    // 验证结果一致
    assertEquals(sum1.getInternalUnits(), sum2.getInternalUnits());
}
```

### 7.3 迁移测试

```java
@Test
void testMigration() {
    MigrationTool tool = new MigrationTool();
    tool.setConversionRatio(1.0);
    
    // 创建测试存档
    String testFile = "test.sav";
    createTestSaveFile(testFile);
    
    // 执行迁移
    tool.migrateSaveFile(testFile);
    
    // 验证迁移结果
    assertTrue(tool.validateMigration(testFile));
}
```

---

## 8. 常见问题

### Q: 如何确定转换比例？

A: 转换比例需要根据当前系统确定。当前系统：
- 星区渲染大小：50 像素
- 星区逻辑大小：1 光年 = 63,241 AU
- 因此：1 AU ≈ 0.00079 像素（对于星区级别）

### Q: 迁移工具如何处理旧数据？

A: 迁移工具会：
1. 检测数据版本
2. 备份原始数据
3. 根据转换比例转换所有距离和大小值
4. 验证转换结果的正确性

### Q: 如何调整可视化缩放？

A: 可以通过两种方式：
1. 修改配置文件 `visual-scale-config.properties`
2. 运行时通过 UI 控件手动调整（如果实现了 UI）

### Q: 定点数的精度如何保证？

A: 使用缩放因子 10^12，可以精确表示 0.000000000001 AU 到约 145,000 光年的范围。对于超出范围的值，使用单位转换（例如使用光年或秒差距）。

---

## 9. 下一步

完成快速开始后，可以：
1. 查看 [data-model.md](./data-model.md) 了解详细的数据模型
2. 查看 [research.md](./research.md) 了解技术决策
3. 查看 [tasks.md](./tasks.md) 了解实施任务（创建后）
