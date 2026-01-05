# Implementation Plan: Localization & UI Beautification (本地化与界面美化)

**Branch**: `003-localization-ui-settings` | **Date**: 2026-01-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-localization-ui-settings/spec.md`

## Summary

实现基于 `I18NBundle` 的多语言系统（中/英），集成 `gdx-freetype` 加载阿里巴巴普惠体，并美化主界面（动态星空背景、按钮动画）。采用 LibGDX `Preferences` 持久化语言设置，并通过观察者模式或事件总线实现界面的实时语言切换。

## Technical Context

**Language/Version**: Java (LibGDX Framework)
**Primary Dependencies**: `libgdx`, `gdx-freetype`
**Storage**: LibGDX `Preferences` ("staraxis-settings")
**Testing**: JUnit (for LocalizationService logic), Manual UI Testing
**Target Platform**: Desktop (LWJGL3)
**Project Type**: Multi-project Gradle (core, lwjgl3, etc.)
**Performance Goals**: Language switch < 100ms, UI 60fps
**Constraints**: < 100MB extra memory for fonts, smooth parallax background
**Scale/Scope**: 2 languages, 1 main menu, 1 settings screen

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **模块化 (Modularization)**: 是。通过 `LocalizationService` 抽象翻译逻辑，避免硬编码。
- **端侧分离 (C/S Separation)**: 是。本地化仅涉及客户端表现层，不影响后端模拟逻辑。
- **命名规范 (Naming)**: 是。所有新类和方法将遵循命名规范并附带中文注释。
- **Mod 支持 (Extensibility)**: 是。语言包以外部资源形式加载，方便未来 Mod 扩展语言。
- **模拟驱动 (Simulation)**: 是。UI 动画（如背景视差）将关联游戏 Delta Time，而非固定帧率。

## Project Structure

### Documentation (this feature)

```text
specs/003-localization-ui-settings/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (UI Event Contracts)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
core/src/main/java/com/staraxis/game/
├── core/
│   ├── i18n/            # LocalizationService, Bundle management
│   └── ui/
│       ├── MainMenuScreen.java
│       ├── SettingsScreen.java
│       └── components/  # Animated buttons, Backgrounds
assets/
├── i18n/                # .properties files (messages.properties, messages_en.properties)
├── fonts/               # AlibabaPuHuiTi-3-65-Medium.ttf
└── textures/            # UI skins, background assets
```

**Structure Decision**: 采用标准的 LibGDX 多模块结构，核心逻辑位于 `core` 模块，资源位于 `assets`。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
