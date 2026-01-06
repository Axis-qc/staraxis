# Tasks: 008 Server/Client Separation & Communication

**Input**: Design documents from `specs/008-server-client-split/`

**Prerequisites**:
- `specs/008-server-client-split/spec.md`
- `specs/008-server-client-split/plan.md`
- `specs/008-server-client-split/research.md`
- `specs/008-server-client-split/data-model.md`
- `specs/008-server-client-split/contracts/worldgen-api.yaml`
- `specs/008-server-client-split/quickstart.md`

**Tests**: 本特性未要求 TDD；以下任务以“实现+手工验证”为主，仅在必要处包含最小自动化门禁。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立 server 模块与基础依赖，确保后续任务有落点。

- [x] T001 在 `settings.gradle` 增加 `server` 子模块（路径：`settings.gradle`）
- [x] T002 新增 `server/build.gradle` 并配置 `application` 与 `run` 入口（路径：`server/build.gradle`）
- [x] T003 [P] 在 `gradle.properties` 增加 JSON 库版本常量（例如 `jacksonVersion`）（路径：`gradle.properties`）
- [x] T004 [P] 在 `server/build.gradle` 引入 JSON 库依赖（使用 `jacksonVersion`）并依赖 `project(':core')`、`project(':shared')`（路径：`server/build.gradle`）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: DTO、契约常量、转换器与用例边界，所有 user story 都依赖这一层。

**Checkpoint**: 完成后，US1/US2/US3 才能开始。

- [x] T005 定义协议版本常量 `worldgen_v1`（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/SchemaVersions.java`）
- [x] T006 [P] 定义请求 DTO `StartNewGameRequest`（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/StartNewGameRequest.java`）
- [x] T007 [P] 定义有效参数回填 DTO `StartNewGameEffectiveConfig`（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/StartNewGameEffectiveConfig.java`）
- [x] T008 [P] 定义错误 DTO `ErrorEnvelope`（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/ErrorEnvelope.java`）
- [x] T009 [P] 定义响应 DTO `StartNewGameResponse`（包含 `schemaVersion/world/effectiveConfig/error`）（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/StartNewGameResponse.java`）
- [x] T010 [P] 定义快照 DTO：`WorldSnapshot/HexTileSnapshot/HexCoordSnapshot/StarSystemSnapshot/StarSnapshot/PlanetSnapshot/WorldGenStatsSnapshot`（路径：`shared/src/main/java/com/staraxis/game/shared/net/worldgen/snapshot/*.java`）
- [x] T011 在 `shared` 增加“从 WorldSnapshot 还原 WorldMap”的转换器（路径：`shared/src/main/java/com/staraxis/game/shared/world/WorldSnapshotConverter.java`）
- [x] T012 在 `core` 增加 `StartNewGameUseCase`：输入请求 DTO -> 组装 `WorldGenConfig` -> 调用 `WorldGenerator` -> 输出响应 DTO（路径：`core/src/main/java/com/staraxis/game/core/worldgen/StartNewGameUseCase.java`）
- [x] T013 在 `core` 增加 `WorldSnapshotMapper`：`WorldMap` -> `WorldSnapshot`（路径：`core/src/main/java/com/staraxis/game/core/worldgen/WorldSnapshotMapper.java`）
- [x] T014 在 `core` 增加参数规范化逻辑：比例 clamp、preset 校验、seedText->seedValue（服务端权威）并填充 `effectiveConfig`（路径：`core/src/main/java/com/staraxis/game/core/worldgen/StartNewGameUseCase.java`）

---

## Phase 3: User Story 1 - Server 权威世界生成与通讯最小闭环 (Priority: P1) MVP

**Goal**: 启动 server 后，客户端可通过 HTTP+JSON 调用 `/api/worldgen/start-new-game` 获取 `StartNewGameResponse`（schemaVersion=worldgen_v1，含 WorldSnapshot 与 effectiveConfig）。

**Independent Test**: 启动 server 后，使用任意 HTTP 客户端发送合法 JSON 请求，能得到 200 响应，且响应字段满足 `contracts/worldgen-api.yaml`。

- [x] T015 [US1] 创建 server 启动入口 `ServerMain`（读取 host/port 配置；默认端口 8080）（路径：`server/src/main/java/com/staraxis/game/server/ServerMain.java`）
- [x] T016 [US1] 实现 HTTP 路由注册（至少注册 `POST /api/worldgen/start-new-game`）（路径：`server/src/main/java/com/staraxis/game/server/http/HttpRoutes.java`）
- [x] T017 [US1] 实现 JSON 编解码适配层（将请求 body -> StartNewGameRequest；将 StartNewGameResponse -> JSON）（路径：`server/src/main/java/com/staraxis/game/server/http/JsonCodec.java`）
- [x] T018 [US1] 实现请求处理器：调用 `StartNewGameUseCase` 并返回响应（路径：`server/src/main/java/com/staraxis/game/server/http/StartNewGameHandler.java`）
- [x] T019 [US1] 按契约实现状态码策略：
  - 200：成功（含 clamp 后 effectiveConfig）
  - 400：不可修正错误（例如 mapSizePresetId 不存在 / JSON 结构错误）
  - 500：服务端异常（路径：`server/src/main/java/com/staraxis/game/server/http/StartNewGameHandler.java`）
- [x] T020 [US1] 为局域网可访问实现 bindAddress 配置（默认 0.0.0.0；并在日志提示“未启用认证，仅开发/测试”）（路径：`server/src/main/java/com/staraxis/game/server/ServerMain.java`）
- [x] T021 [US1] 在服务端记录最小可观测信息：生成耗时、响应体大小、seedValue（路径：`server/src/main/java/com/staraxis/game/server/http/StartNewGameHandler.java`）

---

## Phase 4: User Story 2 - Client 只渲染与输入转发 (Priority: P2)

**Goal**: 客户端 UI 不再 `new DefaultWorldGenerator()`，改为调用 server API；解析 WorldSnapshot 并进入 WorldScreen 渲染。

**Independent Test**: 启动 server + 客户端，通过“新游戏”界面生成世界；WorldScreen 正常渲染网格与星系标记；debug overlay 显示来自快照的数据。

- [x] T022 [US2] 新增客户端 API 调用封装 `WorldGenApiClient`（配置 serverBaseUrl；POST 发送 JSON；解析 StartNewGameResponse）（路径：`lwjgl3/src/main/java/com/staraxis/game/client/net/WorldGenApiClient.java`）
- [x] T023 [US2] 新增客户端响应处理：schemaVersion 校验（必须为 worldgen_v1），不匹配则走 ErrorEnvelope + messageKey 提示（路径：`lwjgl3/src/main/java/com/staraxis/game/client/net/WorldGenApiClient.java`）
- [x] T024 [US2] 在 `NewGameConfigScreen.startGame()` 替换本地生成逻辑为调用 `WorldGenApiClient`（路径：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`）
- [x] T025 [US2] 在 `NewGameConfigScreen` 中使用 `effectiveConfig` 回填最终采用参数的显示/日志（至少用于 debug）（路径：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`）
- [x] T026 [US2] 客户端将 WorldSnapshot 转换为 WorldMap（使用 `WorldSnapshotConverter`）并进入 WorldScreen（路径：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`）
- [x] T027 [US2] 增加 messageKey 的本地化资源项（覆盖 spec FR-006 中定义的最小集合，例如 server_unreachable/schema_mismatch/invalid_map_preset/invalid_json/internal_error）（路径：`assets/i18n/messages.properties`、`assets/i18n/messages_en.properties`）
- [x] T028 [US2] 明确客户端连接失败/服务端不可用时的 UX：按钮恢复、loadingLabel 提示（messageKey）、允许返回主菜单（路径：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`）

---

## Phase 5: User Story 3 - Core 可服务端运行（无 Gdx 依赖） (Priority: P3)

**Goal**: core 可在无图形运行时环境运行；去除 core 中的 UI/资源依赖，保证 server 可以稳定调用核心逻辑。

**Independent Test**: 仅启动 server（不启动 LibGDX 客户端），仍可完成 worldgen 请求并返回合法快照。

- [x] T029 [US3] 移除 `core` 中对 `Gdx` 的引用：替换 `DefaultWorldGenerator` 的 `Gdx.app.log` 为纯 Java 日志/可注入日志接口（路径：`core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java`）
- [x] T030 [US3] 将 `LocalizationService` 从 `core` 模块迁移到客户端模块（`lwjgl3`），并修正所有引用点（路径：`core/src/main/java/com/staraxis/game/core/i18n/LocalizationService.java`；以及 `lwjgl3/src/main/java/**` 调用点）
- [x] T031 [US3] 清理 `core/build.gradle` 中与客户端资源相关的依赖（例如 gdx-freetype 等），确保 core 只保留 headless 所需依赖（路径：`core/build.gradle`）
- [x] T032 [US3] 确保 `core:check` 通过（包括 `checkNoGraphicsDependencies`）并记录在 quickstart 验证步骤中（路径：`specs/008-server-client-split/quickstart.md`）

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文档一致性、门禁与可执行验证步骤。

- [x] T033 [P] 校验 `contracts/worldgen-api.yaml` 与 `data-model.md` 的字段一致性（schemaVersion/effectiveConfig/world/error）（路径：`specs/008-server-client-split/contracts/worldgen-api.yaml`、`specs/008-server-client-split/data-model.md`）
- [x] T034 更新 `quickstart.md`：补充“如何用 curl/postman 调用 API”的示例（路径：`specs/008-server-client-split/quickstart.md`）
- [x] T035 记录并固化性能测量方法（SC-005~SC-007）：服务端计时点、客户端计时点、响应体字节数统计方式（路径：`specs/008-server-client-split/quickstart.md`）
- [x] T036 运行 quickstart 的手工验证闭环并在 `tasks.md` 勾选前记录结果（路径：`specs/008-server-client-split/quickstart.md`）
- [x] T037 验证版本控制纪律：spec/plan/research/data-model/contracts/quickstart/tasks 全部纳入 git；实现+测试+文档对齐后再 push；质量门禁通过后再合并（路径：仓库根目录）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 可立即开始
- **Foundational (Phase 2)**: 依赖 Phase 1，完成后才能进入任何 US
- **US1 (Phase 3)**: 依赖 Phase 2
- **US2 (Phase 4)**: 依赖 Phase 3（需要 server 可用）
- **US3 (Phase 5)**: 可与 US2 并行推进，但会影响 server 运行稳定性，建议在 US2 前尽早完成
- **Polish (Phase 6)**: 依赖目标 US 完成

### Parallel Opportunities

- Phase 1 中 `T003` 可与 `T001/T002` 并行
- Phase 2 中 DTO 定义任务 `T006`~`T010` 可并行
- US1 中 `T016`~`T018` 可并行推进（不同文件）
- US2 中 `T022` 与 `T027` 可并行推进（网络层 vs i18n 资源）

## Implementation Strategy

### MVP First (US1)

1. 完成 Phase 1/2
2. 完成 US1（Phase 3）并用 curl/postman 验证契约与字段
3. 停止并验证：schemaVersion 固定、effectiveConfig 回填、WorldSnapshot 可解析

### Incremental Delivery

1. US1 -> 先打通 server 权威生成闭环
2. US2 -> 客户端改为调用 server 并渲染快照
3. US3 -> 完成 core headless 化与依赖清理，确保 server 可独立运行
