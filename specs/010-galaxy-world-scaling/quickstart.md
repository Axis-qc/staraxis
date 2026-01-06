# Quickstart: 星系与世界规模系统完善

**Feature**: [spec.md](./spec.md)  
**Branch**: `010-galaxy-world-scaling`  
**Created**: 2026-01-06

## 概述

本文档提供快速开始指南，帮助开发者快速理解和使用星系与世界规模系统。

## 快速开始

### 1. 配置星系规模

#### 使用预设档位

```java
// 加载预设档位配置
GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
GalaxyScaleConfig config = loader.loadConfig("small", null); // 小型星系

// 使用配置生成星系
WorldGenConfig worldConfig = new WorldGenConfig();
worldConfig.setGalaxyScaleConfig(config);
WorldGenerator generator = new DefaultWorldGenerator();
WorldMap worldMap = generator.generate(worldConfig);
```

#### 使用自定义范围

```java
// 创建自定义范围
GalaxyScaleRange customRange = new GalaxyScaleRange();
customRange.setMinStarSystems(50);
customRange.setMaxStarSystems(100);
customRange.setDefaultStarSystems(75);

GalaxyScaleConfig config = new GalaxyScaleConfig();
config.setCustomRange(customRange);

// 使用配置生成星系
WorldGenConfig worldConfig = new WorldGenConfig();
worldConfig.setGalaxyScaleConfig(config);
WorldGenerator generator = new DefaultWorldGenerator();
WorldMap worldMap = generator.generate(worldConfig);
```

### 2. 配置区块规模

#### 使用预设档位

```java
// 加载预设档位配置
WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
WorldBlockScaleConfig config = loader.loadConfig("medium", null); // 中型地图

// 使用配置生成世界
WorldGenConfig worldConfig = new WorldGenConfig();
worldConfig.setWorldBlockScaleConfig(config);
WorldGenerator generator = new DefaultWorldGenerator();
WorldMap worldMap = generator.generate(worldConfig);
```

#### 使用自定义范围

```java
// 创建自定义范围
WorldBlockScaleRange customRange = new WorldBlockScaleRange();
customRange.setWidth(200);
customRange.setHeight(200);
customRange.setBlockSize(1.0f);

WorldBlockScaleConfig config = new WorldBlockScaleConfig();
config.setCustomRange(customRange);

// 使用配置生成世界
WorldGenConfig worldConfig = new WorldGenConfig();
worldConfig.setWorldBlockScaleConfig(config);
WorldGenerator generator = new DefaultWorldGenerator();
WorldMap worldMap = generator.generate(worldConfig);
```

### 3. 验证配置

```java
// 创建验证器
ScaleConfigValidator validator = new ScaleConfigValidator();

// 验证星系规模配置
GalaxyScaleConfig galaxyConfig = loader.loadConfig("large", null);
ValidationResult result = validator.validateGalaxyScale(galaxyConfig);
if (!result.isValid()) {
    System.err.println("配置验证失败: " + result.getErrors());
    return;
}

// 验证性能阈值
long estimatedTime = 5000; // 预估 5 秒
long estimatedMemory = 100; // 预估 100 MB
ValidationResult perfResult = validator.validatePerformance(
    galaxyConfig, blockConfig, estimatedTime, estimatedMemory);
if (!perfResult.isValid()) {
    System.err.println("性能验证失败: " + perfResult.getErrors());
    return;
}
```

### 4. 协调星系与区块规模

```java
// 创建协调器
GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();

// 协调星系和区块规模
Random random = new Random(worldConfig.getSeedValue());
GalaxyBlockCoordinationResult coordResult = coordinator.coordinate(
    galaxyConfig, blockConfig, random);

if (coordResult.isAdjusted()) {
    System.out.println("已自动调整星系分布密度");
    System.out.println("警告: " + coordResult.getWarnings());
}

// 使用调整后的配置生成世界
WorldGenerator generator = new DefaultWorldGenerator();
WorldMap worldMap = generator.generate(worldConfig);
```

### 5. 计算轨道位置和周期

```java
// 创建轨道计算器
OrbitCalculator calculator = new OrbitCalculator();

// 计算行星位置
Orbit orbit = planet.getOrbit();
float centerMass = star.getMass();
float time = 100.0f; // 游戏时间
Vector2 position = calculator.calculatePosition(orbit, centerMass, time);
System.out.println("行星位置: (" + position.getX() + ", " + position.getY() + ")");

// 计算轨道周期
float period = calculator.calculatePeriod(orbit, centerMass);
System.out.println("轨道周期: " + period);
```

### 6. 检查轨道稳定性

```java
// 创建稳定性检查器
OrbitStabilityChecker checker = new OrbitStabilityChecker();

// 检查轨道稳定性
List<Orbit> otherOrbits = system.getPlanets().stream()
    .map(Planet::getOrbit)
    .collect(Collectors.toList());
float collisionRadius = 0.1f; // 碰撞半径

OrbitStabilityCheckResult result = checker.checkStability(
    orbit, centerMass, otherOrbits, collisionRadius);

if (!result.isStable()) {
    System.err.println("轨道不稳定: " + result.getMessages());
    // 尝试自动调整或重采样
}
```

## 配置文件示例

### galaxy-scale-config.properties

```properties
# 小型星系预设
galaxy.scale.preset.small.id=small
galaxy.scale.preset.small.displayName=小型星系
galaxy.scale.preset.small.minStarSystems=10
galaxy.scale.preset.small.maxStarSystems=20
galaxy.scale.preset.small.defaultStarSystems=15

# 中型星系预设
galaxy.scale.preset.medium.id=medium
galaxy.scale.preset.medium.displayName=中型星系
galaxy.scale.preset.medium.minStarSystems=50
galaxy.scale.preset.medium.maxStarSystems=100
galaxy.scale.preset.medium.defaultStarSystems=75

# 大型星系预设
galaxy.scale.preset.large.id=large
galaxy.scale.preset.large.displayName=大型星系
galaxy.scale.preset.large.minStarSystems=100
galaxy.scale.preset.large.maxStarSystems=200
galaxy.scale.preset.large.defaultStarSystems=150
```

### world-block-scale-config.properties

```properties
# 小型地图预设
block.scale.preset.small.id=small
block.scale.preset.small.displayName=小型地图
block.scale.preset.small.width=50
block.scale.preset.small.height=50
block.scale.preset.small.blockSize=1.0

# 中型地图预设
block.scale.preset.medium.id=medium
block.scale.preset.medium.displayName=中型地图
block.scale.preset.medium.width=200
block.scale.preset.medium.height=200
block.scale.preset.medium.blockSize=1.0

# 大型地图预设
block.scale.preset.large.id=large
block.scale.preset.large.displayName=大型地图
block.scale.preset.large.width=500
block.scale.preset.large.height=500
block.scale.preset.large.blockSize=1.0
```

### scale-validation-config.properties

```properties
# 星系规模限制
validation.galaxy.minStarSystems=1
validation.galaxy.maxStarSystems=1000
validation.galaxy.maxGenerationTimeMs=10000

# 区块规模限制
validation.block.minWidth=1
validation.block.maxWidth=10000
validation.block.minHeight=1
validation.block.maxHeight=10000
validation.block.maxGenerationTimeMs=5000

# 性能阈值
validation.performance.maxGenerationTimeMs=10000
validation.performance.maxMemoryUsageMB=500
validation.performance.strategy=WARN
```

## 测试示例

### 单元测试

```java
@Test
public void testGalaxyScaleConfigLoading() throws IOException {
    GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
    GalaxyScaleConfig config = loader.loadConfig("small", null);
    
    assertNotNull(config);
    assertEquals("small", config.getPresetId());
}

@Test
public void testOrbitCalculation() {
    OrbitCalculator calculator = new OrbitCalculator();
    Orbit orbit = new Orbit();
    orbit.setSemiMajorAxis(10.0f);
    orbit.setEccentricity(0.1f);
    
    float period = calculator.calculatePeriod(orbit, 1.0f);
    assertTrue(period > 0);
}

@Test
public void testOrbitStabilityCheck() {
    OrbitStabilityChecker checker = new OrbitStabilityChecker();
    Orbit orbit = new Orbit();
    orbit.setSemiMajorAxis(10.0f);
    orbit.setEccentricity(0.1f);
    
    OrbitStabilityCheckResult result = checker.checkStability(
        orbit, 1.0f, Collections.emptyList(), 0.1f);
    
    assertTrue(result.isStable());
}
```

## 常见问题

### Q: 如何选择预设档位还是自定义范围？

A: 预设档位适合快速开始和标准场景，自定义范围适合需要精确控制的高级用户。建议先使用预设档位，如果需要更精细的控制再使用自定义范围。

### Q: 配置验证失败怎么办？

A: 检查配置值是否在允许范围内，如果超出范围，调整配置值或修改验证配置文件中的限制。

### Q: 星系与区块规模不匹配会怎样？

A: 系统会自动调整星系分布密度以适应区块规模，同时记录警告信息。建议使用匹配的规模配置以获得最佳体验。

### Q: 轨道稳定性检查失败怎么办？

A: 系统会优先尝试自动调整/重采样，如果失败则记录警告信息。可以检查警告信息了解具体原因，然后调整轨道参数或生成配置。

## 下一步

- 查看 [data-model.md](./data-model.md) 了解数据模型
- 查看 [contracts/scale-config-api.md](./contracts/scale-config-api.md) 了解 API 契约
- 查看 [spec.md](./spec.md) 了解完整规格
