# Phase 0 Research - 014 基础坐标系与比例尺

本文件用于消除 Implementation Plan 中的 `NEEDS CLARIFICATION`，并将关键技术决策文档化。

## 1. 渲染/引擎技术栈识别（必须）

- **Decision**: 使用 libGDX 1.12.1，桌面端采用 LWJGL3 backend（见 `lwjgl3/build.gradle`，依赖 `gdx-backend-lwjgl3`）。
- **Rationale**: 坐标轴/网格的“世界空间调试线框渲染”的实现方式高度依赖引擎（例如是否已有 DebugDraw、LineBatch、ImmediateModeRenderer、Gizmo 系统等）。
- **Alternatives considered**:
  - libGDX: `ShapeRenderer`/`ImmediateModeRenderer20`/自建 line mesh
  - LWJGL: 自建 GL line shader + dynamic VBO
  - Unity/Unreal: 内建 Gizmos/DebugDraw（但项目显然不是）

## 2. 网格“漂亮数”步长选择策略

- **Decision**: 采用 1-2-5 系列的“漂亮数”步长（例如 1/2/5/10 * 10^n），使相邻网格线的屏幕距离接近目标值（100px），同时保持数值可读。
- **Rationale**: 该策略在地图/工程制图/调试网格中成熟可靠；能避免缩放时网格频繁抖动且便于用户理解。
- **Alternatives considered**:
  - 固定步长：远景过密或近景过稀
  - 连续步长：网格线持续滑动，视觉噪音较大

## 3. 分层坐标与渲染精度的常见实现模式

- **Decision**: 分层坐标用于“逻辑表示”；渲染阶段以相机附近为参考做局部化（Localize）以避免浮点误差。
- **Rationale**: 即使 `LocalOffset` 是 float/double，如果直接用巨大世界坐标送入 GPU/矩阵，也会导致精度损失。常见做法是以 camera origin 为参考，渲染时传入相对坐标。
- **Alternatives considered**:
  - 全 double 渲染：GPU pipeline 仍多为 float，收益有限
  - 原点漂移（floating origin）：实现复杂，但未来可能需要

## 结论与待办

- 已确认：项目使用 libGDX 1.12.1，桌面端 LWJGL3 backend。
- 待办 1：在 `client` 模块内定位现有的渲染主循环与 UI 系统接入点（Stage/SpriteBatch 管线）以放置 DebugOverlay 与 WorldGridRenderer。
