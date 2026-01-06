# Astronomical Units System API Contract

**Feature**: [spec.md](../spec.md)  
**Branch**: `011-astronomical-units`  
**Created**: 2026-01-07

## Overview

本文档定义天文单位系统的内部 API 契约，包括类接口、方法签名和契约约束。

---

## 1. AstronomicalUnit API

### 类定义

```java
public class AstronomicalUnit implements Serializable {
    private final long internalUnits;
    private static final long SCALE_FACTOR = 1_000_000_000_000L;  // 10^12
}
```

### 工厂方法

```java
// 从 AU 值创建
public static AstronomicalUnit fromAU(double auValue)

// 从光年值创建
public static AstronomicalUnit fromLightYears(double lyValue)

// 从秒差距值创建
public static AstronomicalUnit fromParsecs(double pcValue)

// 从内部单位创建（内部使用）
public static AstronomicalUnit fromInternalUnits(long internalUnits)
```

**契约**：
- 所有工厂方法必须验证输入值的合理性
- 转换时检查溢出，超出范围时抛出 `ArithmeticException`

### 转换方法

```java
// 转换为 AU 值
public double toAU()

// 转换为光年值
public double toLightYears()

// 转换为秒差距值
public double toParsecs()

// 获取内部单位值（内部使用）
public long getInternalUnits()
```

**契约**：
- 转换精度必须达到 99.9% 以上
- 转换结果必须在合理范围内

### 算术运算方法

```java
// 加法
public AstronomicalUnit add(AstronomicalUnit other)

// 减法
public AstronomicalUnit subtract(AstronomicalUnit other)

// 乘法（整数因子）
public AstronomicalUnit multiply(long factor)

// 除法（整数除数）
public AstronomicalUnit divide(long divisor)
```

**契约**：
- 所有运算必须检查溢出
- 除法时除数不能为 0
- 运算结果必须保持确定性

---

## 2. UnitConverter API

### 类定义

```java
public class UnitConverter {
    // 工具类，所有方法为静态方法
    private UnitConverter() {}  // 禁止实例化
}
```

### 转换方法

```java
// AU 转光年
public static AstronomicalUnit auToLightYears(AstronomicalUnit au)

// 光年转 AU
public static AstronomicalUnit lightYearsToAU(AstronomicalUnit ly)

// AU 转秒差距
public static AstronomicalUnit auToParsecs(AstronomicalUnit au)

// 秒差距转 AU
public static AstronomicalUnit parsecsToAU(AstronomicalUnit pc)

// 通用转换方法
public static AstronomicalUnit convert(
    AstronomicalUnit value,
    String fromUnit,  // "AU", "ly", "pc"
    String toUnit     // "AU", "ly", "pc"
)
```

**契约**：
- 转换精度必须达到 99.9% 以上
- 不支持的单位组合必须抛出 `IllegalArgumentException`
- 转换结果必须在合理范围内

---

## 3. SizeDefinition APIs

### SectorSizeDefinition

```java
public class SectorSizeDefinition implements Serializable {
    // 获取星区大小（AU）
    public AstronomicalUnit getSizeInAU()
    
    // 获取星区大小（光年）
    public double getSizeInLightYears()
    
    // 设置星区大小
    public void setSizeInAU(AstronomicalUnit size)
    
    // 验证星区大小
    public void validate() throws IllegalArgumentException
}
```

**契约**：
- `setSizeInAU()` 必须验证大小 > 0
- `validate()` 必须检查大小能够容纳恒星系（建议 >= 1 光年）

### OrbitSizeDefinition

```java
public class OrbitSizeDefinition implements Serializable {
    // 获取轨道半长轴
    public AstronomicalUnit getSemiMajorAxis()
    
    // 设置轨道半长轴
    public void setSemiMajorAxis(AstronomicalUnit axis)
    
    // 验证轨道大小
    public void validate() throws IllegalArgumentException
}
```

**契约**：
- `setSemiMajorAxis()` 必须验证轴在 [minValue, maxValue] 范围内
- `validate()` 必须检查轨道大小符合真实行星系统比例

### StarSizeDefinition / PlanetSizeDefinition

```java
public class StarSizeDefinition implements Serializable {
    // 获取半径（AU）
    public AstronomicalUnit getRadiusInAU()
    
    // 设置半径
    public void setRadiusInAU(AstronomicalUnit radius)
    
    // 从配置加载（按类型）
    public static StarSizeDefinition loadFromConfig(String starTypeId)
    
    // 验证大小
    public void validate() throws IllegalArgumentException
}
```

**契约**：
- `setRadiusInAU()` 必须验证半径在 [minRadius, maxRadius] 范围内（如果定义了范围）
- `loadFromConfig()` 如果类型不存在，必须抛出 `IllegalArgumentException`
- `validate()` 必须检查大小符合真实物理特性

---

## 4. VisualScaleConfig API

```java
public class VisualScaleConfig implements Serializable {
    // 获取 AU 到像素的转换比例
    public float getAuToPixels()
    
    // 获取有效缩放因子（自动或手动）
    public float getEffectiveScaleFactor()
    
    // 设置手动缩放因子
    public void setManualScaleFactor(float factor)
    
    // 重置为自动缩放
    public void resetToAuto()
    
    // 是否启用自动缩放
    public boolean isAutoScaleEnabled()
}
```

**契约**：
- `setManualScaleFactor()` 必须验证因子 > 0
- `getEffectiveScaleFactor()` 如果启用自动缩放，返回自动计算值；否则返回手动设置值

---

## 5. AstronomicalUnitSystem API

```java
public class AstronomicalUnitSystem {
    // 从配置文件加载系统
    public static AstronomicalUnitSystem loadFromConfig()
    
    // 获取星区大小定义
    public SectorSizeDefinition getSectorSizeDefinition()
    
    // 获取可视化缩放配置
    public VisualScaleConfig getVisualScaleConfig()
    
    // 获取单位转换器（工具类引用）
    public UnitConverter getUnitConverter()
    
    // 验证系统配置
    public void validate() throws IllegalArgumentException
}
```

**契约**：
- `loadFromConfig()` 如果配置文件不存在或格式错误，必须抛出 `IOException` 或 `IllegalArgumentException`
- `validate()` 必须验证所有配置的合理性

---

## 6. MigrationTool API

```java
public class MigrationTool {
    // 设置源版本
    public void setSourceVersion(String version)
    
    // 设置目标版本
    public void setTargetVersion(String version)
    
    // 设置转换比例
    public void setConversionRatio(double ratio)
    
    // 迁移单个存档文件
    public void migrateSaveFile(String filePath) throws IOException, MigrationException
    
    // 批量迁移目录
    public void migrateDirectory(String dirPath) throws IOException, MigrationException
    
    // 验证迁移结果
    public boolean validateMigration(String filePath) throws IOException
    
    // 备份原始文件
    public void backupOriginal(String filePath) throws IOException
}
```

**契约**：
- `migrateSaveFile()` 迁移前必须自动备份原始文件
- `migrateSaveFile()` 迁移后必须验证数据完整性
- `validateMigration()` 必须检查所有必需字段都已转换且值在合理范围内
- 所有方法如果文件不存在或格式错误，必须抛出相应的异常

---

## 7. 错误处理契约

### 异常类型

```java
// 数值溢出异常
public class ArithmeticOverflowException extends ArithmeticException

// 迁移异常
public class MigrationException extends Exception

// 配置加载异常
public class ConfigurationException extends Exception
```

### 异常使用规则

- **ArithmeticOverflowException**: 当定点数运算溢出时抛出
- **MigrationException**: 当迁移过程中发生错误时抛出（包含详细错误信息）
- **ConfigurationException**: 当配置文件格式错误或缺失时抛出
- **IllegalArgumentException**: 当参数无效时抛出（例如负数、超出范围）

---

## 8. 线程安全契约

### 线程安全类

- `AstronomicalUnit`: 不可变类，线程安全
- `UnitConverter`: 无状态工具类，线程安全
- `SizeDefinition` 类: 可变但通常单线程使用，不保证线程安全

### 非线程安全类

- `AstronomicalUnitSystem`: 配置加载后通常只读，但修改配置时不保证线程安全
- `VisualScaleConfig`: 修改缩放因子时不保证线程安全
- `MigrationTool`: 不保证线程安全，应单线程使用

---

## 9. 性能契约

### 性能要求

- **单位转换**: < 1ms（单次转换）
- **配置加载**: < 100ms（首次加载，包含文件 I/O）
- **迁移工具**: 批量转换 1000 个存档 < 10 秒
- **算术运算**: < 0.1ms（单次运算）

### 优化建议

- 配置加载器使用缓存，避免重复 I/O
- 单位转换使用预计算的转换常数
- 迁移工具支持批量处理，减少 I/O 次数

---

## 10. 向后兼容契约

### 迁移兼容性

- 迁移工具必须支持所有现有数据格式版本
- 迁移后数据格式必须完全符合新单位系统
- 迁移工具必须提供验证机制确保转换正确性

### API 兼容性

- 新 API 不保证与旧代码的兼容性（需要迁移）
- 迁移完成后，旧格式不再支持
- 所有新代码必须使用新 API
