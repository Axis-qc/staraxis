# Quickstart: 星系生成系统增强（009）

## 目标

快速验证本特性在 **headless 环境（core）** 下可生成：

- 多恒星层级归属（至少双星共同质心 + 环双星行星）
- 椭圆轨道参数与轨道路径（低/中/高精度档位）
- 轨道冲突自动修复与 3 次重试上限
- 行星球面六五边网格（五边形固定 12；低/中/高分辨率档位）

## 推荐验证路径

### 1) 运行现有确定性测试（基线回归）

- `:core:test`（包含 `DefaultWorldGeneratorTest`）

### 2) 新增/扩展测试建议（实现阶段）

- **多恒星层级测试**: 断言双星情况下存在“共同质心/子系统节点”且行星归属可判定
- **椭圆轨道测试**: 偏心率不为 0 时轨道路径仍闭合且为椭圆特征
- **精度档位测试**: 低/中/高采样点数不同，但形状一致；同档位确定性一致
- **轨道冲突测试**: 构造冲突输入时触发修复与最多 3 次重试，失败返回诊断信息
- **球面网格拓扑测试**: 五边形数量恒为 12；邻接双向一致；单元类型仅 Hex/Pent

## 代码导航（当前基线）

- 世界生成入口：`core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`
- 恒星/行星生成：`core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java`
- 共享数据模型：`shared/src/main/java/com/staraxis/game/shared/world/stellar/*`
- 既有测试：`core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java`
