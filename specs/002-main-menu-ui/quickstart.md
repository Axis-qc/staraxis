# Quickstart: Main Menu UI (游戏主界面)

**Feature**: 002-main-menu-ui
**Date**: 2026-01-05

## Development Setup

### 1. 资源准备
- 确保 `lwjgl3/src/main/resources/` 下存在必要的 UI 资源（如 `uiskin.json`, `uiskin.png`, `uiskin.atlas`）。
- 暂时可以使用 LibGDX 默认的皮肤资源进行开发。

### 2. 核心类说明
- `MainMenuScreen`: 主入口页面，包含新游戏、加载、多人、设置、退出按钮。
- `SettingsScreen`: 设置页面，包含分辨率下拉框、全屏开关、帧率限制、应用及返回按钮。
- `SettingsManager`: 负责设置数据的持久化（Preferences）与硬件状态同步。

## Running and Testing

### 运行主菜单
```bash
./gradlew :lwjgl3:run
```
程序启动后应直接进入 `MainMenuScreen`。

### 测试要点
1. **分辨率切换**: 在设置中修改并应用，确认窗口大小发生变化且无黑屏。
2. **全屏切换**: 确认能在窗口和全屏之间平滑切换。
3. **占位提示**: 点击“新游戏”等按钮，确认有“功能开发中”的弹窗提示。
4. **退出确认**: 点击“退出”，确认程序立即终止。
