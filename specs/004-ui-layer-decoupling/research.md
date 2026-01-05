# Research: UI Layer Decoupling & Command Restrictions

## 1. UI Model & Data Binding Pattern
- **Decision**: 使用 **UI Model + Event Bus** 模式。
- **Rationale**: 逻辑层发布纯数据事件（Data Events），UI Model 订阅这些事件并更新自身状态。UI View (Screen) 仅观察 UI Model。这种模式实现了逻辑层与 UI 层的彻底双向解耦。
- **Alternatives considered**: 
    - 直接引用接口：UI 层仍需持有逻辑层引用，解耦不彻底。
    - 响应式框架：引入额外依赖，增加系统复杂度。

## 2. Delegated Rendering Architecture
- **Decision**: 在 `lwjgl3` 模块中实现 `RenderDelegate` 系统。
- **Rationale**: `Main.render()` 方法将 Batch、Camera 和 Delta 等必要渲染上下文传递给 `UIManager`。`UIManager` 根据当前状态调度 `GameViewport`（负责世界渲染）和 `Screen`（负责 UI 渲染）。
- **Alternatives considered**: 
    - 保持现状：逻辑层仍需感知渲染顺序和 UI 状态。

## 3. Gradle Toolchain Implementation
- **Decision**: 在根目录 `build.gradle` 中定义扩展任务，并使用专门的 `scripts/dev-tools.gradle` 进行管理。
- **Rationale**: 解决之前遇到的资源路径手动配置易错问题。符合宪章中关于自动化和脚本化的要求。
- **Alternatives considered**: 
    - `buildSrc`：适合极其复杂的逻辑，当前场景下通过外部脚本导入（dev-tools.gradle）更轻量。已在任务列表中采用 dev-tools.gradle 方案。

## 4. Resource Automation
- **Decision**: 封装 `syncAssets` 任务，自动执行资源校验、字体生成检查及多语言文件同步。
- **Rationale**: 解决之前遇到的资源路径手动配置易错问题。
- **Alternatives considered**: 
    - 手动复制：违反原则且易出错。
