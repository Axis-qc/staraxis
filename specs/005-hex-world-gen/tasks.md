# Tasks: Hex World Generation & New Game Config (六边形世界生成与新游戏配置)

**Input**: Design documents from `/specs/005-hex-world-gen/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format

- `- [ ] T### [P?] [US?] Description with file path`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[US]**: Which user story this task belongs to (US1-US4)

## Path Conventions

- **Logic (core)**: `core/src/main/java/com/staraxis/game/core/`
- **Shared models**: `shared/src/main/java/com/staraxis/game/shared/`
- **Client (lwjgl3)**: `lwjgl3/src/main/java/com/staraxis/game/`
- **Tests**: `core/src/test/java/com/staraxis/game/` and `shared/src/test/java/com/staraxis/game/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare package layout and wiring points for worldgen + UI flow.

- [x] T001 Ensure feature docs are tracked: add `specs/005-hex-world-gen/**` to git index and verify build still passes (repo root)
- [x] T002 Create world feature package stubs (directories): `core/src/main/java/com/staraxis/game/core/world/` and `shared/src/main/java/com/staraxis/game/shared/world/`
- [x] T003 [P] Add placeholder screens package (directories): `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/` and `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/`
- [x] T004 [P] Add placeholder test package (directories): `shared/src/test/java/com/staraxis/game/shared/world/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data model + deterministic config parsing used by all stories.

- [x] T005 [P] Implement `HexCoord` (立方体坐标) in `shared/src/main/java/com/staraxis/game/shared/world/HexCoord.java`
- [x] T006 [P] Add data-driven tile type definitions in `shared/src/main/resources/worldgen/tile-types.properties`
- [x] T007 [P] Implement `HexTile` in `shared/src/main/java/com/staraxis/game/shared/world/HexTile.java` (use `typeId` string instead of hard-coded enums)
- [x] T008 [P] Add data-driven map size presets in `shared/src/main/resources/worldgen/map-presets.properties` (presetId -> radius R)
- [x] T009 [P] Add placeholder tech level presets in `shared/src/main/resources/worldgen/tech-level-presets.properties`
- [x] T010 [P] Implement `WorldGenConfig` in `shared/src/main/java/com/staraxis/game/shared/world/WorldGenConfig.java` (fields from spec: mapSizePresetId/habitableRatio/seedText/seedValue/aiCount/techLevelPresetId)
- [x] T011 [P] Implement deterministic seed conversion helper in `shared/src/main/java/com/staraxis/game/shared/world/SeedUtil.java` (string -> long; empty -> random)
- [x] T012 [P] Implement `WorldMap` container in `shared/src/main/java/com/staraxis/game/shared/world/WorldMap.java` (config snapshot, boundsRadius, tiles map)
- [x] T013 Implement cubic invariants and helpers (neighbors/distance) in `shared/src/main/java/com/staraxis/game/shared/world/HexMath.java`
- [x] T014 [P] Add unit tests for `HexCoord` invariants and `HexMath.distance()` in `shared/src/test/java/com/staraxis/game/shared/world/HexMathTest.java`
- [x] T015 [P] Add unit tests for `SeedUtil` determinism in `shared/src/test/java/com/staraxis/game/shared/world/SeedUtilTest.java`
- [x] T016 [P] Implement preset/type loaders in `shared/src/main/java/com/staraxis/game/shared/world/WorldGenDefinitions.java` (load presets + tile types + tech level presets)

**Checkpoint**: Shared world model + deterministic seed ready.

---

## Phase 3: User Story 1 - 六边形网格渲染 (Priority: P1) 🎯 MVP

**Goal**: Render a visible hex grid; hover highlights the correct cell.

**Independent Test**: Run `./gradlew :lwjgl3:run` and visually confirm hex grid + hover highlight.

- [x] T017 [US1] Create `WorldScreen` skeleton in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java` (Stage integration via UIManager)
- [x] T018 [US1] Implement orthographic camera + viewport init in `WorldScreen.java` (top-down baseline; no zoom yet)
- [x] T019 [US1] Implement `HexGridRenderer` outline drawing in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/HexGridRenderer.java`
- [x] T020 [US1] Implement `HexPicker` (screen -> world -> HexCoord) in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/HexPicker.java`
- [x] T021 [US1] Wire hover highlight (picked coord -> renderer highlight) in `WorldScreen.java`
- [x] T022 [US1] Add debug overlay text for hovered `HexCoord` in `WorldScreen.java`
- [x] T023 [US1] Add temporary bounded radius map stub using a default `mapSizePresetId` in `WorldScreen.java`

---

## Phase 4: User Story 2 - 星域类型生成 (Priority: P1)

**Goal**: Generate bounded world tiles with types (`galaxy`/`deep_space`/`nebula`) deterministically.

**Independent Test**: With the same config+seed, restarting yields identical tile types at the same coordinates.

- [x] T024 [US2] Create `WorldGenerator` interface in `core/src/main/java/com/staraxis/game/core/world/WorldGenerator.java`
- [x] T025 [US2] Implement `DefaultWorldGenerator` in `core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java` (bounded radius R from `WorldGenDefinitions` map presets)
- [x] T026 [US2] Implement tile type assignment strategy in `DefaultWorldGenerator.java` (random distribution; ensure at least 3 types exist for normal sizes)
- [x] T027 [US2] Implement habitable feature assignment in `DefaultWorldGenerator.java` (only for galaxy tiles, per-tile probability = habitableRatio)
- [x] T028 [US2] Ensure determinism: generator seeded only by `WorldGenConfig.seedValue` in `DefaultWorldGenerator.java`
- [x] T029 [US2] Add generator unit test for determinism in `core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java`
- [x] T030 [US2] Integrate generator output into client: call generator and pass `WorldMap` into `WorldScreen` in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java`
- [x] T031 [US2] Render tile types with distinct visual styles based on `typeId` in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/HexGridRenderer.java`

---

## Phase 5: User Story 4 - 新游戏配置 (Priority: P1)

**Goal**: From Main Menu -> New Game Config -> Start -> WorldScreen; config fields follow spec.

**Independent Test**: From main menu, you can configure 地图大小预设/HabitableRatio/Seed；AI/Tech are visible but disabled; Start enters world.

- [x] T032 [US4] Add `NewGameConfigScreen` skeleton in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`
- [x] T033 [US4] Implement map size preset selector (presetId + radius description) in `NewGameConfigScreen.java` (load from `WorldGenDefinitions`)
- [x] T034 [US4] Implement HabitableRatio input (0.0-1.0) with clamp/validation in `NewGameConfigScreen.java`
- [x] T035 [US4] Implement Seed text input (string; empty allowed) in `NewGameConfigScreen.java`
- [x] T036 [US4] Add AI count + Tech level controls as disabled placeholders (grey + "开发中") in `NewGameConfigScreen.java`
- [x] T037 [US4] Build `WorldGenConfig` from UI and freeze values on Start in `NewGameConfigScreen.java`
- [x] T038 [US4] Update main menu route: “新游戏” click opens `NewGameConfigScreen` in `lwjgl3/src/main/java/com/staraxis/game/client/ui/MainMenuScreen.java`
- [x] T039 [US4] Implement Start flow: create/resolve `seedValue` via `SeedUtil` and navigate to `WorldScreen` with generated `WorldMap` in `NewGameConfigScreen.java`
- [x] T040 [US4] Implement Back flow: return to `MainMenuScreen` in `NewGameConfigScreen.java`

---

## Phase 6: User Story 3 - 顶部正交视角控制 (Priority: P2)

**Goal**: Fixed top-down orthographic camera with semantic zoom (LOD).

**Independent Test**: Zooming changes rendering detail (abstract vs detailed) without perspective distortion.

- [x] T041 [US3] Add `CameraController` (pan + zoom) in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/CameraController.java`
- [x] T042 [US3] Define semantic zoom tier rules in `specs/005-hex-world-gen/contracts/zoom-tiers.md` (at least 2-3 tiers; specify what to render per tier)
- [x] T043 [US3] Implement zoom levels and semantic LOD thresholds in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/ZoomLevel.java` (follow `contracts/zoom-tiers.md`)
- [x] T044 [US3] Wire camera controller into input processing in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java`
- [x] T045 [US3] Implement LOD rendering branches in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/HexGridRenderer.java` (follow `contracts/zoom-tiers.md`)
- [x] T046 [US3] Add view culling: render only tiles within viewport bounds in `HexGridRenderer.java`

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Performance, stability, and documentation alignment.

- [x] T047 [P] Add loading state when world generation exceeds frame budget in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java`
- [x] T048 Ensure `UIManager`/screen lifecycle disposes stages properly to avoid leaks in `lwjgl3/src/main/java/com/staraxis/game/client/ui/manager/UIManager.java`
- [x] T049 [P] Add world generation timing measurement for SC-001 in `core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java` (log total ms; include mapSizePresetId + tileCount)
- [x] T050 [P] Add FPS sampling overlay for SC-002 in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java` (display current/avg FPS)
- [x] T051 [P] Add background layer placeholder (starfield/solid background) in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java` to satisfy FR-003 base layer
- [x] T052 [P] Add top overlay placeholder renderer (units/markers layer, empty for now) in `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/WorldOverlayRenderer.java` to satisfy FR-003 top layer
- [x] T053 [P] Ensure render order follows FR-003 (background -> hex grid -> overlay -> UI) in `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java`
- [x] T054 [P] Add performance validation steps (SC-001/SC-002) to `specs/005-hex-world-gen/quickstart.md`
- [x] T055 [P] Verify deterministic generation end-to-end (config -> world -> render) with manual checklist in `specs/005-hex-world-gen/quickstart.md`

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2** are blocking prerequisites.
- **US1** depends on Phase 2 (`HexMath`/picking helpers).
- **US2** depends on Phase 2 (World model) and feeds data to US1 rendering.
- **US4** depends on Phase 2 (WorldGenConfig/SeedUtil) and routes into US1/US2 world screen.
- **US3** depends on US1 baseline WorldScreen.

## Parallel Opportunities

- Phase 2 model classes (T005-T012) can be parallelized across different files.
- Tests (T014-T015) can run in parallel after their corresponding utilities exist.
- UI Screen skeletons (T016, T031) can be started in parallel with generator interface (T023).

## Suggested MVP Scope

- Implement **Phase 2 + US1 (T005-T022)** first to get a visible hex grid + picking/highlight.
