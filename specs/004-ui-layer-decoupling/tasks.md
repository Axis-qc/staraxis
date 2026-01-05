# Tasks: UI Layer Decoupling & Command Restrictions (UI层解耦与指令限制)

**Feature**: UI Layer Decoupling & Command Restrictions
**Status**: Ready for Implementation
**Plan**: [plan.md](./plan.md)

## Phase 1: Setup (项目初始化)

**Goal**: 配置自动化工具链基础。

- [ ] T001 [P] 创建自动化工具脚本 `G:\games\staraxis\scripts\dev-tools.gradle`
- [ ] T002 [P] 在根目录 `G:\games\staraxis\build.gradle` 中导入并配置 `dev-tools.gradle`
- [ ] T003 [P] 创建 UI 层级目录：`lwjgl3/src/main/java/com/staraxis/game/client/ui/model`, `.../manager`, `.../view`

---

## Phase 2: Foundational (核心架构与 UI 层级)

**Goal**: 实现核心逻辑与独立 UI 层的契约解耦。
**Independent Test**: 执行 `./gradlew checkDecoupling` 验证 `core` 模块无 UI 依赖。

- [ ] T004 [P] 定义全局事件总线接口 `G:\games\staraxis\core\src\main\java\com\staraxis\game\core\api\EventBus.java`
- [ ] T005 [P] 实现基于 LibGDX 的事件总线 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\manager\LibGdxEventBus.java`
- [ ] T006 [P] 实现基础 UI 模型类 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\model\BaseUIModel.java`
- [ ] T007 [P] 实现解耦检查任务 `checkDecoupling` 在 `scripts/dev-tools.gradle` 中

---

## Phase 3: User Story 1 - UI 架构重构 (Priority: P1)

**Goal**: 实现主界面与设置界面的数据驱动。
**Independent Test**: 通过 Mock 事件更新 `MainMenuModel`，验证 View 自动刷新。

- [ ] T008 [P] [US1] 创建主菜单数据模型 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\model\MainMenuModel.java`
- [ ] T009 [P] [US1] 创建设置界面数据模型 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\model\SettingsModel.java`
- [ ] T010 [US1] 重构 `MainMenuScreen` 使用 `MainMenuModel` 进行渲染
- [ ] T011 [US1] 重构 `SettingsScreen` 使用 `SettingsModel` 进行渲染
- [ ] T012 [US1] 在 `LocalizationService` 切换语言时，通过 `EventBus` 发布变更事件

---

## Phase 4: User Story 3 - 渲染解耦 (Priority: P2)

**Goal**: 统一渲染权委派逻辑。
**Independent Test**: 检查 `Main.render` 方法，确认其仅调用 `UIManager`。

- [ ] T013 [P] [US3] 实现顶层 UI 管理器 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\manager\UIManager.java`
- [ ] T014 [P] [US3] 实现游戏世界视图组件 `G:\games\staraxis\lwjgl3\src\main\java\com\staraxis\game\client\ui\view\GameViewport.java`
- [ ] T015 [US3] 重构 `G:\games\staraxis\lwjgl3\src\main\java\io\staraxis\Main.java`，将所有渲染逻辑委派给 `UIManager`

---

## Phase 5: User Story 2 - 终端操作自动化 (Priority: P1)

**Goal**: 落实“禁止频繁使用终端命令”原则。
**Independent Test**: 运行 `./gradlew syncAssets` 和 `./gradlew checkEnv` 成功。

- [ ] T016 [P] [US2] 在 `dev-tools.gradle` 中实现 `checkEnv` 任务（校验 JDK 和目录）
- [ ] T017 [P] [US2] 在 `dev-tools.gradle` 中实现 `syncAssets` 任务（资源校验与同步）
- [ ] T018 [P] [US2] 在 `dev-tools.gradle` 中实现 `runFullTests` 任务（全量测试触发）
- [ ] T019 [US2] 修改 `Main.java` 启动逻辑，集成环境自检调用

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 架构加固与清理。

- [x] T020 [P] 移除 `core` 模块中残留的所有 `com.badlogic.gdx.scenes.scene2d` 引用
- [x] T021 [P] 文档更新：在 `README.md` 中增加自动化工具链使用说明
- [x] T022 验证所有终端操作均已通过 Gradle 脚本实现 [Principles]
- [x] T023 运行 `G:\games\staraxis\specs\004-ui-layer-decoupling\quickstart.md` 验证全流程

---

## Dependencies & Execution Order

1. **Setup (Phase 1)**：必须首先完成，建立脚本基础。
2. **Foundational (Phase 2)**：核心事件机制与模型基类是 US1 的前提。
3. **UI 重构 (Phase 3)** 与 **渲染解耦 (Phase 4)** 可并行开发。
4. **自动化工具链 (Phase 5)** 需在最后进行全局集成。

## Implementation Strategy

### MVP First (UI Model & Decoupling)
1. 完成工具链脚本框架。
2. 实现 `EventBus` 并完成 `MainMenuModel` 的重构。
3. **验证点**：`core` 模块通过解耦检查任务。
