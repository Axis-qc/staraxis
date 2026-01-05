# Data Model: Main Menu UI (游戏主界面)

**Feature**: 002-main-menu-ui
**Date**: 2026-01-05

## Entities

### SettingsData (设置数据)
存储游戏运行时的显示配置信息。

| Attribute | Type | Description |
|-----------|------|-------------|
| width | int | 屏幕宽度（像素） |
| height | int | 屏幕高度（像素） |
| fullscreen | boolean | 是否全屏模式 |
| vsync | boolean | 是否开启垂直同步 |
| targetFPS | int | 目标帧率限制 (30, 60, 0=无限制) |

## State Transitions (状态转换)

### Screen Transitions (页面跳转)
- **Startup** -> `MainMenuScreen`
- `MainMenuScreen` --(点击设置)--> `SettingsScreen`
- `SettingsScreen` --(点击返回)--> `MainMenuScreen`
- `MainMenuScreen` --(点击退出)--> **Exit Process**

## Validation Rules (校验规则)
1. **分辨率校验**: 必须是显示器支持的 `DisplayMode` 之一。
2. **帧率校验**: 仅限预设的合法值 (30, 60, 无限制)。
