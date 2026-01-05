# Feature Specification: UI & Input Polishing (UI与输入优化)

**Feature Branch**: `006-ui-input-polishing`
**Created**: 2026-01-05
**Status**: Draft
**Input**: User description: "更新本地化文本和美化ui控件，增加输入控制器，wasd控制镜头平移，滚轮控制缩放"

## Clarifications

### Session 2026-01-05
- Q: UI 视觉风格定位 → A: 科技感风格 (Futuristic): 半透明背景、霓虹高亮、细线边框。
- Q: 镜头平移交互细节 (WASD) → A: 平滑加速度/惯性: 启动有缓冲，停止有滑动感。
- Q: 滚轮缩放中心点 → A: 以鼠标指针为中心: 缩放时视角会自动向指针处对焦。
- Q: 输入焦点拦截范围 → A: 严格拦截 (Strict): 只要任何输入框处于焦点状态，所有键盘镜头控制（WASD）立即失效。
- Q: UI 美化资源来源 → A: 程序化增强: 使用代码生成 NinePatch 边框、渐变背景，并通过颜色叠加实现霓虹效果。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 界面视觉美化与本地化 (Priority: P1)

作为玩家，我希望看到风格统一且经过美化的界面，并且所有文字都以我选择的语言正确显示，以便获得更好的沉浸感。

**Why this priority**: 良好的视觉表现是游戏品质的直接体现，本地化是多语言支持的基础。
**Independent Test**: 通过手动运行游戏，观察所有 UI 控件（滑块、输入框、按钮）的样式是否美观，并切换语言验证文本是否正确更新。
**Acceptance Scenarios**:

1. **Given** 游戏启动到主菜单，**When** 点击新游戏，**Then** 配置界面的滑块和输入框呈现统一的艺术风格。
2. **Given** 在设置中切换语言，**When** 返回主菜单或进入配置界面，**Then** 所有新添加的 UI 文本（如“生成中...”、“宜居比例”等）都显示为对应语言。

---

### User Story 2 - 键盘与鼠标组合控制 (Priority: P1)

作为玩家，我希望通过键盘 WASD 进行镜头平移，通过鼠标滚轮进行缩放，以便更高效地浏览星域。

**Why this priority**: 传统的键鼠组合控制是 PC 游戏的标准交互方式，能显著提升操作效率。
**Independent Test**: 在 WorldScreen 中使用 WASD 键移动镜头，使用滚轮缩放，验证交互是否灵敏。
**Acceptance Scenarios**:

1. **Given** 处于 WorldScreen，**When** 按下 W/A/S/D 键，**Then** 镜头平滑地上下左右移动。
2. **Given** 处于 WorldScreen，**When** 向前/向后滚动鼠标滚轮，**Then** 地图按预期的语义缩放层级进行缩放。

---

### Edge Cases

- 当同时按下相反方向键（如 W 和 S）时，镜头应保持静止。
- 缩放达到最小或最大限制时，滚轮操作应被忽略。
- 在输入框（TextField）获取焦点时，WASD 键应优先用于文本输入而非镜头控制。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: **本地化覆盖 (Localization Coverage)**: 所有新增的 UI 文本（包括加载状态、错误提示等）必须在多语言资源文件中定义，支持中英文切换。
- **FR-002**: **UI 控件美化 (UI Skinning)**: 采用**科技感风格 (Futuristic)**，通过**程序化增强**（如 NinePatch、ShapeRenderer 结合现有纹理）为滑动条 (Slider)、文本输入框 (TextField) 和下拉选择框 (SelectBox) 增加半透明背景、霓虹高亮和细线边框，确保与星际主题一致。
- **FR-003**: **键盘输入映射 (WASD Mapping)**: 实现带**平滑惯性**的输入控制器，将键盘 W/A/S/D 键映射为镜头的上下左右平移，支持启动缓冲与停止滑动。
- **FR-004**: **平滑缩放 (Smooth Zooming)**: 鼠标滚轮缩放应**以鼠标指针为中心**平滑过渡，缩放时视角自动向指针处对焦。
- **FR-005**: **输入焦点管理 (Input Focus Handling)**: 当文本输入框处于激活焦点状态时，键盘输入应优先用于文本编辑，**严格拦截**所有键盘镜头平移控制（WASD 立即失效）。

### Key Entities *(include if feature involves data)*

- **UI 皮肤定义 (UI Skin)**: 描述 UI 控件的纹理、颜色、字体和状态样式（悬停、按下等）。
- **输入映射器 (Input Map)**: 建立物理按键（如 W 键）与逻辑操作（如镜头向上移动）之间的关联。
- **本地化字典 (Localization Bundle)**: 存储多语言文本映射表。

## Assumptions & Dependencies

- **Assumptions**: 
  - 用户使用的是具备标准滚轮的鼠标和 QWERTY 布局的键盘。
  - 现有的 `CameraController` 结构可以被扩展或替换以支持新的映射。
- **Dependencies**: 
  - 依赖 `LocalizationService` 提供的字体渲染能力以显示特殊符号或美化文本。
  - 依赖 `UIManager` 的焦点管理机制。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% 的新增 UI 字符串完成中英文本地化。
- **SC-002**: 镜头平移响应延迟感不可察觉（随帧更新）。
- **SC-003**: 所有 UI 控件在悬停、按下、禁用状态下均有明显的视觉区分。

