# Tasks: UI & Input Polishing (UI与输入优化)

**Input**: Design documents from `/specs/006-ui-input-polishing/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Organization**: Tasks are grouped by logical components to enable progressive implementation and verification.

## Format

- `- [ ] T### [P?] [US?] Description with file path`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[US]**: Which user story this task belongs to (US1-US2)

## Path Conventions

- **Logic (core)**: `core/src/main/java/com/staraxis/game/core/`
- **Resources**: `core/src/main/resources/`
- **Client (lwjgl3)**: `lwjgl3/src/main/java/com/staraxis/game/client/`
- **Tests**: `shared/src/test/java/com/staraxis/game/shared/`

---

## Phase 1: Localization & Resources (Shared)

**Purpose**: Ensure all visible strings are externalized and supported in both languages.

- [x] T001 [US1] Define new localization keys for world generation states in `core/src/main/resources/i18n/messages.properties` (e.g., `config_generating`, `status_error`)
- [x] T002 [US1] [P] Translate new localization keys in `core/src/main/resources/i18n/messages_en.properties`
- [x] T003 [US1] Audit `NewGameConfigScreen.java` and `WorldScreen.java` for hard-coded strings and replace with `i18n.get()`

---

## Phase 2: UI Visual Polishing (Skinning)

**Purpose**: Implement the futuristic HUD style using procedural NinePatches.

- [x] T004 [US1] Implement `ProceduralNinePatch` utility in `lwjgl3/src/main/java/com/staraxis/game/client/ui/components/SkinUtils.java` (create NinePatch from 1x1 white texture with border)
- [x] T005 [US1] Update `Main.createDefaultSkin()` to use `ProceduralNinePatch` for `Slider` background and knob
- [x] T006 [US1] [P] Update `Main.createDefaultSkin()` to use `ProceduralNinePatch` for `TextField` background and focus border
- [x] T007 [US1] [P] Update `Main.createDefaultSkin()` to use `ProceduralNinePatch` for `SelectBox` and its `List` background
- [x] T008 [US1] Implement Neon Glow effect (using color tinting) for focused states in `Main.java`

---

## Phase 3: Input Controller Refactoring (Inertia & Zoom)

**Purpose**: Implement smooth camera movement and mouse-centered zoom.

- [x] T009 [US2] Add physics-based state (velocity, acceleration, friction) to `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/CameraController.java`
- [x] T010 [US2] Implement WASD input handling with acceleration in `CameraController.update()`
- [x] T011 [US2] Implement velocity decay (friction) in `CameraController.update()` to achieve inertia
- [x] T012 [US2] Refactor `CameraController.scrolled()` to implement mouse-pointer centered zoom logic
- [x] T013 [US2] Add zoom smoothing (interpolating towards target zoom) in `CameraController.java`

---

## Phase 4: Focus Management & Integration

**Purpose**: Prevent input conflicts between UI and camera.

- [x] T014 [US2] Implement `InputInterceptor` logic in `CameraController.java` to check if an `Actor` has keyboard focus
- [x] T015 [US2] Wire `Stage.setKeyboardFocus()` events to toggle `isIntercepted` state in `CameraController`
- [x] T016 [US2] Verify `InputMultiplexer` order in `WorldScreen.java` (UI stage must have priority over CameraController)

---

## Phase 5: Polish & Validation

**Purpose**: Final verification against spec and constitution.

- [x] T017 [P] Add unit test for `CameraController` velocity logic (simulated delta time) in `lwjgl3/src/test/java/com/staraxis/game/client/ui/view/InputInertiaTest.java`
- [x] T018 Verify SC-001: Run app and switch languages, ensure no missing keys
- [x] T019 Verify SC-002: Run app and move camera, ensure smooth sliding feel
- [x] T020 Verify SC-003: Visually inspect all themed controls for state feedback (hover/focus)

---

## Dependencies & Execution Order

- **Phase 1** is independent and low-risk.
- **Phase 2** affects global UI styles.
- **Phase 3** depends on `CameraController` refactoring.
- **Phase 4** requires both UI components and `CameraController` to be ready.
- **Phase 5** is the final gate.
