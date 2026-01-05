# Tasks: Localization & UI Beautification (本地化与界面美化)

**Feature**: Localization & UI Beautification
**Status**: Ready for Implementation
**Plan**: [plan.md](./plan.md)

## Phase 1: Setup (项目初始化)

**Goal**: 配置 Gradle 依赖及初始化资源目录。

- [x] T001 [P] 在 `G:\games\staraxis\core\build.gradle` 中添加 `gdx-freetype` 依赖
- [x] T002 [P] 创建资源目录：`G:\games\staraxis\assets\fonts`, `G:\games\staraxis\assets\i18n`, `G:\games\staraxis\assets\textures`
- [x] T003 [P] 将 `AlibabaPuHuiTi-3-65-Medium.ttf` 放入 `G:\games\staraxis\assets\fonts\`
- [x] T004 [P] 创建中文语言包 `G:\games\staraxis\assets\i18n\messages.properties`
- [x] T005 [P] 创建英文语言包 `G:\games\staraxis\assets\i18n\messages_en.properties`

---

## Phase 2: Foundational (核心基础架构)

**Goal**: 实现本地化服务核心逻辑，为所有 UI 组件提供基础支持。
**Independent Test**: 通过 JUnit 或简单的命令行测试验证 `LocalizationService` 能正确加载 Bundle 并返回对应语言的文本。

- [x] T006 [P] 定义语言变更监听接口 `G:\games\staraxis\core\src\main\java\com\staraxis\game\core\i18n\LanguageChangeListener.java`
- [x] T007 [P] 实现本地化服务类 `G:\games\staraxis\core\src\main\java\com\staraxis\game\core\i18n\LocalizationService.java`
- [x] T008 [P] 在 `LocalizationService` 中集成 `FreeTypeFontGenerator` 以加载 `AlibabaPuHuiTi-3-65-Medium.ttf`
- [x] T009 [P] 实现 `I18NBundle` 的动态加载与切换逻辑

**Checkpoint**: 核心本地化服务可独立运行，支持字体生成和多语言文本获取。

---

## Phase 3: User Story 1 - 语言切换与即时应用 (Priority: P1)

**Goal**: 实现 UI 组件对语言变更的实时响应。
**Independent Test**: 在代码中手动触发 `setLanguage`，验证已显示的 `MainMenuScreen` 文字立即更新。

- [x] T010 [US1] 在 `LocalizationService` 中完善监听器通知机制
- [x] T011 [US1] 修改 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\MainMenuScreen.java` 实现 `LanguageChangeListener`
- [x] T012 [US1] 重构 `MainMenuScreen` 中的所有 Label 和 Button 文本，改为通过 `LocalizationService.get()` 获取

**Checkpoint**: 主界面已支持实时多语言切换。

---

## Phase 4: User Story 3 - 设置项扩展 (Priority: P1)

**Goal**: 在设置界面提供语言切换入口并持久化配置。
**Independent Test**: 在设置中切换语言并点击应用，重启游戏后语言保持不变。

- [x] T013 [US3] 在 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\SettingsScreen.java` 中添加语言选择下拉框 (SelectBox)
- [x] T014 [US3] 实现设置持久化逻辑，使用 `Gdx.app.getPreferences("staraxis-settings")` 保存 `language` 键值
- [x] T015 [US3] 在游戏启动时（`LocalizationService.init`）读取 `Preferences` 加载对应语言

**Checkpoint**: 设置界面功能完备，支持语言持久化存储。

---

## Phase 5: User Story 2 - 界面美化与视觉反馈 (Priority: P2)

**Goal**: 提升主界面的视觉质量，增加动态反馈。
**Independent Test**: 运行游戏，观察主菜单是否有视差背景效果，且按钮在悬停时是否有缩放/发光动画。

- [x] T016 [P] [US2] 实现视差背景类 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\components\ParallaxBackground.java`
- [x] T017 [P] [US2] 实现带动画反馈的按钮类 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\components\AnimatedButton.java`
- [x] T018 [US2] 在 `MainMenuScreen` 中集成 `ParallaxBackground` 并配置多层星空资源
- [x] T019 [US2] 将 `MainMenuScreen` 中的普通按钮替换为 `AnimatedButton`

**Checkpoint**: 游戏主界面视觉效果达到预期，具备科幻氛围。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 细节打磨及边缘情况处理。

- [x] T020 [P] 实现“自动缩放 + 跑马灯滚动”复合文本组件，用于处理超长翻译文本
- [x] T021 [P] 补齐所有 UI 提示信息（如“功能开发中”）的本地化 Key
- [x] T022 运行 `G:\games\staraxis\specs\003-localization-ui-settings\quickstart.md` 中的所有验证场景
- [x] T023 最终代码清理及文档更新

---

## Dependencies & Execution Order

1. **Setup (Phase 1)** 必须首先完成，以确保环境和资源就绪。
2. **Foundational (Phase 2)** 必须在任何 User Story 之前完成。
3. **US1 和 US3** 可并行或按顺序开发，它们共同构成了本地化功能的核心。
4. **US2 (美化)** 优先级稍低，可在核心功能稳定后进行。
5. **Polish (Phase 6)** 最后执行，确保所有边缘情况（如文本溢出）得到妥善处理。

## Implementation Strategy

### MVP First (User Story 1 & 3)
1. 完成 Phase 1 和 Phase 2。
2. 完成 US1 实现基本的实时切换。
3. 完成 US3 实现配置持久化。
4. **验证点**: 玩家可以在设置中切换语言，且重启后生效。

### Parallel Execution Examples
- 开发者 A: T006-T009 (核心架构)
- 开发者 B: T001-T005 (环境准备与资源)
- 架构完成后: 开发者 A (US1+US3), 开发者 B (US2)
