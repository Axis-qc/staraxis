# Scale Configuration API Contract

**Feature**: [spec.md](../spec.md)  
**Branch**: `010-galaxy-world-scaling`  
**Created**: 2026-01-06

## Overview

本文档定义规模配置系统的 API 契约，包括配置加载、验证和协调机制的接口定义。

## API Contracts

### 1. GalaxyScaleConfigLoader（星系规模配置加载器）

**职责**: 加载和解析星系规模配置（预设档位或自定义范围）

**接口**:

```java
public class GalaxyScaleConfigLoader {
    /**
     * 加载星系规模配置
     * @param presetId 预设档位 ID（如 "small"/"medium"/"large"），如果为 null 则使用 customRange
     * @param customRange 自定义范围，如果为 null 则使用 presetId
     * @return 解析后的星系规模配置
     * @throws IllegalArgumentException 如果 presetId 和 customRange 都为空或都不为空
     * @throws IOException 如果配置文件加载失败
     */
    public GalaxyScaleConfig loadConfig(String presetId, GalaxyScaleRange customRange) throws IOException;
    
    /**
     * 加载预设档位定义
     * @param presetId 预设档位 ID
     * @return 预设档位定义
     * @throws IllegalArgumentException 如果预设档位不存在
     * @throws IOException 如果配置文件加载失败
     */
    public ScalePreset loadPreset(String presetId) throws IOException;
}
```

### 2. WorldBlockScaleConfigLoader（世界区块规模配置加载器）

**职责**: 加载和解析世界区块规模配置（预设档位或自定义范围）

**接口**:

```java
public class WorldBlockScaleConfigLoader {
    /**
     * 加载世界区块规模配置
     * @param presetId 预设档位 ID（如 "small"/"medium"/"large"），如果为 null 则使用 customRange
     * @param customRange 自定义范围，如果为 null 则使用 presetId
     * @return 解析后的世界区块规模配置
     * @throws IllegalArgumentException 如果 presetId 和 customRange 都为空或都不为空
     * @throws IOException 如果配置文件加载失败
     */
    public WorldBlockScaleConfig loadConfig(String presetId, WorldBlockScaleRange customRange) throws IOException;
    
    /**
     * 加载预设档位定义
     * @param presetId 预设档位 ID
     * @return 预设档位定义
     * @throws IllegalArgumentException 如果预设档位不存在
     * @throws IOException 如果配置文件加载失败
     */
    public ScalePreset loadPreset(String presetId) throws IOException;
}
```

### 3. ScaleConfigValidator（规模配置验证器）

**职责**: 验证规模配置的有效性（数值范围+性能阈值）

**接口**:

```java
public class ScaleConfigValidator {
    /**
     * 验证星系规模配置
     * @param config 星系规模配置
     * @return 验证结果（包含是否有效、警告信息等）
     */
    public ValidationResult validateGalaxyScale(GalaxyScaleConfig config);
    
    /**
     * 验证世界区块规模配置
     * @param config 世界区块规模配置
     * @return 验证结果（包含是否有效、警告信息等）
     */
    public ValidationResult validateWorldBlockScale(WorldBlockScaleConfig config);
    
    /**
     * 验证性能阈值
     * @param config 规模配置
     * @param estimatedGenerationTimeMs 预估生成时间（毫秒）
     * @param estimatedMemoryUsageMB 预估内存使用（MB）
     * @return 验证结果
     */
    public ValidationResult validatePerformance(GalaxyScaleConfig galaxyConfig, 
                                                WorldBlockScaleConfig blockConfig,
                                                long estimatedGenerationTimeMs,
                                                long estimatedMemoryUsageMB);
}
```

### 4. GalaxyBlockCoordinator（星系与区块协调器）

**职责**: 协调星系生成与区块生成，处理不匹配情况

**接口**:

```java
public class GalaxyBlockCoordinator {
    /**
     * 协调星系规模与区块规模
     * @param galaxyConfig 星系规模配置
     * @param blockConfig 区块规模配置
     * @param random 随机数生成器（用于确定性调整）
     * @return 协调结果（包含是否调整、调整后配置、警告信息等）
     */
    public GalaxyBlockCoordinationResult coordinate(GalaxyScaleConfig galaxyConfig,
                                                    WorldBlockScaleConfig blockConfig,
                                                    Random random);
    
    /**
     * 计算星系密度（每区块平均恒星系统数）
     * @param galaxyConfig 星系规模配置
     * @param blockConfig 区块规模配置
     * @return 星系密度
     */
    public float calculateDensity(GalaxyScaleConfig galaxyConfig, 
                                   WorldBlockScaleConfig blockConfig);
    
    /**
     * 检测规模是否匹配
     * @param galaxyConfig 星系规模配置
     * @param blockConfig 区块规模配置
     * @return 是否匹配
     */
    public boolean isMatching(GalaxyScaleConfig galaxyConfig, 
                              WorldBlockScaleConfig blockConfig);
}
```

### 5. OrbitCalculator（轨道计算器）

**职责**: 根据轨道参数计算行星位置和周期

**接口**:

```java
public class OrbitCalculator {
    /**
     * 计算行星在指定时刻的位置
     * @param orbit 轨道参数
     * @param centerMass 中心天体质量
     * @param time 时间（游戏时间单位）
     * @return 行星位置（X, Y 坐标）
     */
    public Vector2 calculatePosition(Orbit orbit, float centerMass, float time);
    
    /**
     * 计算轨道周期
     * @param orbit 轨道参数
     * @param centerMass 中心天体质量
     * @return 轨道周期（游戏时间单位）
     */
    public float calculatePeriod(Orbit orbit, float centerMass);
    
    /**
     * 计算真近点角
     * @param orbit 轨道参数
     * @param time 时间（游戏时间单位）
     * @return 真近点角（弧度）
     */
    public float calculateTrueAnomaly(Orbit orbit, float time);
}
```

### 6. OrbitStabilityChecker（轨道稳定性检查器）

**职责**: 基于物理约束检查轨道稳定性

**接口**:

```java
public class OrbitStabilityChecker {
    /**
     * 检查轨道稳定性
     * @param orbit 轨道参数
     * @param centerMass 中心天体质量
     * @param otherOrbits 其他轨道列表（用于碰撞检测）
     * @param collisionRadius 碰撞半径
     * @return 稳定性检查结果
     */
    public OrbitStabilityCheckResult checkStability(Orbit orbit,
                                                    float centerMass,
                                                    List<Orbit> otherOrbits,
                                                    float collisionRadius);
    
    /**
     * 计算最小距离（与其他轨道之间的最小距离）
     * @param orbit1 轨道1
     * @param orbit2 轨道2
     * @return 最小距离
     */
    public float calculateMinDistance(Orbit orbit1, Orbit orbit2);
    
    /**
     * 计算轨道能量
     * @param orbit 轨道参数
     * @param centerMass 中心天体质量
     * @return 轨道能量（< 0 表示椭圆轨道）
     */
    public float calculateOrbitalEnergy(Orbit orbit, float centerMass);
}
```

## Data Transfer Objects

### ValidationResult（验证结果）

```java
public class ValidationResult {
    private boolean isValid;
    private List<String> warnings;
    private List<String> errors;
    
    // Getters and setters
}
```

### Vector2（二维向量）

```java
public class Vector2 {
    private float x;
    private float y;
    
    // Getters and setters
}
```

## Error Handling

- **IllegalArgumentException**: 当参数无效时抛出（如 presetId 和 customRange 都为空）
- **IOException**: 当配置文件加载失败时抛出
- **ValidationException**: 当配置验证失败时抛出（如果验证策略为 REJECT）

## Determinism Guarantees

- 所有接口必须保证确定性：相同输入产生相同输出
- 协调器的调整操作必须基于随机种子，保证相同配置产生相同调整结果
- 轨道计算必须确定性，相同轨道参数和时间产生相同位置
