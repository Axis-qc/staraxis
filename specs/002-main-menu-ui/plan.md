# Implementation Plan: Main Menu UI (游戏主界面)

**Branch**: `002-main-menu-ui` | **Date**: 2026-01-05 | **Spec**: [spec.md](./spec.md)

## Summary
实现基于 LibGDX Scene2D.ui 的游戏主入口界面。采用模块化 Screen 管理机制，核心功能包括动态分辨率获取、全屏切换、帧率限制以及基础的 UI 交互反馈。技术上将使用 `Gdx.app.getPreferences()` 处理配置持久化。

## Technical Context

**Language/Version**: Java 21 (OpenJDK)
**Primary Dependencies**: LibGDX 1.14.0.3, Scene2D.ui
**Storage**: LibGDX Preferences (本地 XML 文件)
**Testing**: JUnit 5 (逻辑测试), 手动冒烟测试 (UI 交互)
**Target Platform**: Desktop (Windows/macOS/Linux via LWJGL3)
**Project Type**: Desktop Application
**Performance Goals**: UI 响应时间 < 16ms, 启动至菜单 < 2s
**Constraints**: 必须符合 C/S 分离架构（UI 仅在客户端），遵循命名注释规范
**Scale/Scope**: 2 个主页面 (MainMenu, Settings), 1 个配置管理类

## Constitution Check

- **模块化 (Modularization)**: 使用独立的 Screen 类和 Manager 类，严禁在 Launcher 中直接写 UI。
- **端侧分离 (C/S Separation)**: UI 逻辑完全限制在 `lwjgl3` 模块，不影响 `core` 中的模拟逻辑。
- **命名规范 (Naming)**: 变量与方法包含注释，如 `updateResolution /* 更新分辨率 */`。
- **Mod 支持 (Extensibility)**: 使用 Skin 系统，未来可通过替换 JSON 资源实现主题更换。
- **模拟驱动 (Simulation)**: UI 交互不直接影响核心模拟，通过指令或配置同步。

## Project Structure

### Documentation (this feature)

```text
specs/002-main-menu-ui/
├── spec.md              # 需求规格
├── plan.md              # 本计划文件
├── research.md          # 技术调研与决策
├── data-model.md        # 数据模型与状态转换
└── quickstart.md        # 开发与测试指南
```

### Source Code (repository root)

```text
lwjgl3/src/main/java/com/staraxis/game/client/
├── ui/
│   ├── MainMenuScreen.java    # 主菜单页面
│   ├── SettingsScreen.java    # 设置页面
│   └── components/
│       └── Toast.java         # 占位提示组件
└── config/
    └── SettingsManager.java   # 配置管理器
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | | |
