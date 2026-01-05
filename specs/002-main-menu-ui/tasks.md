# Tasks: Main Menu UI (游戏主界面)

**Input**: Design documents from `/specs/002-main-menu-ui/`
**Prerequisites**: spec.md, plan.md, research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

## Path Conventions

- **StarAxis LWJGL3**: `lwjgl3/src/main/java/com/staraxis/game/client/`
- **Resources**: `lwjgl3/src/main/resources/`

---

## Phase 1: Setup (Infrastructure)

**Purpose**: Project initialization for UI

- [X] T001 [P] [US1] Add basic UI skin resources (programmatic skin implemented in Main.java)
- [X] T002 [P] [US2] Create `SettingsManager /* 配置管理器 */` in `client/config/` for LibGDX Preferences persistence

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core UI components and screen management

- [X] T003 [P] [US1] Create `Toast /* 简易提示组件 */` in `client/ui/components/` for placeholder feedback
- [X] T004 [US1] Update `Main.java` or create a base `Game` class to support `setScreen` for LibGDX

---

## Phase 3: User Story 1 - 游戏启动与主菜单浏览 (Priority: P1)

**Goal**: Functional main menu navigation shell.

- [X] T005 [P] [US1] Create `MainMenuScreen /* 主菜单页面 */` shell in `client/ui/`
- [X] T006 [US1] Implement `MainMenuScreen` layout using Scene2D `Table` with 5 vertical buttons
- [X] T007 [US1] Add button style and hover/click visual feedback using `Skin`

---

## Phase 4: User Story 2 - 系统设置调整 (Priority: P1)

**Goal**: Working settings page with resolution and fullscreen support.

- [X] T008 [P] [US2] Create `SettingsScreen /* 设置页面 */` shell in `client/ui/`
- [X] T009 [US2] Implement dynamic resolution detection logic using `Gdx.graphics.getDisplayModes()`
- [X] T010 [US2] Implement `SettingsScreen` UI components (SelectBox for resolution, CheckBox for fullscreen, SelectBox for FPS)
- [X] T011 [US2] Implement "Apply" logic to update `Graphics` state and save to `SettingsManager`
- [X] T012 [US2] Wire navigation between `MainMenuScreen` and `SettingsScreen`

---

## Phase 5: User Story 3 & 4 - 退出与占位反馈 (Priority: P2/P3)

**Goal**: Complete the menu interaction loop.

- [X] T013 [US3] Implement Exit button logic calling `Gdx.app.exit()`
- [X] T014 [US4] Bind New Game, Load, and Multiplayer buttons to show `Toast` ("Feature under development")

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2.
2. Implement Phase 3 (US1).
3. **VALIDATE**: Run `:lwjgl3:run` and verify the main menu appears and buttons respond to hover.

### Incremental Delivery

1. Foundation -> Main Menu (US1) -> Settings (US2) -> Polish (US3/4).
2. Each story adds testable value.

---

## Notes

- All UI code must stay in the `lwjgl3` module.
- Use Chinese comments for descriptions as per constitution (`name /* description */`).
- Verify each screen independently by temporarily setting it as the initial screen in `Main.java`.
