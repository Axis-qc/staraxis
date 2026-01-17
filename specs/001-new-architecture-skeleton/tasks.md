# Tasks: Scene2D 主菜单 UI 骨架（Gui 管理器）

**Input**: Design documents from `/specs/001-new-architecture-skeleton/`
**Prerequisites**: plan.md (required), spec.md (required)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 清理 JCEF 依赖阻塞，确保 `ui` 模块依赖正确，并使用 `assets/ui` 下的现成 `uiskin` 皮肤资源。

- [x] T001 从 `lwjgl3/build.gradle` 移除 JCEF 远程依赖（`me.friwi:jcef-api`, `me.friwi:jcef`）。
- [x] T002 在 `ui/build.gradle` 中添加 LibGDX 核心依赖：`implementation "com.badlogicgames.gdx:gdx:$gdxVersion"`。
- [x] T003 [P] 清理 `specs/001-new-architecture-skeleton/contracts/` 中 WebView 相关契约。
- [x] T004 [P] 确认 `assets/ui/` 下已存在可用的 `uiskin.json`（以及其引用的字体/贴图资源），并明确 UI 统一通过 `Gdx.files.internal("ui/uiskin.json")` 加载。（T007/T008/T012 依赖此项）
- [x] T005 运行 `./gradlew :ui:compileJava --no-daemon`，确保 `ui` 子模块可编译（门槛任务）。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立可运行的桌面启动器与 Scene2D UI 框架（`ClientGame` 作为游戏窗口管理 `Gui`，`Gui` 按类型注册/调度组件）。

- [x] T006 在 `ui/src/main/java/staraxis/` 下创建 `ClientGame.java` 作为 LibGDX 应用入口，它将作为“游戏窗口”持有并管理 `Gui` 实例。
- [x] T007 [P] 在 `ui/src/main/java/staraxis/ui/` 下创建 `Gui.java`（UI 管理器：持有 `Stage`；提供按组件类型注册/管理的接口）。
- [x] T008 [P] 在 `ui/src/main/java/staraxis/ui/widgets/` 下创建 `DevelopingDialog.java`（统一“开发中”弹窗组件，使用 `uiskin` 皮肤资源）。
- [x] T009 [P] 在 `ui/src/main/java/staraxis/ui/screens/` 下创建 `MainMenuScreen.java`（Scene2D 主菜单，作为可由 `Gui` 按类型注册/管理的组件；使用 `uiskin` 皮肤资源）。
- [x] T010 在 `ClientGame.java` 中初始化 `Stage` 与 `Gui`，并在启动时注册 `MainMenuScreen` 与 `DevelopingDialog`。
- [x] T011 在 `ClientGame.java` 中设置默认启动流程：通过 `Gui` 显示主菜单。
- [x] T012 在 `lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java` 中确认启动入口引用的是 `staraxis.ClientGame` 且能编译运行。（前置：`settings.gradle` 已 include `ui` 且 `lwjgl3` 依赖 `:ui`）

**Checkpoint**: Foundation ready - 能启动窗口并显示 Scene2D UI（至少黑底 + 可见 UI）。

---

## Phase 3: User Story 1 - 启动即进入主菜单 (Priority: P1) 🎯 MVP

**Goal**: 启动后展示 6 个主菜单按钮。

**Independent Test**: 运行 `Lwjgl3Launcher`，窗口内可见 6 个按钮。

### Implementation for User Story 1

- [x] T013 [US1] 在 `MainMenuScreen.java` 中创建并布局 6 个按钮：新游戏、加载游戏、多人游戏（未来规划）、舰船设计器、设置、退出游戏（使用 `uiskin` 皮肤资源）。
- [x] T014 [US1] 在 `Gui.java` 中提供主菜单显示方法（例如 `show(MainMenuScreen.class)`），用于把主菜单组件添加到舞台并保证可见。
- [x] T015 [US1] 确保主菜单布局在常见窗口尺寸下可用（按钮不重叠且可点击）。

**Checkpoint**: User Story 1 完成，可独立验证 UI 展示。

---

## Phase 4: User Story 2 - 主菜单按钮可点击并有明确反馈 (Priority: P2)

**Goal**: 除“退出游戏”外，所有按钮点击均弹出“开发中”；“退出游戏”退出客户端。

**Independent Test**: 逐个点击按钮，观察反馈符合 spec。

### Implementation for User Story 2

- [x] T016 [US2] 在 `Gui.java` 中实现统一动作分发：
  - 接收到 `EXIT_CLICK` 时调用 `Gdx.app.exit()`。
  - 接收到其他点击事件时，通过 `Gui` 显示 `DevelopingDialog`。
- [x] T017 [US2] 在 `MainMenuScreen.java` 中将 6 个按钮点击转换为动作事件并交给 `Gui`（不直接在 Screen 内处理业务）。
- [x] T018 [US2] 在 `DevelopingDialog.java` 中确保提示文案固定为“开发中”。

**Checkpoint**: US1+US2 完成，主菜单可交互。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 处理资源缺失与窗口变化等边界情况，并更新文档。

- [ ] T019 [P] 更新 `specs/001-new-architecture-skeleton/quickstart.md`：按 Scene2D+Gui 口径说明构建/运行/验收步骤。
- [ ] T020 [P] 更新 `specs/001-new-architecture-skeleton/data-model.md`：从 WebView 事件调整为 Scene2D 内部按钮动作。
- [ ] T021 运行一次 `./gradlew :lwjgl3:run` 手工验证，并明确验收标准：
  - 窗口启动，可见 6 个按钮
  - 点击非退出按钮，弹出“开发中”
  - 点击退出按钮，窗口关闭且 Gradle 进程结束

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup completion.
- **User Story 1 (Phase 3)**: Depends on Foundational completion.
- **User Story 2 (Phase 4)**: Depends on User Story 1 completion.
- **Polish (Phase 5)**: Depends on US1 + US2 being complete.

### Parallel Opportunities

- `T003`, `T004` can run in parallel.
- `T007`, `T008`, `T009` can run in parallel.
- `T019`, `T020` can run in parallel.

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 + Phase 2
2. Complete Phase 3 (US1)
3. **STOP and VALIDATE**: 此时应能看到静态主菜单（6 按钮）。
