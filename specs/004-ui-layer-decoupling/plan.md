# Implementation Plan: UI Layer Decoupling & Command Restrictions (UI层解耦与指令限制)

**Branch**: `004-ui-layer-decoupling` | **Date**: 2026-01-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-ui-layer-decoupling/spec.md`

## Summary

通过引入 `UIManager` 和 `UIModel` 层级，将 UI 状态从核心逻辑中剥离。实现基于事件总线的数据驱动更新机制。同时，开发一套完善的 Gradle 任务集（Toolchain），将所有频繁的手动终端操作自动化，彻底落实宪章原则。

## Technical Context

**Language/Version**: Java (LibGDX Framework)
**Primary Dependencies**: `libgdx`, `gdx-freetype`, `Gradle`
**Storage**: N/A (UI State only)
**Testing**: JUnit (Mock UI Models), Manual UI Verification
**Target Platform**: Cross-platform (via Gradle)
**Project Type**: Multi-project Gradle
**Performance Goals**: UI response < 16ms, Rendering fully delegated
**Constraints**: Zero `scene2d` dependencies in `core` module
**Scale/Scope**: Entire client UI layer, common dev toolchain

## Constitution Check

- **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: 是。严禁逻辑层直接构建 UI 条件。
- **UI 层独立性 (Independent UI Layer)**: 是。UI 将作为独立层级，通过 UI Model 与逻辑解耦。
- **模块化 (Modularization)**: 是。采用 UIManager 统一调度。
- **命名规范 (Naming)**: 是。遵循中文注释和职责命名。
- **命令限制 (Command Restrictions)**: 是。所有开发命令将封装为 Gradle 任务。`Main.java` 已集成自动环境自检触发（T019）。

## Project Structure

### Documentation (this feature)

```text
specs/004-ui-layer-decoupling/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # UI Model & Event Contracts
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
core/src/main/java/com/staraxis/game/core/
├── logic/               # logic only, no UI awareness
└── api/                 # Data interfaces for UI to consume
lwjgl3/src/main/java/com/staraxis/game/client/ui/
├── model/               # Independent UI Models
├── manager/             # UIManager
├── components/          # Reusable UI widgets
└── view/                # Screens (delegated rendering)
buildSrc/                # Custom Gradle tasks for toolchain
```

**Structure Decision**: 采用 `buildSrc` 模块或根目录 `build.gradle` 扩展来管理自动化工具链。UI 层级在 `lwjgl3` 模块内进一步细分。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
