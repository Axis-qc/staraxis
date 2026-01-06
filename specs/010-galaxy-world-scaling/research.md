# Research: 星系与世界规模系统完善（010）

## 结论与决策汇总

- **Decision**: 在现有 `WorldGenConfig` 和 `WorldGenDefinitions` 基础上扩展规模配置系统，采用预设档位+自定义范围的混合模式。
  - **Rationale**: 预设档位提供易用性，自定义范围提供灵活性；与现有配置系统（MapSizePreset）保持一致的设计模式；数据驱动，避免硬编码。
  - **Alternatives considered**: 
    - 仅预设档位（灵活性不足，无法满足高级用户需求）
    - 仅自定义范围（易用性差，增加配置复杂度）

- **Decision**: 星系规模配置和区块规模配置分别独立管理，通过协调器处理两者之间的匹配关系。
  - **Rationale**: 保持模块化设计，避免耦合；协调器负责处理不匹配情况，提供自动调整和警告机制。
  - **Alternatives considered**: 
    - 统一规模配置（耦合度高，难以独立扩展）
    - 强制匹配（灵活性差，无法处理用户自定义配置）

- **Decision**: 轨道稳定性检查采用基于物理约束的量化标准（最小距离 > 碰撞半径的 2 倍，轨道能量 < 0）。
  - **Rationale**: 提供明确的判定标准，易于实现和测试；符合物理规律，提供合理的游戏体验；量化标准便于调试和调参。
  - **Alternatives considered**: 
    - 基于经验规则（缺乏物理依据，难以验证）
    - 基于模拟验证（计算成本高，不适合实时生成）

- **Decision**: 配置验证采用配置文件中定义范围+性能阈值的组合方式。
  - **Rationale**: 数值范围验证提供基本的数据完整性保护；性能阈值验证防止配置过大导致的性能问题；配置文件定义便于调参和 Mod 扩展。
  - **Alternatives considered**: 
    - 硬编码固定上限（灵活性差，难以调参）
    - 仅性能阈值（无法防止无效配置，如负数或 0）

- **Decision**: 轨道系统扩展支持完整的开普勒轨道参数（半长轴、偏心率、倾角、升交点经度、近地点幅角、真近点角）。
  - **Rationale**: 提供完整的轨道表达能力，支持复杂的多体系统；符合天体力学的标准参数化；便于后续扩展和 Mod 支持。
  - **Alternatives considered**: 
    - 仅保留现有参数（表现力不足，无法支持复杂轨道）
    - 引入更多高级参数（实现复杂度高，当前需求不必要）

- **Decision**: 多体系统轨道计算使用简化的 N 体问题解法，优先保证稳定性和性能。
  - **Rationale**: 真实 N 体问题计算成本高且不稳定；简化解法满足游戏需求，提供合理的近似；优先保证稳定性和性能符合游戏开发实践。
  - **Alternatives considered**: 
    - 真实 N 体问题求解（计算成本高，不适合实时生成）
    - 完全忽略多体效应（表现力不足，不符合物理规律）

## 现有代码基线（用于对齐改动范围）

- **世界生成入口**: `core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`
- **恒星/行星生成**: `core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`
- **配置系统**: 
  - `shared/src/main/java/com/staraxis/game/shared/world/WorldGenConfig.java`
  - `shared/src/main/java/com/staraxis/game/shared/world/WorldGenDefinitions.java`
- **轨道系统**: 
  - `shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/Orbit.java`
  - `core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitParamSampler.java`
  - `core/src/main/java/com/staraxis/game/core/world/stellar/orbit/OrbitValidator.java`
- **确定性回归测试**: `core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java`

## 技术调研要点

### 1. 规模配置系统设计

**研究问题**: 如何设计预设档位+自定义范围的配置系统？

**调研结果**:
- 参考现有 `MapSizePreset` 设计模式，使用数据驱动的预设定义
- 预设档位存储在配置文件中，包含默认的数值范围
- 自定义范围允许用户覆盖预设值，提供完整的灵活性
- 配置加载器负责解析预设档位和自定义范围，合并为最终配置

**决策**: 采用 `GalaxyScaleConfig` 和 `WorldBlockScaleConfig` 数据模型，支持预设档位 ID 或自定义范围；配置加载器负责解析和验证。

### 2. 星系与区块协调机制

**研究问题**: 如何处理星系规模与区块规模不匹配的情况？

**调研结果**:
- 自动调整分布密度：根据区块数量调整星系分布，确保每个区块都有合理的星系密度
- 警告机制：记录不匹配情况和调整操作，供用户查看和调试
- 确定性保证：调整操作必须基于随机种子，保证相同配置产生相同结果

**决策**: 实现 `GalaxyBlockCoordinator`，负责检测不匹配、自动调整分布、记录警告信息；调整算法必须确定性。

### 3. 轨道稳定性检查算法

**研究问题**: 如何实现基于物理约束的轨道稳定性检查？

**调研结果**:
- 最小距离检查：计算相邻轨道之间的最小距离，确保 > 碰撞半径的 2 倍
- 轨道能量检查：计算轨道能量，确保 < 0（椭圆轨道）
- 碰撞检测：检查轨道是否会导致行星碰撞
- 逃逸检测：检查轨道是否会导致行星逃逸

**决策**: 实现 `OrbitStabilityChecker`，基于物理约束的量化标准进行稳定性检查；检查失败时优先尝试自动调整/重采样，失败则记录警告。

### 4. 配置验证机制

**研究问题**: 如何实现配置文件中定义范围+性能阈值的验证机制？

**调研结果**:
- 数值范围验证：检查配置值是否在定义范围内（最小值、最大值）
- 性能阈值验证：检查配置是否会导致性能问题（生成时间、内存使用）
- 验证策略：警告或拒绝，根据配置策略执行
- 验证时机：配置加载时和生成前

**决策**: 实现 `ScaleConfigValidator`，支持数值范围验证和性能阈值验证；验证规则在配置文件中定义；验证失败时根据策略执行警告或拒绝。

### 5. 轨道计算算法

**研究问题**: 如何实现轨道位置和周期计算？

**调研结果**:
- 开普勒定律：使用开普勒方程计算轨道位置
- 轨道周期：T = 2π√(a³/GM)，其中 a 是半长轴，G 是引力常数，M 是中心天体质量
- 真近点角计算：使用开普勒方程求解
- 确定性保证：计算必须确定性，相同输入产生相同输出

**决策**: 实现 `OrbitCalculator`，支持轨道位置计算和周期计算；使用开普勒定律的合理近似；保证确定性。

## 需在 Phase 1 设计中落地的关键点

- **数据模型扩展**: 
  - `GalaxyScaleConfig`: 星系规模配置（预设档位+自定义范围）
  - `WorldBlockScaleConfig`: 区块规模配置（预设档位+自定义范围）
  - `ScalePreset`: 规模预设档位定义
  - `Orbit`: 扩展完整轨道参数（半长轴、偏心率、倾角、升交点经度、近地点幅角、真近点角）
  - `WorldGenConfig`: 扩展规模配置字段

- **算法扩展**:
  - 规模配置加载和解析（预设档位+自定义范围）
  - 星系与区块协调机制（自动调整分布+警告）
  - 轨道稳定性检查（基于物理约束的量化标准）
  - 配置验证机制（数值范围+性能阈值）
  - 轨道位置和周期计算（开普勒定律）

- **配置文件结构**:
  - `galaxy-scale-config.properties`: 星系规模预设档位定义
  - `world-block-scale-config.properties`: 区块规模预设档位定义
  - `scale-validation-config.properties`: 规模验证配置（数值范围+性能阈值）
