# Implementation Plan: Scene2D 主菜单 UI 骨架

**Feature Spec**: [spec.md](./spec.md)

## 1. Technical Context

- **Goal**: 搭建一个可运行的桌面客户端骨架，能启动一个窗口，并使用 LibGDX Scene2D 渲染一个可交互的主菜单。主菜单功能严格遵循 `spec.md` 的范围，不包含模拟层与通信层。
- **Core Technologies**:
  - **Windowing & Application Lifecycle**: LibGDX (via `gdx-backend-lwjgl3`)
  - **UI Rendering**: LibGDX Scene2D
  - **Build System**: Gradle
- **Key Boundaries**:
  - **Host (Java/LibGDX)**: 负责创建窗口、初始化 Scene2D（`stage`）、渲染与输入分发。
  - **UI (Scene2D Widgets)**: 负责主菜单的布局与视觉呈现，并处理按钮点击事件。
- **Scope Guardrails**:
  - 不引入 `WorldState` 或任何模拟/通信/存档系统。
  - 所有按钮点击行为仅包含：
    - 退出应用
    - 弹出“开发中”提示

## 2. Constitution Check

本计划已根据 `.specify/memory/constitution.md` (Version: 1.6.1) 进行核对：

- **[✅] I. 模块化、可维护性与数据驱动**: UI 资源允许从 `assets/` 加载，但本阶段可先以最小可运行为目标。
- **[✅] II. 架构分层与层间边界（单机）**: 严格遵守。本计划只涉及“平台与表现层”，不包含任何核心模拟逻辑。
- **[✅] III. 单机权威与本地世界状态**: 严格遵守。本计划不引入 `WorldState`。
- **[✅] IV. 确定性模拟**: 严格遵守。本计划不引入模拟逻辑。
- **[✅] V. 实体-ID 索引模型**: 严格遵守。本计划不引入实体。
- **[✅] VI. 内存常驻与实体文件存档**: 严格遵守。本计划不引入存档功能。
- **[✅] VII. 规范化命名与注释**: 将在实现中遵循。
- **[✅] VIII. 扩展性与 Mod 支持**: 本次骨架搭建将为后续扩展 UI 页面提供基础，符合原则。
- **[✅] IX. 游戏模拟驱动**: 严格遵守。本计划不引入游戏模拟逻辑。
- **[✅] X. 多核性能优化**: 严格遵守。本计划不涉及性能关键的并行计算。

**Result**: No violations detected.

## 3. Implementation Phases

### Phase 0: Research & Decisions

*本阶段聚焦于 Scene2D 资源与布局的最小决策，避免在实现阶段走弯路。*

- **Task 1**: 确定 Scene2D 主菜单 UI 的最小资源策略：是否需要 `Skin`/字体资源，或先使用最小占位资源。
- **Task 2**: 确定“开发中”提示的 UI 形态：使用简单对话框/弹窗组件，确保所有非退出按钮统一提示。

**Output**: [research.md](./research.md)

### Phase 1: Design & Contracts

*本阶段定义 UI 结构与交互约定（不涉及模拟与通信）。*

- **Task 1**: 更新 `data-model.md`：将交互实体从“WebView 事件”调整为“主菜单按钮动作”。
- **Task 2**: 更新 `contracts/`：将 UI↔Host Bridge 契约调整为 Scene2D 内部事件处理约定（或删除不再需要的 Bridge 契约）。
- **Task 3**: 更新 `quickstart.md`：说明如何构建并运行 Scene2D 主菜单。

**Outputs**:
- [data-model.md](./data-model.md)
- [contracts/*](./contracts/)
- [quickstart.md](./quickstart.md)

### Phase 2: Implementation

*本阶段进入任务拆解与编码实现。*
