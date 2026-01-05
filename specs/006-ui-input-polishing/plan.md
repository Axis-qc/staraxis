# Implementation Plan: UI & Input Polishing (UI与输入优化)

**Branch**: `006-ui-input-polishing` | **Date**: 2026-01-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-ui-input-polishing/spec.md`

## Summary

本特性旨在提升游戏的视觉表现与交互体验。技术方案包括：采用程序化绘制（NinePatch 与 ShapeRenderer）增强 libGDX Scene2D 控件的“科技感”视觉效果；全面覆盖中英文本地化文本；并重构输入控制器，实现带惯性的 WASD 镜头平移以及以鼠标为中心的平滑缩放。

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: libGDX 1.14.0 (Scene2D UI, gdx-freetype)  
**Storage**: N/A (本地化文本存储于 .properties 资源文件)  
**Testing**: JUnit 5 (输入逻辑单元测试), 手动 UI 走查验证  
**Target Platform**: Desktop (lwjgl3)
**Project Type**: Multi-project Gradle (core/shared/lwjgl3)  
**Performance Goals**: UI 渲染保持 60 FPS，输入响应延迟 < 16ms  
**Constraints**: 严格遵循 UI 层独立性，禁止在 Core 模块处理具体按键逻辑  
**Scale/Scope**: 覆盖配置界面与世界界面的所有可见文本，美化滑块、输入框、下拉框控件，重构镜头控制交互。

## Constitution Check

- **UI 层独立性 (Independent UI Layer)**: 是。所有视觉美化与输入控制均在 `lwjgl3` 模块的 UI 层实现，与核心逻辑解耦。
- **模块化与可维护性 (Modularization & Maintainability)**: 是。采用独立的 `InputController` 处理输入映射，皮肤配置集中管理。
- **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: 是。镜头位置仅作为客户端表现状态，不影响服务端逻辑演算。
- **规范化命名与注释 (Standardized Naming & Documentation)**: 是。遵循项目职责说明命名规范。
- **扩展性与 Mod 支持 (Extensibility & Mod Support)**: 是。本地化系统天然支持通过外部资源包扩展语言。
- **游戏模拟驱动 (Simulation-Driven Logic)**: 是。镜头平滑平移基于 delta time 演算，不依赖固定帧率。

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

## Project Structure

### Documentation (this feature)

```text
specs/006-ui-input-polishing/
├── plan.md              # This file (/speckit.plan)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
core/src/main/resources/
└── i18n/                       # 本地化资源文件

lwjgl3/src/main/java/com/staraxis/game/client/
├── ui/
│   ├── components/             # 美化后的通用 UI 组件
│   └── view/                   # 增强后的 CameraController
└── GameClient.java             # 输入焦点管理逻辑
```

**Structure Decision**: 美化后的 UI 逻辑保留在 `lwjgl3` 客户端模块。本地化文本位于 `core` 资源目录以便共享。输入控制逻辑封装在 `CameraController` 并通过 `InputMultiplexer` 与 UI 舞台协调。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |

