# Research: Main Menu UI (游戏主界面)

**Feature**: 002-main-menu-ui
**Date**: 2026-01-05

## UI Framework Choice: LibGDX Scene2D.ui

- **Decision**: 使用 LibGDX 自带的 `Scene2D.ui` 框架。
- **Reasoning**: 
    - `Scene2D.ui` 是 LibGDX 的标准 UI 解决方案，性能优秀且高度可定制。
    - 提供了完善的布局系统 (`Table`) 和常用组件 (`Button`, `Label`, `List`, `SelectBox`)。
    - 支持皮肤 (`Skin`) 系统，方便未来进行视觉风格的整体替换。

## Settings Storage (设置存储)

- **Decision**: 使用 `Gdx.app.getPreferences()` 进行轻量级存储。
- **Reasoning**: 
    - 跨平台兼容性好。
    - 接口简单，适合存储分辨率、帧率、全屏状态等基础键值对。
    - 文件通常存储在用户目录下的 `.prefs` 文件中。

## Screen Management (页面管理)

- **Decision**: 扩展 LibGDX 的 `Screen` 类。
- **Reasoning**: 
    - 符合 LibGDX 的典型游戏循环架构。
    - 方便在主菜单 (`MainMenuScreen`) 和设置页面 (`SettingsScreen`) 之间进行切换。

## Resolution Handling (分辨率处理)

- **Decision**: 使用 `Lwjgl3ApplicationConfiguration.getDisplayModes()` 获取当前显示器支持的所有分辨率。
- **Reasoning**: 
    - 确保获取的分辨率是真实可用的，避免黑屏。
    - 符合澄清后的需求。

## Feedback Mechanism (反馈机制)

- **Decision**: 实现一个通用的 `Toast` 类或利用 `Dialog` 显示占位提示。
- **Reasoning**: 
    - `Scene2D.ui` 的 `Dialog` 组件非常成熟，支持淡入淡出。
    - 符合澄清后的需求。
