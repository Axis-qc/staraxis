# Tasks: Game Framework Architecture (C/S Separation)

**Input**: Design documents from `/specs/001-game-framework-cs/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

## Path Conventions

- **StarAxis Core**: `core/src/main/java/com/staraxis/game/`
- **StarAxis LWJGL3**: `lwjgl3/src/main/java/com/staraxis/game/`
- **Tests**: `core/src/test/java/com/staraxis/game/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Initialize LibGDX multi-module project structure (core, shared, lwjgl3) in root `build.gradle` and `settings.gradle`
- [X] T002 Configure `core` module to be a pure Java module with zero graphics dependencies in `core/build.gradle`
- [X] T003 [P] Add Kryo 5.x and JUnit 5 dependencies to `core` and `shared` modules in their respective `build.gradle` files
- [X] T004 Setup `checkStyle` or custom Gradle task to enforce zero graphics library (com.badlogic.gdx.graphics) in `core` module
- [X] T005 [P] Implement base file headers and Chinese naming convention documentation in `README.md` or a dedicated `DEVELOPMENT.md`

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T006 [P] Create shared models `Vector2` (逻辑坐标) in `core/src/main/java/com/staraxis/game/shared/model/Vector2.java`
- [X] T007 [P] Create shared `Command` (指令) and `CommandType` in `core/src/main/java/com/staraxis/game/shared/model/Command.java`
- [X] T008 Create `GameState` (游戏状态) and `EntityState` (实体状态) in `core/src/main/java/com/staraxis/game/shared/model/GameState.java`
- [X] T009 [P] Implement `NetworkRegistry` (网络注册) for Kryo following the exact order in `contracts/network-protocol.md`
- [X] T010 Create `Message` wrappers (ConnectionRequest/Response, GameStateUpdate) in `core/src/main/java/com/staraxis/game/shared/network/`
- [X] T011 [P] Setup base `Observer` (观察者) interfaces for C/S communication in `core/src/main/java/com/staraxis/game/shared/event/`
- [X] T012 Implement `In-Memory Queue` (内存队列) for local C/S communication testing in `core/src/main/java/com/staraxis/game/shared/network/MemoryQueue.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 独立的服务端逻辑模拟 (Priority: P1) 🎯 MVP

**Goal**: Core game logic running in a headless server environment.

**Independent Test**: Run `core:test` to verify entity position updates without any UI.

- [X] T013 [P] [US1] Create `GameServer` (服务端核心) shell in `core/src/main/java/com/staraxis/game/core/engine/GameServer.java`
- [X] T014 [US1] Implement 20Hz `SimulationLoop` (模拟循环) with fixed timestep in `GameServer.java`
- [X] T015 [US1] Implement `EntityProcessor` (实体处理器) for basic position updates (移动计算) in `core/src/main/java/com/staraxis/game/core/processor/`
- [X] T016 [US1] Implement `CommandValidator` (指令校验器) for validating MOVE commands in `core/src/main/java/com/staraxis/game/core/logic/`
- [X] T017 [US1] Add logic to apply validated commands to `GameState` in the next tick
- [X] T018 [US1] Create unit test `GameServerTest.java` verifying 20Hz tick progression and position updates
- [X] T019 [US1] Add 10ms processing budget check in `SimulationLoop` and log warnings if exceeded
- [X] T020 [US1] Verify `core` module remains Headless (no GDX graphics imports) via SC-001 check

**Checkpoint**: User Story 1 functional - Headless simulation works.

---

## Phase 4: User Story 4 - 连接管理与初始同步 (Priority: P2)

**Goal**: Full connection lifecycle and initial state snapshot synchronization.

**Independent Test**: Mock client connection and verify snapshot receipt.

- [X] T021 [P] [US4] Implement `ConnectionManager` (连接管理器) in `GameServer.java`
- [X] T022 [US4] Implement Handshake logic (握手协议) for `ConnectionRequest/Response`
- [X] T023 [US4] Implement `SnapshotGenerator` (快照生成器) for serializing current `GameState` to new clients
- [X] T024 [US4] Implement `HeartbeatSystem` (心跳系统) with 5s timeout detection on server
- [X] T025 [US4] Implement `EntityRetentionSystem` (实体保留系统) to keep player state for 30s after disconnect
- [X] T026 [US4] Create test case `ConnectionSyncTest.java` for verifying handshake and snapshot receipt
- [X] T027 [US4] Add support for `GameStateUpdate` (增量/全量更新) message pushing in `GameServer.java`
- [X] T028 [US4] Implement sequence numbering for messages to ensure ordered processing

---

## Phase 5: User Story 2 - 响应式的客户端渲染与交互 (Priority: P2)

**Goal**: Visual representation of server state with smooth interpolation.

**Independent Test**: Run `lwjgl3:run` and see entity move smoothly despite 20Hz server updates.

- [X] T029 [P] [US2] Create `GameClient` (客户端核心) in `lwjgl3/src/main/java/com/staraxis/game/client/GameClient.java`
- [X] T030 [US2] Implement `StateBuffer` (状态缓冲区) to store the last 2 `GameState` updates
- [X] T031 [US2] Implement `LinearInterpolator` (线性插值器) for smoothing entity movement in `lwjgl3/src/main/java/com/staraxis/game/client/render/`
- [X] T032 [US2] Implement `EntityRendererProxy` (实体渲染代理) that reads from `StateBuffer`
- [X] T033 [US2] Implement `InputForwarder` (输入转发器) to send `Command` messages to server
- [X] T034 [US2] Add visual indicator for "Reconnecting/Offline" state in UI when heartbeat fails
- [X] T035 [US2] Implement `ReconciliationLogic` (预测校解) to force snap-back if drift > 0.1 units
- [X] T036 [US2] Verify SC-003: Visual jitter is unnoticeable under simulated 150ms RTT

---

## Phase 6: User Story 3 - 游戏模拟驱动的同步机制 (Priority: P3)

**Goal**: Ensure logic consistency regardless of frame rate.
- [X] T037 [P] [US3] Add `SimulationTime` (模拟时间) tracking to `GameState`
- [X] T038 [US3] Implement client-side logic that advances simulation only based on received Tick numbers
- [X] T039 [US3] Implement `TickDriftMonitor` (帧漂移监控) on server with ±2ms tolerance
- [X] T040 [US3] Add debug overlay in `lwjgl3` showing current Server Tick vs Client Interpolation Time
- [X] T041 [US3] Create a stress test scenario with 100 entities and verify SC-002 coverage
- [X] T042 [US3] Final validation of SC-004: Independent Headless run vs LWJGL3 run

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T043 [P] Add comprehensive log4j/slf4j logging with [TICK-X] prefix for all server events
- [X] T044 Finalize `quickstart.md` documentation with screenshots of running framework
- [X] T045 Performance profiling of Kryo serialization for large `GameState` packages

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on T001, T002, T003 - BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2. MVP priority.
- **US4 (Phase 4)**: Depends on US1 completion. Required for US2.
- **US2 (Phase 5)**: Depends on US4.
- **US3 (Phase 6)**: Depends on US2.

### Parallel Opportunities

- T003, T005 can run in parallel with T001
- T006, T007, T009, T011 can run in parallel (Foundational)
- T013 can start while Foundational is in final stages

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2.
2. Implement Phase 3 (US1).
3. **VALIDATE**: Run `core:test` to ensure headless simulation works perfectly.

### Incremental Delivery

1. Foundation -> Simulation (US1) -> Connection (US4) -> Rendering (US2) -> Polish.
2. Each phase delivers a testable increment.
