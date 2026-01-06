# Tasks: Stellar & Planet Generation (恒星与行星生成)

**Input**: Design documents from `/specs/007-stellar-planet-gen/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format

- `- [ ] T### [P?] [US?] Description with file path`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[US]**: Which user story this task belongs to (US1-US4)
- **新增 Java 文件约束**: 所有新增的 `.java` 文件必须包含项目要求的标准文件头注释块，且文件内注释使用中文。
- **命名括号说明约束**: 所有变量/方法/字段命名后需紧跟括号说明其具体职责（遵循宪章）。

## Path Conventions

- **Logic (core)**: `core/src/main/java/com/staraxis/game/core/`
- **Shared models**: `shared/src/main/java/com/staraxis/game/shared/`
- **Client (lwjgl3)**: `lwjgl3/src/main/java/com/staraxis/game/client/`
- **Resources (shared)**: `shared/src/main/resources/worldgen/`
- **Tests**: `core/src/test/java/com/staraxis/game/` and `shared/src/test/java/com/staraxis/game/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare package layout and resource stubs for stellar/planet generation.

- [X] T001 Ensure feature docs are tracked: add `specs/007-stellar-planet-gen/**` to git index (repo root)
- [X] T002 Create stellar model package (directories): `shared/src/main/java/com/staraxis/game/shared/world/stellar/`
- [X] T003 [P] Create stellar generator package (directories): `core/src/main/java/com/staraxis/game/core/world/stellar/`
- [X] T004 [P] Create client renderer package (directories): `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/stellar/`
- [X] T005 [P] Create resource stubs (directories): `shared/src/main/resources/worldgen/stellar/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend config + shared data model so all stories can build on deterministic, data-driven entities.

- [X] T006 Update config model: add `starDensity`, `planetComplexity`, `nebulaRatio` to `shared/src/main/java/com/staraxis/game/shared/world/WorldGenConfig.java` (add getters/setters with clamp [0,1], update `toString()`)
- [X] T007 Update seed semantics doc sync: align `SeedUtil.resolveSeed` behavior description with spec (deterministic mapping for same string) in `shared/src/main/java/com/staraxis/game/shared/world/SeedUtil.java`
- [X] T008 [P] Add stellar entities: `StarSystem`, `Star`, `Planet` in `shared/src/main/java/com/staraxis/game/shared/world/stellar/` (string IDs, 1..3 stars validation)
- [X] T009 [P] Add world generation stats entity: `WorldGenStats` in `shared/src/main/java/com/staraxis/game/shared/world/stellar/WorldGenStats.java` (tileCount/sectorCounts/galaxyTileCount/starCount/planetCount)
- [X] T010 Extend tile model to hold optional star system: add nullable `StarSystem` field + accessors in `shared/src/main/java/com/staraxis/game/shared/world/HexTile.java`
- [X] T011 Extend world map to hold optional stats: add nullable `WorldGenStats` field + getter/setter in `shared/src/main/java/com/staraxis/game/shared/world/WorldMap.java`
- [X] T012 [P] Add data-driven type defs: create `shared/src/main/resources/worldgen/stellar/star-types.properties` and `shared/src/main/resources/worldgen/stellar/planet-types.properties` (string id -> display name; no hard enums)
- [X] T013 Extend definitions loader to load stellar type defs: add `getStarTypes()` / `getPlanetTypes()` to `shared/src/main/java/com/staraxis/game/shared/world/WorldGenDefinitions.java` (load from `/worldgen/stellar/*.properties` with sensible fallbacks)
- [X] T014 [P] Add i18n keys for new UI fields (CN + EN): update `assets/i18n/messages.properties` and `assets/i18n/messages_en.properties` (e.g., `config_star_density`, `config_planet_complexity`, `config_nebula_ratio`)

---

## Phase 3: User Story 1 - 新游戏配置宇宙参数 (Priority: P1)

**Goal**: In New Game config, user can set map size + seed + star density + planet complexity + nebula ratio and start game.

**Independent Test**: Run `./gradlew :lwjgl3:run`, open “新游戏设置”, confirm fields exist and start enters `WorldScreen`.

- [X] T015 [US1] Add `StarDensity` slider + label to `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/NewGameConfigScreen.java` (0.0-1.0, localized label)
- [X] T016 [US1] Add `PlanetComplexity` slider + label to `NewGameConfigScreen.java` (0.0-1.0, localized label)
- [X] T017 [US1] Add `NebulaRatio` slider + label to `NewGameConfigScreen.java` (0.0-1.0, localized label)
- [X] T018 [US1] Build extended `WorldGenConfig` from UI in `NewGameConfigScreen.startGame()` (set new fields; keep any legacy fields at fixed default if still required by code paths)
- [X] T019 [US1] Update loading/error text to be localized for generation failures in `NewGameConfigScreen.java` (use i18n keys instead of raw "Error: ...")

---

## Phase 4: User Story 2 - 六边形区块“星域-恒星-行星”内容生成 (Priority: P1)

**Goal**: Generator produces sector type (`galaxy`/`deep_space`/`nebula`) using config ratios, and creates star systems (1..3 stars) with planets assigned to stars.

**Independent Test**: Same config+seed generates identical sector counts, star/planet totals, and identical sampled star system structure at the same coordinates.

- [X] T020 [US2] Add stellar generation helper: create `core/src/main/java/com/staraxis/game/core/world/stellar/StellarGenerator.java` (methods: generateStarSystem, generateStars, generatePlanets)
- [X] T021 [US2] Update sector type selection to use config ratios in `core/src/main/java/com/staraxis/game/core/world/DefaultWorldGenerator.java` (follow `spec.md` “SectorType 分配口径（方案 A）”; remove any hard-coded thresholds; clamp & normalize)
- [X] T022 [US2] Generate `StarSystem` only for `galaxy` tiles in `DefaultWorldGenerator.java` and attach to `HexTile` via the new field (enforce 1..3 stars)
- [X] T023 [US2] Implement planets-per-star rule in `StellarGenerator.java` driven by `planetComplexity` (produce deterministic count per star; allow empty)
- [X] T024 [US2] Compute `WorldGenStats` after generation and store in `WorldMap` (`worldMap.setStats(...)`) in `DefaultWorldGenerator.java`
- [X] T025 [US2] Update generator log line to include new key stats (sectorCounts, starCount, planetCount) in `DefaultWorldGenerator.java`
- [X] T026 [US2] Add unit tests for deterministic stellar generation in `core/src/test/java/com/staraxis/game/core/world/DefaultWorldGeneratorTest.java` (same seed -> same stats; stars count 1..3; planets assigned to stars)

---

## Phase 5: User Story 3 - 3D 渲染但 2D 俯视可读 (Priority: P2)

**Goal**: Render stellar content without breaking 2D readability: hex borders + highlight remain clear.

**Independent Test**: In `WorldScreen`, hex borders/highlight always visible; star/planet markers are visible but do not obstruct hover feedback.

- [X] T027 [US3] Add a minimal stellar overlay renderer (2D marker MVP): create `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/stellar/StellarMarkerRenderer.java` (draw star/planet markers at tile center using ShapeRenderer)
- [X] T028 [US3] Wire stellar marker rendering into `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java` render order (after grid, before UI; respect zoom)
- [X] T029 [US3] Ensure marker visibility rules: when zoom is too small, render simplified markers only (no per-planet detail) in `StellarMarkerRenderer.java`

---

## Phase 6: User Story 4 - 生成结果可复现与可验证 (Priority: P2)

**Goal**: Determinism and validation signals are clearly defined and observable (stats + debug view).

**Independent Test**: Using same config+seed, restarting yields identical `WorldGenStats` shown in logs and/or debug UI.

- [X] T030 [US4] Expose stats in UI debug overlay: update `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/WorldScreen.java` to show `worldMap.getStats()` (sectorCounts/starCount/planetCount)
- [X] T031 [US4] Add per-tile debug info on hover: update `WorldScreen.java` to show hovered tile `typeId` and, if galaxy, stars/planets count
- [X] T032 [US4] Add unit tests for `WorldGenConfig` clamping of new fields in `shared/src/test/java/com/staraxis/game/shared/world/WorldGenConfigTest.java`
- [X] T033 [US4] Update `specs/007-stellar-planet-gen/quickstart.md` to include explicit determinism verification steps for new parameters

---

## Phase 7: Polish & Cross-Cutting Concerns

 **Purpose**: Documentation alignment, out-of-scope clarity, and removing hidden ambiguity.
 
 - [X] T034 Verify Out-of-scope section is present and consistent with latest decisions in `specs/007-stellar-planet-gen/spec.md` (no changes if already complete)
 - [X] T035 Align contracts with actual request/response: update `specs/007-stellar-planet-gen/contracts/worldgen-stellar-contracts.md` if field names diverge from code (`seedText` vs `seedValue`, etc.)

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2** are blocking prerequisites.
- **US1** depends on Phase 2 (WorldGenConfig new fields + i18n keys).
- **US2** depends on Phase 2 (stellar entities + tile/worldmap extensions).
- **US3** depends on US2 producing star/planet data, and on current `WorldScreen` render pipeline.
- **US4** depends on US2 stats generation.

## Parallel Opportunities

- Phase 2 entity additions (T008-T013) can be parallelized across different files.
- i18n updates (T014) can be done in parallel with model changes.
- US3 renderer (T027) can start in parallel once `HexTile` carries star system data (T010).

## Suggested MVP Scope

- Implement **Phase 2 + US1 + US2 (T006-T026)** first to get: new config → deterministic sector distribution → multi-star systems + planet assignment + stats.
