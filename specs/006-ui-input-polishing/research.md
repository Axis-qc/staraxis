# Research: UI & Input Polishing (UI与输入优化)

**Feature**: [006-ui-input-polishing](../spec.md)
**Status**: COMPLETED
**Date**: 2026-01-05

## 1. 科技感 (Futuristic) UI 皮肤化方案

### Decision
采用程序化 NinePatch 结合现有的 `white` 纹理进行染色，并配合 `ShapeRenderer` 实现霓虹光效。

### Rationale
- **灵活性**：代码生成 NinePatch 允许动态调整边框粗细和霓虹颜色，无需为每种颜色导出贴图。
- **性能**：NinePatch 渲染效率高，适合 libGDX 的 `SpriteBatch` 批处理。
- **契合度**：当前项目中已存在 `white` 像素贴图，可以通过 `Color` 叠加直接实现半透明霓虹效果。

### Alternatives Considered
- **外部 Atlas 贴图**：需要额外设计资源，且后期调整配色困难。
- **自定义 Shader**：虽然效果上限高，但对于基础控件美化过于复杂，可能影响 UI 层独立性。

## 2. 带惯性的 WASD 镜头平移

### Decision
使用 `Vector2` 存储当前平移速度 (Velocity)，WASD 增减加速度 (Acceleration)，每帧根据摩擦系数 (Friction) 衰减。

### Rationale
- **体验感**：加速度与摩擦力的组合能模拟物理惯性，使平移不再生硬。
- **确定性**：基于 `delta time` 演算，确保不同帧率下平滑度一致。

## 3. 以鼠标指针为中心的缩放逻辑

### Decision
缩放前记录鼠标在世界空间的坐标 $P_{world}$，缩放后计算新的屏幕对应坐标，平移摄像机使 $P_{world}$ 仍映射到相同的屏幕位置。

### Rationale
- **行业标准**：这是 RTS 和策略游戏的通用做法，交互最为自然。
- **libGDX 支持**：利用 `camera.unproject()` 和 `camera.position` 的向量操作可精确实现。

## 4. 本地化覆盖策略

### Decision
新增的 UI 键值对将严格分类存储于 `messages.properties` (中文) 和 `messages_en.properties` (英文)。

### Rationale
- 现有 `LocalizationService` 已支持此结构，兼容性最佳。
- 分类命名（如 `config_` 前缀）便于后期维护。
