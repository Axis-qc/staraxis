# Research: 星系生成系统增强（009）

## 结论与决策汇总

- **Decision**: 以现有 `DefaultWorldGenerator` + `StellarGenerator` 为基础扩展，不引入新的生成入口。
  - **Rationale**: 已存在按 tile 混合种子（`seed ^ coord`）的确定性机制，以及 JUnit 覆盖的确定性测试；扩展可复用现有约束与测试框架。
  - **Alternatives considered**: 完全重写生成器（风险：破坏现有确定性与统计回归）。

- **Decision**: 多恒星系统采用“层级归属”表达（主星行星 + 环双星），并允许“子系统节点”作为轨道中心。
  - **Rationale**: 满足可视化与数据建模需求，同时将复杂多体物理求解留作后续迭代。
  - **Alternatives considered**: 全部行星绕系统中心（信息不足）；支持任意深度多层级嵌套（实现成本过高）。

- **Decision**: 轨道形状支持椭圆（开普勒）表达；轨道路径以系统平面（XY）输出与验收。
  - **Rationale**: 既能表达非圆轨道（偏心率），又避免引入 3D 姿态/渲染复杂度；符合“同平台同版本确定性”边界。
  - **Alternatives considered**: 仅圆轨道（表现力不足）；多体扰动轨道（成本过高，且难验收）。

- **Decision**: 轨道路径精度与行星球面网格分辨率均使用离散档位（低/中/高），且同档位确定性一致。
  - **Rationale**: 易验收、易控性能、与现有数据驱动配置体系兼容。
  - **Alternatives considered**: 任意数值精度（难做性能边界与回归）。

- **Decision**: 轨道冲突/交叉采用自动修复策略，最多重试 3 次，失败返回诊断信息。
  - **Rationale**: 生成过程可预测、不会卡死；失败信息可用于调参与 Mod。
  - **Alternatives considered**: 不设上限（风险：卡死）；严格失败不修复（调参体验差）。

- **Decision**: 行星球面网格采用六边形+五边形构成球面，五边形数量固定 12；分辨率为低/中/高档位。
  - **Rationale**: 12 个五边形是球面闭合拓扑的强约束，便于自动校验与后续玩法叠加。
  - **Alternatives considered**: 不固定五边形数量（难以保证一致拓扑与验收）。

- **Decision**: 星系生成配置按“新生成时加载最新配置”执行；不要求运行期热更新影响已生成对象。
  - **Rationale**: 满足调参/测试需求，同时避免运行期状态一致性问题。
  - **Alternatives considered**: 热更新（引入大量状态同步/回放复杂度）。

## 现有代码基线（用于对齐改动范围）

- **世界生成入口**: `core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`
- **恒星/行星生成**: `core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`
- **共享数据模型**:
  - `shared/src/main/java/com/staraxis/game/shared/world/stellar/StarSystem.java`
  - `shared/src/main/java/com/staraxis/game/shared/world/stellar/Star.java`
  - `shared/src/main/java/com/staraxis/game/shared/world/stellar/Planet.java`
- **确定性回归测试**: `core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java`

## 需在 Phase 1 设计中落地的关键点

- **数据模型扩展**: StarSystem/Star/Planet 增加或关联轨道、轨道中心、环双星/子系统节点表达；Planet 增加表面网格引用。
- **算法扩展**:
  - 多恒星层级生成（至少双星共同质心 + 环双星行星）
  - 椭圆轨道参数生成与路径采样（低/中/高）
  - 轨道冲突检测、自动修复与 3 次重试上限
  - 球面六五边网格生成与拓扑校验（五边形=12）
