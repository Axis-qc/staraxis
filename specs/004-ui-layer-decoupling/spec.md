# Feature Specification: UI Layer Decoupling & Command Restrictions (UI层解耦与指令限制)

**Feature Branch**: `004-ui-layer-decoupling`
**Created**: 2026-01-05
**Status**: Draft
**Input**: User description: "将游戏的ui转为专门的ui层，不要在游戏逻辑内构建ui，渲染直接引用相应的ui，禁止频繁使用终端命令"

## Overview

### Description
该功能旨在优化《StarAxis》的整体架构，实现 UI 层与游戏逻辑层的彻底解耦。通过引入独立的 UI Model 和数据绑定机制，确保游戏逻辑层不感知 UI 细节。同时，将所有必要的终端操作（如资源处理、构建等）转化为 Gradle 脚本，以落实宪章中的“禁止频繁使用终端命令”原则。

### Business Value
- **架构健壮性**: 减少层级间的相互影响，降低重构和扩展成本。
- **可维护性**: 清晰的 UI 边界使前端表现与后端逻辑可以并行开发。
- **自动化与规范**: 通过脚本替代手动终端操作，减少人为错误，提高开发效率。

## Clarifications

### Session 2026-01-05
- Q: UI 层获取游戏状态的最佳路径是什么？ → A: 采用主动推送 (Event-based Push) 模型，逻辑层发布事件，UI 订阅并更新 Model。
- Q: 除了已有的资源同步，哪些手动操作是目前最频繁且最需要优先自动化的？ → A: 构建综合工具链，包含资源处理、环境校验及测试触发。
- Q: 游戏内的实体渲染是否也要统一收口到 UI 层的“游戏视图组件”中？ → A: 是，由 UI 层全权委派，包含一个 `GameViewport` 负责底层实体渲染。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - UI 架构重构 (Priority: P1)

作为开发者，我希望所有的 UI 状态和逻辑都集中在专门的 UI 层级，而游戏逻辑层只暴露数据接口，以便于管理复杂的界面交互。

**Why this priority**: 核心架构需求，是后续复杂 UI 功能的基础。
**Independent Test**: 在不启动游戏逻辑引擎的情况下，能够通过 Mock 数据独立渲染 UI 界面。
**Acceptance Scenarios**:

1. **Given** 游戏逻辑状态发生变化，**When** UI 层监听该变化，**Then** 视图组件自动更新显示。
2. **Given** 逻辑层代码，**When** 全局搜索 UI 相关引用，**Then** 搜索结果应为空（除必要的接口定义外）。

---

### User Story 2 - 终端操作自动化 (Priority: P1)

作为开发者，我希望通过简单的 Gradle 任务完成原本需要手动输入的复杂终端命令，以符合项目开发规范。

**Why this priority**: 宪章合规性需求，降低开发门槛。
**Independent Test**: 执行 `./gradlew [TaskName]` 能完全替代之前的多步手动指令。
**Acceptance Scenarios**:

1. **Given** 需要进行资源同步或环境检查，**When** 执行对应的 Gradle 任务，**Then** 任务自动完成所有步骤并给出结果。

---

### User Story 3 - 渲染解耦 (Priority: P2)

作为开发者，我希望渲染引擎直接引用 UI 层定义的组件，而不是在渲染循环中混杂 UI 构建逻辑。

**Why this priority**: 优化渲染性能，提升代码整洁度。
**Independent Test**: 检查 `render()` 方法，其中应仅包含对 UI 层级的统一调用。
**Acceptance Scenarios**:

1. **Given** 进入渲染循环，**When** 执行 UI 渲染，**Then** 渲染器通过 UI 层提供的统一接口完成绘制。

---

### Edge Cases

- **循环依赖**: 在解耦过程中可能出现的 A->B->A 引用问题。
- **性能开销**: 数据绑定和事件监听机制在大规模实体下的性能表现。
- **脚本兼容性**: 自动化脚本在不同操作系统（Windows/Linux）下的表现。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: UI 模型抽象 (UI Model Abstraction): 建立 `com.staraxis.game.client.ui.model` 包，用于管理 UI 状态。
- **FR-002**: 逻辑层清理 (Logic Decoupling): 移除 `core` 模块中所有对 `scene2d` 或特定 UI 组件的引用。
- **FR-003**: 数据驱动 (Data Binding): UI 组件通过事件总线或监听器订阅逻辑层的数据变更事件，实现状态的自动同步。
- **FR-004**: 自动化工具链 (Gradle Toolchain): 实现包含资源压缩、环境自检（要求 JDK 21+）及全量测试在内的 Gradle 任务集。
- **FR-005**: 委派渲染架构 (Delegated Rendering): `Main` 类将渲染权完全委派给 UI 层，实体渲染作为 UI 层的一个 `GameViewport` 组件实现。

### Key Entities

- **UIManager**: 负责管理所有 UI 屏幕和组件的顶层实体。
- **UIModel**: 存储特定界面的显示数据（如按钮文本、进度条比例）。
- **GradleTask**: 用于替代终端命令的自动化任务定义。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core` 模块中的 UI 依赖项数量降为 0。
- **SC-002**: 常用终端操作（如资源同步）的执行步数从 3 步以上减少为 1 步 (Gradle Task)。
- **SC-003**: UI 状态变更后的响应延迟维持在 16ms 以内。

