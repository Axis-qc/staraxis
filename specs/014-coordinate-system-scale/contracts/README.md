# Contracts - 014 基础坐标系与比例尺

本功能为客户端渲染与调试工具，不涉及网络 API。

## 内部契约（接口/事件）

建议以接口与事件作为模块边界（符合宪章：逻辑层不直接依赖 UI）。

### 1. `ICoordinateService`

- 职责：
  - 提供 `WorldCoordinate` 与“渲染局部坐标”的转换
  - 提供比例尺（km/px）查询

### 2. `DebugToggle` 事件

- 事件：F3 触发，切换 DebugOverlay 与 WorldGridRenderer。
- 约束：
  - Core（逻辑）不直接持有 UI 引用
  - UI/Debug 模块订阅事件并自行渲染
