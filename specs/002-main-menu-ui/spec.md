# Feature Specification: Main Menu UI (游戏主界面)

**Feature Branch**: `002-main-menu-ui`  
**Created**: 2026-01-05  
**Status**: Draft  
**Input**: User description: "创建游戏主界面，包含新游戏、加载游戏、多人游戏、设置和退出。其中新游戏、加载、多人仅占位。"

## Overview

### Description
实现《StarAxis》游戏的主入口界面，提供玩家进入游戏、调整配置及退出程序的功能。该界面是玩家启动游戏后的第一个交互点。

## Clarifications

### Session 2026-01-05
- Q: 设置界面显示的分辨率选项是通过读取当前显示器支持的列表动态生成，还是预设几个固定选项？ → A: 动态获取当前显示器支持的所有分辨率。
- Q: 当玩家点击占位按钮（新游戏、加载、多人）时，最理想的反馈方式是什么？ → A: 弹出“功能开发中”的文字提示 (Toast/Dialog)。
- Q: 设置界面是否需要提供“全屏/窗口模式”的切换选项？ → A: 支持全屏和窗口模式切换。

### Business Value
- 提供统一的游戏入口。
- 允许玩家调整显示设置（分辨率、帧率），提升游戏体验。
- 为后续功能（新游戏、存档加载、多人联机）预留标准接口。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 游戏启动与主菜单浏览 (Priority: P1)

作为玩家，当我启动游戏时，我希望看到一个清晰的主菜单，列出所有核心选项，以便我选择下一步操作。

**Why this priority**: 核心导航功能，是所有后续流程的起点。

**Independent Test**: 启动程序后，界面正确渲染 5 个按钮：新游戏、加载游戏、多人游戏、设置、退出。

**Acceptance Scenarios**:

1. **Given** 游戏启动完成，**When** 进入主界面，**Then** 界面居中显示菜单列表。
2. **Given** 鼠标悬停在按钮上，**When** 悬停，**Then** 按钮应有明显的视觉反馈（如变色或缩放）。

---

### User Story 2 - 系统设置调整 (Priority: P1)

作为玩家，我希望能够调整游戏的分辨率和帧率限制，以匹配我的显示器性能和个人偏好。

**Why this priority**: 基础体验需求，确保游戏在不同硬件上都能正常运行。

**Independent Test**: 进入设置页面，修改分辨率并点击应用，窗口大小应随之变化。

**Acceptance Scenarios**:

1. **Given** 在主菜单点击“设置”，**When** 点击，**Then** 切换到设置子页面。
2. **Given** 修改了分辨率选项，**When** 点击“应用”，**Then** 系统调用显示驱动更新窗口状态。

---

### User Story 3 - 退出游戏 (Priority: P2)

作为玩家，我希望在完成游戏后能够安全、快速地关闭程序。

**Why this priority**: 完整的交互闭环。

**Independent Test**: 点击“退出游戏”按钮，程序正常结束进程。

**Acceptance Scenarios**:

1. **Given** 在主菜单点击“退出游戏”，**When** 点击，**Then** 程序直接退出。

---

### User Story 4 - 占位功能反馈 (Priority: P3)

作为玩家，当我点击尚未实现的功能（新游戏、加载游戏、多人游戏）时，系统应给予反馈。

**Why this priority**: 提升用户体验，避免点击无反应。

**Independent Test**: 点击“新游戏”，按钮点击后无逻辑跳转但有点击效果。

**Acceptance Scenarios**:

1. **Given** 点击“新游戏”，**When** 点击，**Then** 控制台或界面无逻辑跳转。

---

### Edge Cases

- **不支持的分辨率**: 当玩家选择显示器不支持的分辨率时，如何处理？（目前假设选择列表由系统生成）。
- **资源丢失**: 如果字体资源加载失败，应有默认方案保证文字可读。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 菜单导航 (Menu Navigation): 提供纵向排列的按钮：新游戏 (New Game)、加载游戏 (Load Game)、多人游戏 (Multiplayer)、设置 (Settings)、退出 (Exit)。
- **FR-002**: 占位处理 (Placeholder Handling): 新游戏、加载游戏、多人游戏点击后弹出“功能开发中”的文字提示。
- **FR-003**: 设置项 (Settings Items): 分辨率选项需动态获取显示器支持的列表；帧率限制支持 30, 60, 无限制；支持全屏/窗口模式切换。
- **FR-004**: 退出逻辑 (Exit Logic): 点击“退出”关闭程序。
- **FR-005**: 模块化设计 (Modular Design): 系统必须采用模块化架构，严禁硬编码。
- **FR-006**: 命名与注释 (Naming & Docs): 必须在名称后加括号说明用途，并包含完整的文件头注释。

### Key Entities

- **SettingsModel**: 存储分辨率、帧率等数据。
- **MainMenuScreen**: 负责渲染主菜单界面。
- **SettingsScreen**: 负责渲染设置界面。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 玩家启动程序后 2 秒内看到主菜单。
- **SC-002**: 设置分辨率后，窗口大小切换成功率 100%。
- **SC-003**: 所有按钮点击在 16ms 内产生视觉反馈。
- **SC-004**: 退出按钮点击后程序立即终止。
