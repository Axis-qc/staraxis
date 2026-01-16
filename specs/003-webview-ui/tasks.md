# Tasks: WebView 嵌入与开始界面（003）

**Input**: Design documents from `/specs/003-webview-ui/`

- `spec.md`
- `plan.md`
- `research.md`
- `data-model.md`
- `quickstart.md`
- `contracts/ui-host-contract.md`

**Tests**: 本期未要求自动化测试；以 `quickstart.md` 的手工验收步骤为准。

**Organization**: 任务按用户故事组织，确保每个故事都可独立验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件/无依赖）
- **[Story]**: [US1]/[US2]/[US3]…
- 每条任务包含明确文件路径

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 统一确认 UI 资源入口路径：`assets/ui/start-menu/index.html`（对齐 `specs/003-webview-ui/spec.md` 与 `specs/003-webview-ui/contracts/ui-host-contract.md`）
- [X] T002 代码库扫描并记录客户端窗口/启动入口与退出逻辑位置（输出到 `specs/003-webview-ui/research.md` 补充段落，标注关键类/文件路径）
- [X] T003 [P] 确认 WebView 接入方案尚不存在于代码中（搜索并记录结果到 `specs/003-webview-ui/research.md`，为后续接入做基线）

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: 完成该阶段后，才能开始实现任何用户故事。

- [X] T004 [P] 在 `lwjgl3/build.gradle` 中添加 JCEF 的 Java 依赖
- [ ] T005 [P] 创建用于存放 JCEF 原生二进制文件的目录，例如 `lwjgl3/natives/jcef-windows/`
- [ ] T006 [P] 将 JCEF 的 Windows 原生包内容放置到 `lwjgl3/natives/jcef-windows/` 目录中，并确保该目录会随打包产物一起分发
- [ ] T007 在 `lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java` 中配置 JCEF 初始化参数，包括指向原生库路径
- [ ] T008 定义并实现宿主侧“UI 入口加载”策略（基于 `assets/ui/start-menu/index.html`），并确保可检测加载失败（挂载点：`client/src/main/java/staraxis/ClientGame.java` 的 `create()` / `render()`；窗口入口：`lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java`）
- [ ] T009 定义并实现 UI ↔ Host 最小桥接通道（参考 `specs/003-webview-ui/contracts/ui-host-contract.md`），至少支持：`ui.requestPlaceholder`、`ui.requestQuit`、`ui.quitConfirmResult`、`host.showPlaceholder`、`host.showQuitConfirm`、`host.webViewLoadFailed`（宿主侧实现位置：`client/src/main/java/staraxis/ClientGame.java`；如需抽离可在 `client/src/main/java/staraxis/client/ui/` 下新增桥接类）
- [ ] T010 实现“兜底错误层”的宿主触发：当入口加载失败时触发 `host.webViewLoadFailed`，并保证 UI 侧出现“退出游戏”按钮（宿主触发：`client/src/main/java/staraxis/ClientGame.java`；UI 资源：`assets/ui/start-menu/`）
- [ ] T011 在 `client/src/main/java/staraxis/ClientGame.java` 的 `dispose()` 方法中确保调用 JCEF 的资源释放/关闭方法，防止进程残留

**Checkpoint**: Foundation ready（JCEF 能初始化，且能加载 UI 或进入错误层）

---

## Phase 3: User Story 1 - 开始界面可见（Priority: P1） 🎯 MVP

**Goal**: 启动客户端后进入开始界面，展示 6 个按钮（FR-W01/FR-W04）。

**Independent Test**:
- 启动客户端后可见开始界面
- 6 个按钮均可见且可点击

### Implementation

- [ ] T012 [US1] 在 UI 主入口 `assets/ui/start-menu/index.html` 实现开始界面基础布局与 6 个按钮（资源路径：`assets/ui/start-menu/index.html` 及其引用资源）
- [ ] T013 [US1] 宿主启动后加载 `assets/ui/start-menu/index.html` 并显示在窗口中（窗口入口：`lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java`；挂载点：`client/src/main/java/staraxis/ClientGame.java`）
- [ ] T014 [US1] 实现按钮点击事件向 Host 发送消息（`ui.requestPlaceholder` / `ui.requestQuit`）（资源路径：`assets/ui/start-menu/index.html` 或其 JS 文件）

**Checkpoint**: 开始界面可见、可点击

---

## Phase 4: User Story 2 - 占位按钮弹“开发中”（Priority: P2）

**Goal**: 点击“新游戏/加载游戏/多人游戏/舰船编辑器/设置”弹出“开发中”提示，支持手动关闭 + 自动消失（FR-W02）。

**Independent Test**:
- 点击任一占位按钮，300ms 内出现“开发中”提示
- 提示可手动关闭
- 提示会自动消失

### Implementation

- [ ] T015 [US2] Host 收到 `ui.requestPlaceholder` 后回 `host.showPlaceholder`（payload 满足 autoDismiss=true,dismissible=true）（宿主侧：`client/src/main/java/staraxis/ClientGame.java` 或其抽离类）
- [ ] T016 [US2] UI 接收 `host.showPlaceholder` 并展示提示层/弹窗，支持关闭按钮与自动消失（资源路径：`assets/ui/start-menu/` 下相关 JS/CSS）
- [ ] T017 [US2] 确保占位提示不会导致按钮被永久遮挡（关闭后可继续操作）（资源路径：`assets/ui/start-menu/`）

**Checkpoint**: 占位按钮交互全部通过（按 quickstart 验收）

---

## Phase 5: User Story 3 - 退出可用（Priority: P3）

**Goal**: 点击“退出游戏”弹确认框，确认后退出客户端（FR-W03）。

**Independent Test**:
- 点击“退出游戏”出现确认对话框（确认/取消）
- 点击取消：回到开始界面
- 点击确认：客户端 1s 内退出

### Implementation

- [ ] T018 [US3] Host 收到 `ui.requestQuit` 后回 `host.showQuitConfirm`（宿主侧：`client/src/main/java/staraxis/ClientGame.java` 或其抽离类）
- [ ] T019 [US3] UI 展示退出确认框并发送 `ui.quitConfirmResult`（confirmed=true/false）（资源路径：`assets/ui/start-menu/`）
- [ ] T020 [US3] Host 收到 `ui.quitConfirmResult.confirmed=true` 后执行客户端退出流程（调用 `com.badlogic.gdx.Gdx.app.exit()`）；confirmed=false 不退出（宿主侧：`client/src/main/java/staraxis/ClientGame.java` 或其抽离类）

**Checkpoint**: 退出流程可用且稳定

---

## Phase 6: User Story 4 - WebView 加载失败兜底（Priority: P4）

**Goal**: WebView 主入口加载失败时，显示错误页/错误层（含错误文案 + “退出游戏”按钮），仍可退出（FR-W05）。

**Independent Test**:
- 模拟 `assets/ui/start-menu/index.html` 不可用
- 显示错误层 + 退出按钮
- 点击退出按钮 → 弹退出确认 → 确认后退出

### Implementation

- [ ] T021 [US4] 在宿主侧实现“入口加载失败检测”并触发 `host.webViewLoadFailed`（宿主侧：`client/src/main/java/staraxis/ClientGame.java`；窗口入口：`lwjgl3/src/main/java/staraxis/lwjgl3/Lwjgl3Launcher.java`）
- [ ] T022 [US4] UI 侧实现错误层展示（message + “退出游戏”按钮），并复用退出流程（资源路径：`assets/ui/start-menu/`）
- [ ] T023 [US4] 验证错误层下仍能完成退出（按 `quickstart.md` 的第 4 步）

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T024 统一校对 spec/plan/contracts/quickstart 中关于入口路径的表述一致性（`assets/ui/start-menu/index.html`）
- [ ] T025 走通 `specs/003-webview-ui/quickstart.md` 全流程手工验收并记录结果（可附到 `specs/003-webview-ui/research.md` 或单独记录文件）

---

## Dependencies & Execution Order

- **Phase 1（Setup）** → **Phase 2（Foundational）** → **US1（开始界面可见）** → **US2（开发中提示）** → **US3（退出）** → **US4（加载失败兜底）** → **Polish**

说明：
- US2/US3/US4 依赖 Phase 2 的桥接与加载失败检测能力。
- 本期不实现键盘操作（FR-W06），因此不包含任何键盘事件任务。

---

## Parallel Opportunities

- [P] 任务：T003 可与 T002 并行（都是扫描/确认类任务，且不改同一文件）。
- [P] 任务：T004/T005/T006 可并行（构建依赖 + 本地目录准备 + 原生包放置互不冲突）。
- UI 资源实现（`assets/ui/start-menu/**`）与宿主侧 WebView 接入（客户端代码）在明确桥接协议后可并行推进，但实际需先完成 Phase 2 的 JCEF 初始化与桥接通道。
